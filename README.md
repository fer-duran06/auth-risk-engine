# AuthShield — Motor de autenticación adaptativa

API que evalúa el riesgo de cada intento de login y decide **ALLOW**, **CHALLENGE** (segundo factor TOTP) o **BLOCK**. Incluye un frontend web, un panel de administración y un simulador de ataque. Proyecto académico de Seguridad Informática, de uso exclusivamente defensivo.

**Problema:** en el *credential stuffing* el atacante prueba en masa pares email/contraseña filtrados de otros servicios. Como muchas personas reutilizan contraseñas, una parte de esas credenciales funciona. AuthShield detecta los patrones de ese ataque (velocidad por IP, muchos usuarios desde una IP, fallos por cuenta, tasa global de fallos) y endurece la respuesta sin castigar al tráfico legítimo.

## Arquitectura

```
 Navegador ──► frontend/  Next.js 16 (App Router) · TypeScript · Tailwind 4      :3000
                  │  fetch con credentials (access token en memoria, refresh en cookie HttpOnly)
                  ▼
              backend/   Kotlin · Spring Boot 3.5 · JDK 21                        :8080
              ┌──────────────────────────────────────────────────────────────┐
              │ infrastructure  web (REST) · persistence (JPA/JDBC + Flyway) │
              │                 redis (contadores) · security (Argon2, JWT)  │
              │        │ implementa puertos                                  │
              │ application     casos de uso (login, 2FA, admin)             │
              │        │ usa                                                 │
              │ domain          reglas de riesgo, RiskEngine, TOTP           │
              │                 (sin dependencias de Spring)                 │
              └──────────────────────────────────────────────────────────────┘
                  │                                 │
                  ▼                                 ▼
           PostgreSQL 16                       Redis 7
           usuarios, refresh tokens,           contadores por ventana deslizante
           2FA, desafíos, login_attempts       (IP, cuenta, tasa global)

 simulator/  Node 22: credential stuffing sintético, solo contra localhost
```

Zonas: **ALLOW** con score < 30, **CHALLENGE** de 30 a 69, **BLOCK** desde 70 (máximo 100). Si Redis falla, el login se trata como CHALLENGE (fail-safe).

## Requisitos

| Herramienta | Versión |
|---|---|
| JDK | 21 recomendado. Para ejecutar Gradle 8.14 basta JDK 17 o superior (lo exige el plugin de Spring Boot 3.5); la toolchain 21 se descarga sola si falta. |
| Node.js | 22.12 o superior (lo exige Vitest 5), con npm |
| Docker Desktop | con Docker Compose v2 (PostgreSQL 16 y Redis 7 en contenedores; también lo usan las pruebas con Testcontainers) |

## Puesta en marcha (PowerShell)

Todos los comandos se ejecutan desde la raíz del repositorio, salvo cuando se indica `cd`.

### 1. Variables de entorno

```powershell
Copy-Item .env.example .env
```

Genera los secretos y pega cada valor en `.env`:

```powershell
function New-Secret([int]$Bytes) { $b = New-Object byte[] $Bytes; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b) }

New-Secret 48   # JWT_SECRET           (mínimo 32 bytes)
New-Secret 32   # TOTP_ENCRYPTION_KEY  (exactamente 32 bytes en base64)
New-Secret 24   # POSTGRES_PASSWORD
New-Secret 24   # REDIS_PASSWORD
```

- Sin un `JWT_SECRET` de al menos 32 bytes, o sin un `TOTP_ENCRYPTION_KEY` válido, el backend **no arranca**. El valor de ejemplo de `TOTP_ENCRYPTION_KEY` es inválido a propósito.
- Si el puerto 5432 está ocupado (por ejemplo, por un PostgreSQL local), cambia `DB_PORT` en `.env`, por ejemplo a `5433`.
- `.env` nunca se versiona.

### 2. Infraestructura

```powershell
docker compose up -d
docker compose ps        # postgres y redis deben quedar "healthy"
```

Los puertos se publican solo en `127.0.0.1`.

### 3. Backend (http://localhost:8080)

```powershell
cd backend
.\gradlew.bat bootRun
```

