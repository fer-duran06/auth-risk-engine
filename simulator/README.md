# AuthShield — Simulador de ataque

Herramienta académica que mide cuánto frena el motor de riesgo un ataque de credential stuffing. **Solo ataca al backend local** (`localhost` o `127.0.0.1`) y usa cuentas sintéticas (`@ejemplo.test`) que ella misma registra.
Diseño, resultados e interpretación: [`docs/fase-7-simulador.md`](../docs/fase-7-simulador.md).

## Requisitos

- Node.js 22 o superior. No necesita `npm install`: no tiene dependencias.
- Infraestructura levantada (`docker compose up -d`).
- Backend con `TOTP_ENCRYPTION_KEY` definida (obligatoria desde la Fase 4) y `app.demo-mode=true`; el perfil `dev` activa el modo demo por defecto. Sin el modo demo, `X-Simulated-IP` se ignora y todos los escenarios comparten la IP 127.0.0.1.

## Uso

```bash
cd simulator
node simulate.mjs run --mode protegido|sin-proteccion [--base-url http://localhost:8080] [--rate 5] [--seed 20261122]
node simulate.mjs compare     # contrasta la última corrida de cada modo → results/comparacion.md
npm test                      # pruebas del simulador (node:test)
```

| Opción | Por defecto | Descripción |
|---|---|---|
| `--mode` | obligatoria | Etiqueta de la corrida. El simulador avisa si el comportamiento observado no coincide con ella. |
| `--base-url` | `http://localhost:8080` | Solo se acepta `localhost` o `127.0.0.1` (http/https), sin credenciales en la URL. |
| `--rate` | `5` | Peticiones por segundo (secuenciales, máximo 50). |
| `--seed` | `20261122` | Semilla de los datos sintéticos. Con la misma semilla las dos corridas son comparables. |

## Comparación con y sin protección

El modo del motor lo decide el backend. Hay que reiniciarlo entre una corrida y otra:

```bash
# 1. Sin protección (el backend solo arranca así si app.demo-mode=true)
cd backend && RISK_ENABLED=false ./gradlew bootRun
cd simulator && node simulate.mjs run --mode sin-proteccion
# detener el backend

# 2. Protegido
cd backend && ./gradlew bootRun          # RISK_ENABLED=true por defecto
cd simulator && node simulate.mjs run --mode protegido

# 3. Reporte comparativo
node simulate.mjs compare
```

Con el motor desactivado no se usa Redis, así que la corrida sin protección no deja contadores. Para **repetir** una corrida protegida antes de 15 minutos (la ventana más larga), vacía los contadores. El comando toma la contraseña de la variable que el contenedor recibió del `.env`, sin mostrarla:

```bash
docker exec authshield-redis sh -c 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli FLUSHALL'
```

En CHALLENGE el backend responde siempre el mismo 403 con un `challengeId`, sea la contraseña correcta o no (Fase 4). Por eso el simulador ya no cuenta credenciales "confirmadas por 403": solo informa, como dato interno, cuántas válidas recibieron 403 o 429 sin tokens.

## Salida

- Tabla en consola.
- `results/<fecha>-<modo>.json` y `.md`: métricas por escenario. No incluyen contraseñas ni emails.
- `results/comparacion.md`: generado por `compare`.
- `results/ejemplo/` (versionado): reportes de la corrida de referencia documentada en la Fase 7. El resto de `results/` lo ignora git.

## Escenarios

| Id | Escenario | IPs (RFC 5737) |
|---|---|---|
| a | Tráfico legítimo: 10 usuarios, algunos con un error de tecleo | 192.0.2.1–10 |
| b | Credential stuffing: 150 credenciales filtradas (15 válidas) desde una IP | 198.51.100.10 |
| c | La misma lista distribuida, 2 intentos por IP | 203.0.113.1–75 |
| d | Password spraying: una contraseña común contra 40 cuentas | 198.51.100.20 |
| e | Fuerza bruta: 40 contraseñas contra una cuenta | 198.51.100.30 |
| a2 | Los mismos usuarios legítimos con el ataque en curso | 192.0.2.101–110 |
