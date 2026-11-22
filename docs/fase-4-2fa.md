# Fase 4 — 2FA con TOTP

Segundo factor con códigos TOTP (RFC 6238) y rediseño de la zona CHALLENGE. El login en CHALLENGE ya no revela si la contraseña es correcta y solo emite tokens tras un código TOTP válido.
Quedan fuera de esta fase los códigos de respaldo, desactivar o reconfigurar el 2FA, y el resto de vistas de la captura de seguridad (actividad, dispositivos).

## Configuración

| Variable / propiedad | Por defecto | Descripción |
|---|---|---|
| `TOTP_ENCRYPTION_KEY` | — (obligatoria) | Clave AES-256 del secreto TOTP: base64 de exactamente 32 bytes (`openssl rand -base64 32`). Si falta o es inválida, la app no arranca. Si cambia, los 2FA activos dejan de verificarse. |
| `app.two-factor.challenge-ttl` | `5m` | Vigencia del `challengeId`. |
| `app.two-factor.max-attempts` | `5` | Intentos de verificación por `challengeId`. |
| `app.two-factor.cleanup-interval` | `5m` | Cada cuánto se purgan los desafíos vencidos o cerrados. |

## Endpoints

| Método | Ruta | Auth | Respuesta |
|---|---|---|---|
| POST | `/api/auth/2fa/setup` | Bearer | `200 {secret, otpauthUri}`. Crea o reemplaza el secreto pendiente. `409` si el 2FA ya está activo. |
| POST | `/api/auth/2fa/enable` | Bearer | Cuerpo `{code}`. `204` activa el 2FA. `400 "Código inválido"`. `409` si ya estaba activo. |
| GET | `/api/auth/2fa/status` | Bearer | `200 {enabled, confirmedAt}`. Nunca devuelve el secreto. |
| POST | `/api/auth/2fa/verify` | pública | Cuerpo `{challengeId, code}`. `200` con tokens y cookie, como el login. Cualquier fallo: `401 {"error":"Credenciales inválidas"}`. |

En zona CHALLENGE, `POST /api/auth/login` responde siempre:

```json
403 {"error":"Verificación adicional requerida","code":"CHALLENGE_REQUIRED","challengeId":"<43 caracteres base64url>"}
```

## Flujo

```
Activación (con sesión)
  setup ──► secreto de 20 bytes, cifrado en user_totp (enabled=false) ──► QR / clave manual
  enable(code) ──► TOTP válido ──► last_used_step = paso, enabled=true

Login en zona CHALLENGE (exista o no el email, sea correcta o no la contraseña)
  Argon2 (hash ficticio si no existe) ──► contadores: CHALLENGED o FAILURE
  ──► fila en login_challenges {hash(id), user_id, password_ok, score, reglas, vence +5 min}
  ──► 403 CHALLENGE_REQUIRED + challengeId

verify(challengeId, code)  [fila bloqueada con SELECT … FOR UPDATE]
  no existe | vencido | cerrado | intentos agotados  ──► 401
  attempts++ (al 5.º fallo se cierra)
  password_ok ∧ 2FA activo ∧ TOTP válido ∧ paso > last_used_step ──► tokens, desafío cerrado, 200
  cualquier otro caso ──► 401
  siempre: fila en login_attempts con stage=TOTP
```

Frontend:

- **Login:** ante `CHALLENGE_REQUIRED`, el `challengeId` se guarda en un módulo en memoria (nunca en `localStorage`, `sessionStorage`, cookies ni en la URL) y se navega a `/login/verify`. Si se recarga la página, se pierde y hay que volver a iniciar sesión.
- **Verificación:** muestra una cuenta regresiva de 5 minutos. Tras 5 fallos o al vencer, ofrece volver al login.
- **Activación:** está en `/account/security` (enlace "Seguridad"). Muestra el QR, la clave agrupada de 4 en 4 con botón de copiar y el URI `otpauth`, y pide un primer código.

## Decisiones

- **TOTP propio** (`domain/totp/Totp`):
  - HMAC-SHA1 de la JDK, 6 dígitos, paso de 30 s y ±1 paso de tolerancia;
  - comparación en tiempo constante sobre los 3 pasos;
  - Base32 propio. No se agregó ninguna dependencia en el backend;
  - pruebas con los vectores del RFC 6238 (Apéndice B, 8 dígitos y sus últimos 6) y del RFC 4648.
- **Anti-reutilización:** `UPDATE … SET last_used_step = :paso WHERE last_used_step IS NULL OR last_used_step < :paso`, una sentencia atómica. Si no actualiza ninguna fila, el código se rechaza. El código usado en la activación tampoco se acepta después en un login.
- **Cifrado del secreto:**
  - AES-256-GCM con nonce aleatorio de 12 bytes y tag de 128 bits. Se guarda `base64(nonce ‖ cifrado ‖ tag)`;
  - el `user_id` va como AAD: un secreto copiado a la fila de otro usuario no descifra;
  - la clave no está en la BD y solo llega por variable de entorno;
  - el secreto en claro solo sale en la respuesta de `setup`, y el frontend lo descarta al activar.
- **CHALLENGE sin oráculo:** en zona CHALLENGE las dos ramas hacen el mismo trabajo: Argon2, registro en contadores, una fila en `login_attempts` y otra en `login_challenges`. La respuesta tiene el mismo estado, los mismos campos y un `challengeId` del mismo largo. Internamente, Redis sigue contando la contraseña incorrecta como fallo.
- **Desafíos en PostgreSQL y no en Redis:** el fail-safe de Redis lleva a CHALLENGE. Si los desafíos vivieran en Redis, con Redis caído nadie podría completar el 2FA. Así funciona aunque Redis falle (lo cubre `RedisDownIntegrationTest`).
- **challengeId:**
  - son 32 bytes de `SecureRandom` en base64url, el mismo generador que el refresh token;
  - en la BD solo queda su SHA-256;
  - es de un solo uso: se cierra al tener éxito o al quinto fallo, y vence a los 5 minutos;
  - `password_ok` nunca sale del servidor.
