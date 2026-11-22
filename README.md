# AuthShield — Motor de Autenticación Adaptativa

Proyecto académico (Seguridad Informática). API que evalúa el riesgo de cada intento de login y decide
**ALLOW / CHALLENGE (2FA TOTP) / BLOCK** para mitigar ataques de *credential stuffing*. Incluye frontend web,
dashboard de administración y un simulador de ataque. Uso exclusivamente académico y defensivo.

> Estado: autenticación (JWT + refresh rotativo), motor de riesgo, frontend de login, registro y `/me` (Fase 3)
> y simulador de ataque con comparación con y sin protección (Fase 7).
> Detalle por fase en [`docs/`](docs/README.md).

## Estructura

| Carpeta      | Contenido                                                       |
|--------------|-----------------------------------------------------------------|
| `backend/`   | Kotlin + Spring Boot 3.5, Gradle (Kotlin DSL), JDK 21, hexagonal |
| `frontend/`  | Next.js (App Router) + TypeScript + Tailwind CSS                |
| `simulator/` | Simulador de credential stuffing (solo localhost, datos sintéticos) |
| `docs/`      | Propuesta, diagramas y decisiones                               |

## Requisitos

- Docker Desktop (con Docker Compose v2)
- JDK 17+ para ejecutar Gradle (la toolchain JDK 21 se descarga sola si falta)
- Node.js 22.12+ y npm (Vitest 5 lo exige)

## Cómo levantar todo

```bash
# 1. Variables de entorno (cambia las contraseñas)
cp .env.example .env
cp frontend/.env.example frontend/.env.local

# 2. Infraestructura: PostgreSQL 16 y Redis 7
docker compose up -d
docker compose ps          # ambos deben quedar "healthy"

# 3. Backend (http://localhost:8080)
cd backend
./gradlew test             # pruebas (usa Testcontainers: requiere Docker)
./gradlew bootRun          # en Windows: .\gradlew.bat bootRun

# 4. Frontend (http://localhost:3000)
cd frontend
npm install
npm test                   # pruebas unitarias (Vitest)
npm run dev
```

- Health: http://localhost:8080/actuator/health
- Swagger (perfil `dev`): http://localhost:8080/swagger-ui.html

El backend lee su configuración de variables de entorno; en desarrollo además importa el `.env` de la raíz.
Si el puerto 5432 está ocupado (p. ej. por un PostgreSQL local), cambia `DB_PORT` en `.env` (por ejemplo a `5433`).
