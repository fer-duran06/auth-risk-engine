# AuthShield - Motor de Autenticación Adaptativa

Proyecto académico individual (Seguridad Informática). API que evalúa el riesgo de cada intento de login y decide ALLOW / CHALLENGE (2FA TOTP) / BLOCK, para mitigar credential stuffing. Incluye frontend web, dashboard de administración y un simulador de ataque. Uso exclusivamente académico y defensivo.

## Estructura (monorepo)
- backend/    Kotlin + Spring Boot 3.5.x, Gradle Kotlin DSL, JDK 21 (toolchain)
- frontend/   Next.js (App Router) + TypeScript + Tailwind CSS
- simulator/  Script de credential stuffing (solo contra localhost, datos sintéticos)
- docs/       Propuesta, diagramas, decisiones
- docker-compose.yml  PostgreSQL 16 y Redis 7

## Arquitectura del backend (hexagonal)
Paquete base: com.authrisk
- domain/          Entidades, value objects, reglas de riesgo (RiskRule, RiskEngine, RiskPolicy). Sin dependencias de Spring.
- application/     Casos de uso y puertos (interfaces in/out).
- infrastructure/  Adaptadores: web (controllers REST), persistence (JPA + Flyway), redis (contadores), geoip, hibp.
Regla: domain no importa nada de application ni infrastructure; application solo depende de domain.
Cada regla de riesgo es una implementación de RiskRule, aislada y con pruebas unitarias.

## Decisiones de seguridad (no negociables)
- Contraseñas: Argon2id con salt. Nunca en logs, respuestas ni texto plano.
- Login: mensaje de error genérico y tiempo de respuesta similar exista o no el usuario.
- Tokens: JWT de acceso de ~15 min + refresh token opaco, hasheado en BD, rotativo; reuso detectado revoca la familia. Refresh en cookie HttpOnly; Secure; SameSite. Nada en localStorage.
- 2FA: TOTP (RFC 6238). Secreto cifrado con AES-GCM; clave fuera de la BD. Códigos de respaldo guardados hasheados.
- Contraseñas filtradas: HIBP con k-anonymity (solo se envían 5 caracteres del SHA-1).
- Score de riesgo visible solo para admin; nunca en vistas de usuario.
- Zonas: ALLOW < 30, CHALLENGE 30-69, BLOCK >= 70. Score limitado a 100.
- Si Redis falla, fallar de forma segura (exigir 2FA), no permitir todo.
- Consultas parametrizadas, CORS restringido, secretos solo por variables de entorno.

## Reglas de riesgo (pesos por defecto)
Velocidad por IP +30, Usuarios distintos por IP +25, Fallos por cuenta +20, Tasa global de fallos +15, País/dispositivo nuevo +15, Contraseña filtrada +10.

## Frontend
- Estilo oscuro tipo ciberseguridad: fondo #0B1020, tarjetas #121A30, acento azul neón #2EA8FF, naranja #FF8A1F (desafío), rojo (bloqueo), verde (permitido). Fuente sans para UI, monoespaciada para IPs, scores y códigos. Todo el texto en español.
- Diseño de referencia: 10 vistas hechas en Stitch (login, registro, 2FA, activar 2FA, seguridad de mi cuenta, dashboard, eventos, bloqueos y usuarios, reglas y umbrales, simulación).
- Prohibido incluir en la UI: "ISO 27001 Compliant", "SOC2", "modelo bayesiano", "Kafka", "WAF", "SIEM", "SMS/Slack/push", WebAuthn/passkeys, LDAP/SAML, "producción", copyright de empresa ficticia. Las contraseñas se "hashean", no se "encriptan".
- El selector "Simulador estado" del login solo existe con NEXT_PUBLIC_DEMO_MODE=true.
- Registro: botón deshabilitado si la contraseña está filtrada.

## Convenciones
- Commits convencionales (feat:, fix:, test:, docs:, chore:), uno por tarea.
- Una tarea por vez. Termina siempre con pruebas pasando y reporta qué comandos correr.
- Nunca commitear .env ni secretos. Mantener .env.example actualizado.
- Código limpio y documentación concisa (KDoc breve donde aporte). Comentarios en español.
- No agregar dependencias sin explicar por qué.
- No inventar funcionalidades fuera del alcance de la fase pedida.

## Comandos
- Infra: docker compose up -d
- Backend: cd backend && ./gradlew bootRun | ./gradlew test
- Frontend: cd frontend && npm run dev