- **Fail-safe:**
  - si no se puede guardar el desafío, el login responde igual con un id que no existe en BD, así que ese verify siempre da 401;
  - si falla la BD o el descifrado durante el verify, la transacción se revierte y no se emiten tokens;
  - el verify no depende de Redis.
- **Usuarios sin 2FA en zona CHALLENGE:** reciben el mismo 403 y no pueden completar el desafío. Deben reintentar cuando su IP salga de la zona (la ventana más larga es de 15 minutos). La pantalla de verificación lo explica sin revelar nada. Es deliberado: avisarles de otra forma ("no tienes 2FA") revelaría que la contraseña era correcta.
- **Purga:** `LoginChallengeCleanup` (`@Scheduled`, cada 5 minutos) borra los desafíos vencidos o cerrados. Así un flujo sostenido de logins en CHALLENGE, como un ataque, no llena la tabla. Se puede desactivar con `app.scheduling.enabled=false`.
- **Registro (V4):** `login_attempts.stage` vale `PASSWORD` o `TOTP`. Un verify guarda el email, el score y las reglas del login que originó el desafío, y la IP del verify.
  - Si el desafío ya no estaba abierto, se agrega la marca `CHALLENGE_INVALID` a las reglas.
  - Si el `challengeId` no existe, se guarda con email vacío, score `NULL` y esa misma marca.
- **QR en el frontend:** `qrcode-generator` 2.0.4.
  - Es MIT, sin dependencias y ocupa unos 50 KB. `npm audit` no encontró vulnerabilidades.
  - Solo se usa su matriz de módulos. El SVG se dibuja en React, sin `dangerouslySetInnerHTML`.
- **De las capturas de Stitch se omitió:**
  - el score y la telemetría, la IP y la ciudad;
  - "ISO 27001", "E2E", "Zero Trust";
  - el nivel de fortaleza en porcentaje;
  - los códigos de respaldo y "Solicitar ayuda" (fuera de alcance);
  - dispositivos, actividad, notificaciones y WebAuthn.

## Compromiso de diseño: 2FA adaptativo (limitación conocida)

El 2FA **solo se exige en zona CHALLENGE**. En ALLOW se emiten tokens con la contraseña, aunque el usuario tenga el 2FA activo.

**Justificación:** si el 2FA se exigiera en ALLOW solo a quien lo activó, la respuesta del login revelaría información. Un 403 con desafío diría "la contraseña es correcta y esta cuenta tiene 2FA". Un 401 diría "la contraseña es incorrecta o la cuenta no tiene 2FA". Eso reabriría el oráculo que esta fase cierra.

**Consecuencia:** un atacante con la contraseña correcta que se mantenga en ALLOW entra sin el segundo factor, aunque la víctima lo haya activado. Esto incluye el ataque distribuido o *low and slow* (ver Fase 7). El 2FA protege los intentos sospechosos, no todos.

**Alternativas fuera de alcance:**
- exigir 2FA en todo login, responder siempre con desafío y crear uno ficticio para cuentas sin 2FA, con el costo de UX que eso implica;
- hacer el 2FA obligatorio para todas las cuentas.

## Otras limitaciones

- **Fuerza bruta del código:** son 5 intentos por desafío y 3 códigos válidos por ventana, una probabilidad de unos 3·10⁻⁶ por intento. Cada desafío nuevo exige un login, que pasa por el motor de riesgo, pero `enable` (con sesión) no tiene límite propio de intentos.
- **Sin códigos de respaldo:** quien pierda el teléfono no puede completar un CHALLENGE. Desactivar o reconfigurar el 2FA tampoco está implementado.
- **Rotar la clave:** cambiar `TOTP_ENCRYPTION_KEY` invalida todos los secretos existentes. No hay re-cifrado.
- **Tiempo de respuesta:** se iguala el trabajo (Argon2 y escrituras), pero no se mide ni se garantiza que los tiempos sean idénticos.

## Pruebas

```
cd backend && ./gradlew test     # Testcontainers: PostgreSQL y Redis
cd frontend && npm test && npm run lint && npm run build
cd simulator && npm test
```

- **Unitarias (backend):**
  - `TotpTest`: vectores del RFC 6238 y RFC 4648, ventana ±1, formatos inválidos.
  - `AesGcmTotpSecretCipherTest`: ida y vuelta, AAD de otro usuario, clave distinta, dato alterado, claves inválidas.
  - `TotpManagementUseCasesTest`.
  - `VerifyLoginChallengeUseCaseTest`: éxito; código incorrecto; reutilización; contraseña incorrecta; usuario sin 2FA; desafío vencido, agotado o desconocido; desafío no guardado; purga.
  - `LoginRiskTest`: respuesta idéntica en CHALLENGE.
- **Integración:**
  - `TotpSetupIntegrationTest`.
  - `TwoFactorChallengeIntegrationTest`: flujo completo; respuesta idéntica con contraseña correcta, incorrecta o email inexistente; código incorrecto o reutilizado; challengeId reutilizado o vencido; 5 intentos; usuario sin 2FA; purga.
  - `RedisDownIntegrationTest`: con Redis caído, el desafío se completa.
- **Frontend (Vitest):** mapeo de los endpoints 2FA y del 403 con `challengeId`, el almacén del desafío (en memoria, con vencimiento, sin tocar el almacenamiento del navegador), la normalización del código y la matriz del QR.
- **Simulador:** el efecto en las corridas está en [`fase-7-simulador.md`](fase-7-simulador.md).
