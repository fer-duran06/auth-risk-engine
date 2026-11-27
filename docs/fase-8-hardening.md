# Fase 8 — Hardening post-SAST

Motivada por una revisión estática (SAST) externa sobre el repo, que marcó 3/5 en "Controles" por dos huecos puntuales, y por un conjunto de instrucciones adicionales del profesor en la misma sesión. Siete puntos en total, todos resueltos en esta fase:

1. Rate limiting (protección contra fuerza bruta / DoS).
2. Cabeceras HTTP de seguridad (CSP, HSTS, X-Content-Type-Options, etc.).
3. Detección real de IP del cliente (antes confiaba en `X-Forwarded-For` sin validar el origen).
4. Validación de email/contraseña filtrados contra una API de terceros (HIBP).
5. Completar la detección de ataques al login (el stuffing distribuido seguía sin detectarse, ver [`fase-7-simulador.md`](fase-7-simulador.md)).
6. Notificación al usuario de actividad sospechosa.
7. Re-ejecución del SAST para confirmar la mejora (pendiente: la hace el propio autor con la herramienta del profesor, fuera de este repo).

## 1–2. Rate limiting y cabeceras de seguridad (los dos puntos marcados por el SAST)

- **Cabeceras:** `SecurityConfig.kt` añade un bloque `.headers {}` explícito — CSP `default-src 'none'; frame-ancestors 'none'` (API pura, sin HTML propio que renderizar), HSTS a 1 año con subdominios, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff` y `Referrer-Policy: no-referrer`. Antes, Spring Security traía algunas por defecto pero sin configuración explícita ni HSTS; el scanner las marcaba como "no detectado" porque busca la configuración declarada, no el comportamiento heredado.
- **Rate limiting:** `RedisRateLimiter` (ventana fija de 1 minuto, 60 peticiones por clave, `INCR`+`EXPIRE` en Lua) detrás de `RateLimitFilter`, un `OncePerRequestFilter` que solo actúa sobre las rutas sensibles (`/api/auth/register`, `/login`, `/refresh`, `/2fa/*`). Clave = IP resuelta (ver punto 3). Al exceder el límite responde `429` con `Retry-After` y no toca Redis para rutas no listadas. Si Redis falla, el filtro deja pasar la petición (*fail-open*): es una capa de disponibilidad, no la última línea de defensa — esa es el motor de riesgo (fases 2 y 5 de este documento), que si falla hace lo contrario (*fail-closed*, `CHALLENGE`). Es una decisión consciente documentada en el propio código.

## 3. IP real del cliente

`server.forward-headers-strategy: native` en `application.yml` activa el `RemoteIpValve` de Tomcat, que reescribe `request.remoteAddr` a partir de `X-Forwarded-For` **solo** cuando el peer TCP inmediato cae en los rangos privados/internos por defecto de Tomcat (RFC 1918 + loopback). En Railway el backend no tiene dominio público: la única conexión que le llega es la del contenedor del frontend por la red privada del proyecto, que cae en esos rangos. Es el patrón estándar de Spring Boot para "corriendo detrás de un proxy de confianza" — sin esto, cualquiera podía enviar su propio `X-Forwarded-For` y falsear la IP que usan el motor de riesgo y, ahora, el rate limiter. `ClientIpResolver` no cambió su lógica, solo el comentario que explica el nuevo modelo de confianza. El override `X-Simulated-IP` del simulador (fase 7) sigue funcionando igual y tiene prioridad, solo en `app.demo-mode=true`.

*Nota:* un `docker run -p` local también cae en esos rangos y Tomcat lo trataría igual de "de confianza". Para desarrollo académico no es un problema; en Railway no aplica porque no hay forma de conectar directo sin pasar por la red privada.

## 4. Email/contraseña filtrados (HIBP)

La instrucción decía "el correo", pero el diseño original del proyecto (fases previas) solo contemplaba contraseñas filtradas. Como la API real de "email filtrado" de HIBP requiere clave de pago y no hay presupuesto para eso en un proyecto académico, se implementaron las dos lecturas posibles, ambas gratuitas y sin clave:

- **Pwned Passwords (k-anonimato):** en el registro, la contraseña nunca sale del servidor completa — se calcula su hash SHA-1 y solo se envían los primeros 5 caracteres a `api.pwnedpasswords.com`; la comparación del resto se hace en memoria con la respuesta. Si aparece en la base de contraseñas filtradas, el registro se rechaza (`PwnedPasswordException`).
- **XposedOrNot (email):** consulta informativa, nunca bloquea nada. Se expone en `GET /api/auth/breach-status` y se muestra en la página de Seguridad del frontend si el correo del usuario aparece en alguna brecha conocida.

Ambas llamadas usan `java.net.http.HttpClient` con timeout corto y **fail-open**: cualquier error de red, timeout o caída del tercero se traduce en `null` ("no se pudo determinar"), que nunca bloquea registro ni login — un servicio externo caído no debe tumbar la aplicación propia.

## 5. Detección de ataques al login: el hueco del stuffing distribuido

La Fase 7 documentaba que el escenario "stuffing distribuido" (muchas IPs, un intento por cuenta cada una) no disparaba ninguna regla, porque las 4 reglas existentes dependen de la IP o de la cuenta individual. Se añadió una 5.ª regla, `GLOBAL_DISTINCT_ACCOUNT_VELOCITY` (peso 20, umbral 20 cuentas distintas con fallo en una ventana de 2 minutos, agregado en Redis sin importar la IP), que detecta el patrón aunque cada IP solo aparezca una vez. Con el umbral en 20 (hay 30 víctimas del stuffing en el simulador), el score sube a 20 en el propio ataque, que sumado a la tasa global de fallos (+15) ya empuja a `CHALLENGE` antes de los 30 intentos — cerrando el hueco señalado como limitación #1 en `fase-7-simulador.md`. **Pendiente:** volver a correr el simulador para confirmar el nuevo resultado del escenario c) y actualizar esa tabla.

## 6. Notificación de actividad sospechosa

No hay infraestructura de envío de correo (ni presupuesto para un proveedor), así que se implementó como notificaciones dentro de la app: tabla `notifications` (migración `V5__notifications.sql`), endpoints `GET /api/notifications` y `POST /api/notifications/{id}/read`, y una tarjeta de avisos en la página de Seguridad del frontend. Se genera una notificación cada vez que un login conocido (usuario existente) cae en zona `CHALLENGE`. Las de zona `BLOCK` no generan notificación a propósito: ese flujo nunca busca al usuario en la base de datos (para no pagar ese costo ni filtrar si el email existe bajo ataque), así que no hay a quién notificar sin romper esa garantía.

**Trade-off aceptado:** el login está diseñado para que el tiempo de respuesta no revele si el email existe (ver `VerifyConstantPathTest`, fase 1). Escribir la notificación solo cuando el usuario existe reintroduce una asimetría de tiempo mínima (un INSERT de una fila), muy por debajo del ruido normal de red, a cambio de un aviso real al dueño de la cuenta.

## 7. Re-ejecución del SAST

Queda fuera de este repo: la hace el autor con la herramienta del profesor una vez desplegados estos cambios.

## Resumen de cambios de configuración

Ninguna variable de entorno nueva es obligatoria; todas tienen default seguro:

| Variable | Default | Efecto |
|---|---|---|
| `FORWARD_HEADERS_STRATEGY` | `native` | Habilita la reescritura de IP real vía Tomcat `RemoteIpValve`. |
| `app.rate-limit.enabled` | `true` | Apaga el rate limiter completo si se necesita para pruebas. |
| `app.rate-limit.capacity` / `.window` | `60` / `1m` | Umbral del rate limiter. |
| `app.risk.rules.global-distinct-accounts.*` | peso 20, umbral 20, ventana 2m | Nueva regla de stuffing distribuido. |

La migración `V5__notifications.sql` se aplica sola al desplegar (Flyway ya está activo en `application.yml`).
