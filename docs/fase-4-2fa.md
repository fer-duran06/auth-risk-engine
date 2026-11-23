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
  - si falla la BD durante el verify, la transacción se revierte y no se emiten tokens;
  - si falla el descifrado (clave cambiada o dato corrupto), el código no se acepta: el mismo `401`, nunca un `500`, y solo se registra el tipo de excepción;
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
- **Tiempo de respuesta:** se iguala el trabajo (Argon2 y escrituras en el login; camino constante en el verify), pero no se mide ni se garantiza que los tiempos sean idénticos.

## Revisión de seguridad

Revisión del código de esta fase tras cerrarla. Se buscaron diferencias observables entre los motivos de fallo, fugas del secreto o del código, y estado que sobreviviera entre usuarios en el frontend.

### Corregido

- **Canal de tiempo en `/2fa/verify` (severidad alta).** El verify cortaba el trabajo según el motivo del fallo.
  - Con contraseña incorrecta no tocaba la BD.
  - Con 2FA activo leía `user_totp`, descifraba, calculaba 3 HMAC y hacía un `UPDATE`.

  Midiendo el tiempo, un atacante en zona CHALLENGE podía distinguir la contraseña correcta, y saber si la cuenta tenía 2FA. Es justo lo que esta fase debía ocultar. Ahora todo verify sigue un **camino constante**:
  - leer el desafío con bloqueo;
  - leer la configuración TOTP;
  - descifrar con AES-GCM;
  - verificar (3 HMAC);
  - un `UPDATE` de `last_used_step`;
  - un `UPDATE` de `login_challenges`, por `id_hash`, también si el id no existe.

  Cuando no aplica el dato real se usan un usuario ficticio (el UUID nulo) y un secreto ficticio cifrado con la misma clave. Solo el éxito hace trabajo extra (emitir tokens), y el éxito ya es visible en la respuesta. Lo cubren `VerifyConstantPathTest` (misma secuencia para 7 motivos de fallo y uso del camino ficticio) y `TwoFactorChallengeIntegrationTest` (respuesta HTTP idéntica).
- **`500` si el descifrado fallaba (baja).** Solo era posible con contraseña correcta y 2FA activo, así que también lo distinguía. Ahora da el mismo `401`.
- **El login conservaba email y contraseña (alta, frontend).** Con `cacheComponents`, Next conserva `/login` oculta con `<Activity>`. Tras un login correcto, el formulario guardaba ambos campos, y al cerrar sesión el siguiente usuario los encontraba cargados. Ahora se limpian al iniciar sesión y al ocultarse la página (`LoginForm.test.tsx`).
- **Estado entre sesiones (baja, frontend):**
  - `session.start` y `session.logout` vacían el desafío 2FA pendiente;
  - `/account/security` descarta el secreto TOTP pendiente al ocultarse;
  - el registro vuelve al formulario vacío al ocultarse.

  Cada uno tiene su prueba con `<Activity>`.
- **Navegación de más tras el 2FA (no es de seguridad):** después de un verify exitoso la vista pasaba por `/login` antes de llegar a `/me`. Ahora hay una sola navegación.

Comprobado sin cambios:
- el login responde igual con contraseña incorrecta, email inexistente o 2FA inactivo, y siempre ejecuta Argon2 (salvo en BLOCK, para todos por igual);
- los códigos y el secreto no aparecen en logs ni en errores;
- el anti-replay es un `UPDATE` condicional atómico;
- el límite de 5 intentos y la expiración se aplican con la fila bloqueada;
- del `challengeId` solo se guarda el hash;
- `/me`, la seguridad de la cuenta y el panel de administración se desmontan al cerrar sesión (`RequireSession`).

### Sin corregir (limitaciones conocidas y trabajo futuro)

- **Fallos de TOTP sin contador por cuenta.** El límite de 5 es por desafío. Los fallos de verify no alimentan los contadores de riesgo (fallos por cuenta, velocidad por IP). Un atacante que ya tenga la contraseña y rote IPs puede pedir desafíos nuevos sin límite y probar 5 códigos en cada uno. La probabilidad por intento es baja (unas 3·10⁻⁶), pero no hay techo: con suficientes intentos el ataque funciona. *Trabajo futuro:* contar los fallos de verify como fallos de la cuenta y de la IP, y bloquear el 2FA de la cuenta tras N fallos en una ventana.
- **`/2fa/verify` sin límite de frecuencia.** El endpoint no pasa por el motor de riesgo ni por ningún límite por IP. Se puede llamar sin restricción.
- **Una fila por `challengeId` desconocido.** Cada verify con un id inventado escribe una fila en `login_attempts`, una tabla que nunca se purga. Es una vía barata para inflar la BD, agravada por la falta de límite de frecuencia. *Trabajo futuro:* limitar por IP en Redis, agregar los ids desconocidos en un contador en vez de guardar una fila cada uno, y definir una retención para `login_attempts`.
- **Sin prueba de concurrencia.** El anti-replay (`UPDATE … WHERE last_used_step < :paso`) y el bloqueo del desafío (`SELECT … FOR UPDATE`) son correctos por diseño en PostgreSQL con READ COMMITTED. Pero ninguna prueba lanza verifies simultáneos: esa garantía solo está razonada, no verificada.
- **Diferencia de microsegundos en Redis.** En zona CHALLENGE, la contraseña incorrecta se registra como `FAILURE`, que actualiza dos contadores más que `CHALLENGED` dentro del mismo script Lua. Es una diferencia de tiempo pequeña pero real entre contraseña correcta e incorrecta. *Trabajo futuro:* escribir siempre las mismas claves, con incremento cero cuando no aplica.
- **El camino constante iguala el trabajo, no el tiempo.** Leer por clave primaria una fila existente o una inexistente, y un `UPDATE` que afecta 1 fila o 0, pueden costar algo distinto en PostgreSQL. La diferencia es mucho menor que la que había, pero no se midió y no se garantiza que sea cero.

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
- **Regresión `VerifyForm.test.tsx`** (happy-dom, devDependency solo de pruebas): con `cacheComponents`, Next conserva la página oculta con `<Activity>`. La prueba comprueba que un desafío nuevo muestra el formulario aunque el anterior haya terminado. Por eso la vista lee el desafío del store en cada render y monta el formulario con `key={challengeId}`.
- **Simulador:** el efecto en las corridas está en [`fase-7-simulador.md`](fase-7-simulador.md).