- Ejecútalo desde `backend/`: el backend importa `../.env`.
- `.env.example` trae `SPRING_PROFILES_ACTIVE=dev`. Ese perfil habilita Swagger (http://localhost:8080/swagger-ui.html), la cookie sin `Secure` (para http://localhost) y el **modo demo**, que acepta el header `X-Simulated-IP`. Fuera del desarrollo local hay que usar otro perfil.
- Al arrancar, Flyway crea las tablas.
- Health: http://localhost:8080/actuator/health.

### 4. Frontend (http://localhost:3000)

En otra terminal:

```powershell
cd frontend
Copy-Item .env.example .env.local
notepad .env.local       # cambia NEXT_PUBLIC_DEMO_MODE=false por NEXT_PUBLIC_DEMO_MODE=true
npm install
npm run dev
```

Con `NEXT_PUBLIC_DEMO_MODE=true`, el login y el registro muestran un selector de IP simulada, útil para ver las zonas de riesgo. Las variables `NEXT_PUBLIC_*` se incrustan al compilar: tras cambiarlas, reinicia `npm run dev`.

### 5. Registrar un usuario

En http://localhost:3000/register (la contraseña debe tener entre 12 y 128 caracteres). También por API:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/register -ContentType 'application/json' -Body '{"email":"tu@correo","password":"una-contrasena-larga"}'
```

La respuesta es siempre el mismo `202`, exista o no el email.

### 6. Promover un administrador

No hay admin sembrado con contraseña. Registra un usuario y promuévelo por SQL. El usuario y la base son `authshield`, los de `.env.example`:

```powershell
"UPDATE users SET role='ADMIN' WHERE email='tu@correo';" | docker exec -i authshield-postgres psql -U authshield -d authshield
```

- Debe responder `UPDATE 1`.
- El rol viaja en el JWT: **cierra sesión y vuelve a entrar**. Luego, en `/me`, aparece el enlace al panel.

## Pruebas

Desde la raíz del repositorio:

```powershell
cd backend;   .\gradlew.bat test; cd ..                    # unitarias + integración (Testcontainers: requiere Docker)
cd frontend;  npm test; npm run lint; npm run build; cd ..  # Vitest, ESLint y build de producción
cd simulator; npm test; cd ..                               # pruebas del simulador (node:test)
```

## Simulador de ataque

Solo ataca `localhost` o `127.0.0.1` y usa cuentas sintéticas que registra él mismo. Necesita el backend en modo demo (perfil `dev`). Detalle en [`simulator/README.md`](simulator/README.md).

Hacen falta dos terminales, ambas abiertas en la raíz del repositorio.

```powershell
# 1) Sin protección. Terminal A: backend con el motor desactivado (solo se permite en modo demo).
cd backend; $env:RISK_ENABLED='false'; .\gradlew.bat bootRun
#    Terminal B:
cd simulator; node simulate.mjs run --mode sin-proteccion
#    Detén el backend (Ctrl+C) en la terminal A.

# 2) Protegido. Terminal A: backend normal.
Remove-Item Env:RISK_ENABLED; .\gradlew.bat bootRun
#    Terminal B (ya en simulator/): vacía los contadores de Redis y ejecuta.
docker exec authshield-redis sh -c 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli FLUSHALL'
node simulate.mjs run --mode protegido
node simulate.mjs compare     # genera results/comparacion.md
```

Opciones: `--base-url` (por defecto `http://localhost:8080`), `--rate` (req/s, 5 por defecto) y `--seed`.

## Rutas y endpoints

**Frontend**

| Ruta | Acceso | Contenido |
|---|---|---|
| `/login` | pública | Inicio de sesión (selector de IP solo en modo demo). |
| `/login/verify` | tras un CHALLENGE | Código TOTP de 6 dígitos. |
| `/register` | pública | Registro. |
| `/me` | sesión | Datos de la cuenta. |
| `/account/security` | sesión | Estado y activación del 2FA (QR, clave manual). |
| `/admin` | ADMIN | KPIs de 24 h y distribución por zona. |
| `/admin/events` | ADMIN | Intentos con score, reglas, filtros y paginación. |
| `/admin/ips` | ADMIN | IPs recientes con la zona de su último intento. |
| `/admin/rules` | ADMIN | Pesos y umbrales (solo lectura). |

**Backend** (todo lo no declarado se deniega)

| Método y ruta | Acceso | Descripción |
|---|---|---|
| `POST /api/auth/register` | pública | Registro. Responde siempre el mismo `202`. |
| `POST /api/auth/login` | pública | `200` con tokens (ALLOW), `403 CHALLENGE_REQUIRED` con `challengeId` (CHALLENGE), `429` (BLOCK) o `401`. |
| `POST /api/auth/2fa/verify` | pública | `{challengeId, code}`: `200` con tokens o el mismo `401`. |
| `POST /api/auth/refresh` · `POST /api/auth/logout` | cookie | Rota el refresh token (detecta reutilización) / revoca la familia. |
| `GET /api/auth/me` | Bearer | Datos del usuario. |
| `POST /api/auth/2fa/setup` · `POST /api/auth/2fa/enable` · `GET /api/auth/2fa/status` | Bearer | Activación y estado del 2FA. |
| `GET /api/admin/stats` · `events` · `ips` · `risk-config` | Bearer, rol ADMIN | Panel de administración. Es lo único que expone score y reglas. |
| `GET /actuator/health` | pública | Estado del servicio. |

## Resultados del simulador

Corrida de referencia del 2026-11-22 (semilla `20261122`, 5 req/s). El mismo ataque se ejecutó con y sin el motor de riesgo. Los reportes están en [`simulator/results/ejemplo/`](simulator/results/ejemplo/) y el análisis en [`docs/fase-7-simulador.md`](docs/fase-7-simulador.md).

| Total | Sin protección | Protegido |
|---|---:|---:|
| Credenciales válidas probadas por los atacantes | 36 | 36 |
| **Comprometidas (200 con tokens)** | **36** | **17** |
| Intentos de ataque frenados (403/429) | 0 | 214 de 380 |
| **Falsos positivos en tráfico legítimo** | **0** | **0** |

| Escenario (comprometidas sin → con protección) | Resultado |
|---|---|
| Stuffing desde 1 IP | 15 → **0**. BLOCK desde el intento 11. |
| Stuffing distribuido (2 intentos por IP) | 15 → **15**. No se detecta. |
| Password spraying desde 1 IP | 5 → **2**. Cayeron en los primeros intentos, aún en ALLOW. |
| Fuerza bruta contra 1 cuenta | 1 → **0**. CHALLENGE, nunca BLOCK. |

- **Lectura honesta:** contra ataques desde una sola IP, las comprometidas bajan de 21 a 2. Contra el ataque distribuido, el más realista, las reglas actuales no aportan nada.
- **Validez:** es una sola semilla, a tasa fija, con IPs simuladas por header y en local. Ilustra el comportamiento de las reglas; no es una estimación de efectividad real.
- **Información al atacante:** desde la Fase 4, en CHALLENGE el atacante no puede saber qué credenciales son válidas.

## Limitaciones conocidas y trabajo futuro

- **Contraseñas filtradas (HIBP, +10):** no implementado. El registro todavía no consulta HIBP.
- **País o dispositivo nuevo (+15):** no implementado.
- **2FA solo en zona CHALLENGE:** en ALLOW se emiten tokens con la contraseña aunque el usuario tenga 2FA. Exigirlo solo a quien lo activó revelaría en la respuesta si la contraseña es correcta. Ver [fase-4](docs/fase-4-2fa.md).
- **Usuarios sin 2FA en zona CHALLENGE** no pueden entrar hasta que baje el riesgo.
- **Stuffing distribuido sin detección:** tres de las cuatro reglas usan como clave la IP o la cuenta. Sin reglas como "IPs distintas por cuenta" o una política global adaptativa, rotar IPs evade el motor.
- **Fuerza bruta contra una cuenta:** desde una IP el máximo es 65 (30 + 20 + 15), así que se queda en CHALLENGE y nunca llega a BLOCK.
- **Ventana inicial en ALLOW:** los primeros intentos de una IP nueva siempre son ALLOW. Un ataque *low and slow* se mantiene por debajo de los umbrales.
- **Sin códigos de respaldo** ni forma de desactivar o reconfigurar el 2FA. El panel de administración es de solo lectura.
- **Pendientes de la revisión de seguridad** (detalle en [fase-4](docs/fase-4-2fa.md#revisión-de-seguridad)):
  - **Fallos de TOTP sin contador por cuenta:** el límite de 5 intentos es por desafío. Con la contraseña y rotando IPs, un atacante puede probar códigos sin techo.
  - **`/2fa/verify` sin límite de frecuencia**, y cada `challengeId` inventado escribe una fila en `login_attempts`, una tabla que nunca se purga.
  - **Sin prueba de concurrencia** del anti-replay ni del límite de intentos: la garantía está razonada, no verificada.
  - **Diferencias de tiempo residuales:** en zona CHALLENGE, la contraseña incorrecta actualiza dos contadores más en Redis (microsegundos), y el camino constante del verify iguala el trabajo pero no garantiza tiempos idénticos.
- **Modo demo** (perfil `dev`): acepta `X-Simulated-IP`, lo que permite elegir la IP. No debe usarse fuera de local.

## Documentación

Índice completo en [`docs/README.md`](docs/README.md).

- [Fase 1 — Autenticación básica](docs/fase-1-auth.md)
- [Fase 2 — Motor de riesgo](docs/fase-2-riesgo.md)
- [Fase 3 — Frontend](docs/fase-3-frontend.md)
- [Fase 4 — 2FA con TOTP](docs/fase-4-2fa.md)
- Fase 5 — Contraseñas filtradas (HIBP): no implementada
- [Fase 6 — Panel de administración](docs/fase-6-admin.md)
- [Fase 7 — Simulador de ataque](docs/fase-7-simulador.md)
- [Simulador: uso](simulator/README.md)
