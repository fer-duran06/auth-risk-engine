# Fase 2 — Motor de riesgo

Cada intento de login recibe un score de riesgo antes de verificar la contraseña, calculado con reglas ponderadas sobre contadores en Redis. El objetivo es frenar el credential stuffing.
Quedan fuera de esta fase: país/dispositivo nuevo, HIBP y 2FA.

## Reglas

Una regla suma su peso si su contador llega al umbral dentro de la ventana. Los contadores cuentan los **intentos previos**, no el que se está evaluando. Todo es configurable en `app.risk` (`application.yml`).

| Regla (id) | Peso | Se dispara con | Ventana |
|---|---|---|---|
| Velocidad por IP (`IP_VELOCITY`) | 30 | ≥ 10 intentos desde la IP | 1 min |
| Usuarios distintos por IP (`DISTINCT_USERS_PER_IP`) | 25 | ≥ 5 emails distintos desde la IP | 10 min |
| Fallos por cuenta (`ACCOUNT_FAILURES`) | 20 | ≥ 5 fallos contra el email | 15 min |
| Tasa global de fallos (`GLOBAL_FAILURE_RATE`) | 15 | ≥ 50 % de fallos, con al menos 20 intentos | 5 min (buckets de 1 min) |

## Zonas

El score es la suma de los pesos, acotada a 100. Los umbrales se configuran en `app.risk.thresholds`.

| Zona | Score | Respuesta |
|---|---|---|
| ALLOW | < 30 | Flujo normal: `200` con tokens (o `401` si la contraseña es incorrecta). |
| CHALLENGE | 30–69 | Se verifica la contraseña. Si es correcta: `403 {"error":"Verificación adicional requerida","code":"CHALLENGE_REQUIRED"}`, sin tokens. Si es incorrecta: el `401` de siempre. |
| BLOCK | ≥ 70 | `429 {"error":"Demasiados intentos. Intenta más tarde."}`. No se busca al usuario ni se ejecuta Argon2. |

Ejemplo con los valores por defecto: una IP que prueba emails distintos queda en ALLOW (25) desde el 6.º intento, en CHALLENGE (55) desde el 11.º y en BLOCK (70) cuando la tasa global supera el 50 % con 20 intentos o más.

## Flujo del login

```
IP (ClientIpResolver) + email normalizado
  └─► RiskCounters.read ──► RiskEngine.assess ──► zona
        (si Redis falla: fail-safe → CHALLENGE, score NULL, regla COUNTERS_UNAVAILABLE)
  BLOCK     ──► registrar(BLOCKED) ──► 429
  verificar contraseña (hash ficticio si el usuario no existe)
  incorrecta ──► registrar(FAILURE) ──► 401
  CHALLENGE ──► registrar(CHALLENGED) ──► 403 CHALLENGE_REQUIRED
  ALLOW     ──► emitir tokens ──► registrar(SUCCESS) ──► 200
registrar = RiskCounters.record (si falla, se ignora) + fila en login_attempts
```

Qué cuenta cada resultado:

| Resultado | IP y emails distintos | Fallos de la cuenta | Intentos globales | Fallos globales |
|---|---|---|---|---|
| SUCCESS | sí | — | sí | — |
| FAILURE | sí | sí | sí | sí |
| CHALLENGED | sí | — | sí | — |
| BLOCKED | sí | — | — | — |

- Los bloqueados sí cuentan para la IP: un atacante que sigue intentando no se "enfría".
- Los bloqueados no cuentan contra la cuenta: así un atacante no puede castigar cuentas ajenas.
- Los bloqueados no cuentan en la tasa global: así no se forma un bucle que se alimenta solo.

## Persistencia

- **Redis:** los contadores por IP y por cuenta son sorted sets con el instante en ms como score, es decir, ventanas deslizantes. Los globales son contadores por minuto (`INCR` + `EXPIRE`). Cada lectura y cada registro es un único script Lua: atómico y en un solo viaje a Redis.
- **PostgreSQL (V3):** la tabla `login_attempts` guarda `id`, `email`, `ip`, `score`, `zone`, `triggered_rules text[]`, `success` y `created_at`, con índices por `created_at` e `ip`.
  - `success` es true solo si se emitieron tokens.
  - `score` es `NULL` solo en el fail-safe: sin contadores no hay un número real que guardar.

## Decisiones de seguridad

- **Riesgo antes que contraseña:** en BLOCK no se consulta la BD ni se ejecuta Argon2. La respuesta es idéntica exista o no el email, porque la decisión se toma antes de buscarlo.
- **Score oculto:** el score y las reglas disparadas nunca se devuelven al cliente ni se registran en logs. Solo quedan en `login_attempts`, para el administrador. El `429` no incluye `Retry-After` para no revelar la ventana.
- **Fail-safe:** si Redis no responde, el intento se trata como CHALLENGE, nunca como ALLOW. Se registra un `WARN` que solo dice el tipo de excepción, sin IP, email ni mensaje. Los timeouts de Redis bajaron a 500 ms para caer rápido al fail-safe.
- **Privacidad en Redis:** los emails se guardan como SHA-256, tanto en las claves como en los miembros de los sets. Es un hash sin sal y los datos son efímeros (expiran con la ventana). Sirve para no dejar emails en claro, no protege contra un ataque de diccionario a Redis.
- **IP del cliente:** se usa la dirección de la conexión (`remoteAddr`). `X-Forwarded-For` se ignora siempre. `X-Simulated-IP` solo se acepta con `app.demo-mode=true`, y solo si es un literal IPv4/IPv6: se valida por formato antes de parsear, así nunca hay una consulta DNS. Si no es válido, se usa la IP de la conexión.
- **Configuración validada:** pesos fuera de 0–100, umbrales incoherentes (`0 < challenge < block <= 100`) o ventanas no positivas impiden que la app arranque.

> ⚠️ **Modo demo activo por defecto en dev.** `app.demo-mode` vale `false` en `application.yml` y `true` en `application-dev.yml`. Como `SPRING_PROFILES_ACTIVE` usa `dev` por defecto (decisión de la Fase 1), **si esa variable no se define, el modo demo queda activo** y cualquier cliente puede elegir su IP con `X-Simulated-IP`, lo que permite evadir las reglas por IP. Fuera del desarrollo local hay que definir `SPRING_PROFILES_ACTIVE` con un perfil distinto de `dev`.

## Pruebas

```
cd backend && ./gradlew test   # Testcontainers levanta PostgreSQL y Redis
```

- **Unitarias:**
  - `RiskRulesTest`: umbral exacto de cada regla.
  - `RiskEngineTest`: bordes 29/30 y 69/70, tope de 100, configuración inválida.
  - `LoginRiskTest`: cada zona y el fail-safe, con contadores falsos.
  - `ClientIpResolverTest`.
- **Integración:**
  - `RiskFlowIntegrationTest`: un usuario normal recibe ALLOW. Una ráfaga desde una IP da 401 → 403 `CHALLENGE_REQUIRED` → 429, mientras otra IP sigue en ALLOW. También verifica las filas de `login_attempts`.
  - `RedisRiskCountersIntegrationTest`: qué cuenta cada resultado y que no queden emails en claro.
  - `RedisDownIntegrationTest`: Redis apuntando a un puerto cerrado → 403 CHALLENGE con `score NULL`.
  - Antes de cada prueba de integración se vacía Redis (`FLUSHALL`), para que los contadores no se arrastren entre pruebas.
