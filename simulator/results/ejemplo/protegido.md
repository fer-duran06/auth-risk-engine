# Simulación AuthShield — Protegido (motor activo)

- Fecha: 2026-11-22T19:44:56.046Z
- Objetivo: `http://localhost:8081`
- runId: `s8xgq7` · semilla: `20261122`
- Tasa pedida: 5 req/s · cuentas registradas: 81

## Resultados

| Esc. | Escenario | Intentos | 200 | 401 | 403 | 429 | Otros | 1.er 403 | 1.er 429 | Válidas | Comprometidas | Falsos + |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| a | Tráfico legítimo | 14 | 10 | 4 | 0 | 0 | 0 | — | — | 10 | — | 0 |
| b | Stuffing, 1 IP | 150 | 0 | 6 | 4 | 140 | 0 | 7 | 11 | 15 | 0 | — |
| c | Stuffing distribuido | 150 | 15 | 135 | 0 | 0 | 0 | — | — | 15 | 15 | — |
| d | Password spraying | 40 | 2 | 3 | 5 | 30 | 0 | 6 | 11 | 5 | 2 | — |
| e | Fuerza bruta, 1 cuenta | 40 | 0 | 5 | 35 | 0 | 0 | 6 | — | 1 | 0 | — |
| a2 | Legítimo durante ataque | 14 | 10 | 4 | 0 | 0 | 0 | — | — | 10 | — | 0 |

Columnas: *Comprometidas* = credenciales válidas que obtuvieron tokens (200). *Falsos +* = intentos legítimos
frenados con 403/429. *1.er 403 / 429* = número de intento del primer CHALLENGE / BLOCK. En CHALLENGE la respuesta
es el mismo 403 sea la contraseña correcta o no (Fase 4): un 403 no le dice al atacante si la credencial es válida,
y sin el código TOTP de la víctima no obtiene tokens.

## Escenarios

### a) Tráfico legítimo

10 usuarios, cada uno desde su IP (192.0.2.x); 1 de cada 3 se equivoca una vez.

- Frenados (403/429): 0 de 14 (0 %) · duración 3.1 s · 4.5 req/s reales
- Falsos positivos: 0 · usuarios con contraseña correcta rechazados: 0
- Secuencia de respuestas: `401, 200×3, 401, 200×3, 401, 200×3, 401, 200`

### b) Stuffing, 1 IP

Lista filtrada de 150 credenciales (15 válidas) desde 198.51.100.10.

- Frenados (403/429): 144 de 150 (96 %) · duración 29.9 s · 5 req/s reales
- Válidas en la lista: 15 → comprometidas 0, con 403 1 y con 429 14 (sin tokens; el atacante no las distingue de las incorrectas)
- Secuencia de respuestas: `401×6, 403×4, 429×140`

### c) Stuffing distribuido

La misma lista repartida en rotación entre 75 IPs (203.0.113.x), 2 intentos por IP.

- Frenados (403/429): 0 de 150 (0 %) · duración 30.2 s · 5 req/s reales
- Válidas en la lista: 15 → comprometidas 15, con 403 0 y con 429 0 (sin tokens; el atacante no las distingue de las incorrectas)
- Secuencia de respuestas: `401×8, 200, 401, 200, 401×4, 200, 401×6, 200, 401×3, 200, 401×2, 200, 401×10, 200, 401×7, 200, 401×5, 200, 401×22, 200, 401×11, 200, 401×6, 200, 401×3, 200, 401×19, 200, 401×18, 200, 401×10`

### d) Password spraying

"Primavera2026!" contra 40 cuentas (5 la usan) desde 198.51.100.20.

- Frenados (403/429): 35 de 40 (87.5 %) · duración 7.9 s · 5.1 req/s reales
- Válidas en la lista: 5 → comprometidas 2, con 403 0 y con 429 3 (sin tokens; el atacante no las distingue de las incorrectas)
- Secuencia de respuestas: `200×2, 401×3, 403×5, 429×30`

### e) Fuerza bruta, 1 cuenta

40 contraseñas contra una cuenta desde 198.51.100.30; la correcta es la n.º 30.

- Frenados (403/429): 35 de 40 (87.5 %) · duración 8.1 s · 5 req/s reales
- Válidas en la lista: 1 → comprometidas 0, con 403 1 y con 429 0 (sin tokens; el atacante no las distingue de las incorrectas)
- Secuencia de respuestas: `401×5, 403×35`

### a2) Legítimo durante ataque

Los mismos usuarios legítimos desde otras IPs (192.0.2.101+), con la tasa global de fallos ya alta.

- Frenados (403/429): 0 de 14 (0 %) · duración 2.8 s · 5 req/s reales
- Falsos positivos: 0 · usuarios con contraseña correcta rechazados: 0
- Secuencia de respuestas: `401, 200×3, 401, 200×3, 401, 200×3, 401, 200`
