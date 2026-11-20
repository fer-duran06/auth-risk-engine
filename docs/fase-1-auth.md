# Fase 1 — Autenticación básica

Registro, login con JWT de acceso y refresh token rotativo con detección de reutilización.
Todavía sin motor de riesgo, 2FA ni HIBP (fases posteriores).

## Configuración

| Variable | Obligatoria | Descripción |
|---|---|---|
| `JWT_SECRET` | sí | Clave HS256, mínimo 32 bytes (`openssl rand -base64 48`). Sin ella la app no arranca. |
| `AUTH_COOKIE_SECURE` | no | Atributo `Secure` de la cookie. Por defecto `true`; el perfil `dev` usa `false` (http://localhost). |

## Endpoints

| Método | Ruta | Auth | Respuesta |
|---|---|---|---|
| POST | `/api/auth/register` | pública | `202` con mensaje genérico, exista o no el email. `400` si el email o la contraseña (12–128) no son válidos. |
| POST | `/api/auth/login` | pública | `200 {accessToken, tokenType, expiresIn}` + cookie `refresh_token`. `401 {"error":"Credenciales inválidas"}`. |
| POST | `/api/auth/refresh` | cookie | `200` con access token nuevo y cookie rotada. `401` si es inválido, expiró o se reutilizó (borra la cookie). |
| POST | `/api/auth/logout` | cookie | `204`, revoca la familia y borra la cookie. Idempotente. |
| GET | `/api/auth/me` | Bearer | `200 {id, email, role, createdAt}`. `401` sin token válido. |

Swagger (solo perfil `dev`): `/swagger-ui.html`, con esquema `bearerAuth` para probar `/me`.

## Flujo de tokens

```
login ──► access JWT (15 min, en memoria del cliente) + refresh R1 (cookie, familia F)
refresh(R1) ──► R1 revocado, replaced_by=R2 ──► access nuevo + R2 (familia F)
refresh(R1) otra vez ──► reutilización: se revocan todos los tokens de F ──► 401
logout(Rn) ──► se revoca la familia de Rn ──► 204
```

- **Access token:** JWT HS256 firmado con `JWT_SECRET`. Lleva los claims `iss=authshield`, `sub=<id usuario>`, `role`, `jti`, `iat` y `exp`. No incluye email ni ningún otro dato sensible.
- **Refresh token:** 32 bytes aleatorios (`SecureRandom`) codificados en base64url. En BD solo se guarda su SHA-256. Con esa entropía no hace falta un hash lento, y el hash determinista permite buscar por índice. Vence a los 7 días.
- **Cookie:** `refresh_token` con `HttpOnly`, `SameSite=Strict` y `Path=/api/auth`; `Secure` según el perfil. Ningún token se guarda en `localStorage`.
- **Concurrencia:** el token se lee con `SELECT … FOR UPDATE`, así dos refresh simultáneos con el mismo token no pueden rotarlo dos veces.
- **Revocación persistente:** el resultado de la detección de reutilización se decide dentro de la transacción y la excepción se lanza después. Así la revocación de la familia se confirma y no se revierte.

## Decisiones de seguridad

- **Contraseñas:** Argon2id (16 MiB, 2 iteraciones, sal aleatoria de 16 bytes). El máximo de 128 caracteres acota el costo del hash.
- **Login sin enumeración:** si el usuario no existe, igual se verifica la contraseña contra un hash ficticio. El tiempo es similar y la respuesta es siempre `401` con el mismo mensaje.
- **Registro sin enumeración:** siempre `202` con el mismo mensaje. La contraseña se hashea aunque el email exista, y la inserción usa `ON CONFLICT DO NOTHING`, así que nunca sobrescribe una cuenta.
- **Email:** se normaliza a minúsculas en el dominio y la BD lo garantiza con un `CHECK`.
- **Logs:** no se registran cuerpos, contraseñas ni tokens. `toString()` de DTOs, entidades y resultados oculta los secretos. Ante una reutilización se registra un `WARN` sin el token.
- **Errores:** JSON genérico, sin trazas ni valores enviados por el cliente.
- **CSRF:** deshabilitado de forma deliberada. La API es stateless con Bearer y la única cookie es `SameSite=Strict` con path restringido. CORS sigue limitado a `CORS_ALLOWED_ORIGINS`.
- **Rutas:** todo lo que no está declarado se deniega (`denyAll`).

## Pruebas

```
docker compose up -d   # no es necesario para las pruebas: usan Testcontainers
cd backend && ./gradlew test
```

- **Unitarias:** casos de uso con fakes en memoria y reloj controlable, más los adaptadores Argon2, JWT y el generador de refresh.
- **Integración:** `AuthFlowIntegrationTest`, con PostgreSQL y Redis reales. Cubre register, login, me, rotación, reutilización (verifica en BD que toda la familia quedó revocada) y logout.
