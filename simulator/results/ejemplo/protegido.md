# Simulación AuthShield — Protegido (motor activo)

- Fecha: 2026-11-22T18:58:43.845Z
- Objetivo: `http://localhost:8081`
- runId: `s7a1om` · semilla: `20261122`
- Tasa pedida: 5 req/s · cuentas registradas: 81

## Resultados

| Esc. | Escenario | Intentos | 200 | 401 | 403 | 429 | Otros | 1.er 403 | 1.er 429 | Válidas | Comprometidas | Confirmadas 403 | Falsos + |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| a | Tráfico legítimo | 14 | 10 | 4 | 0 | 0 | 0 | — | — | 10 | — | — | 0 |
| b | Stuffing, 1 IP | 150 | 0 | 9 | 1 | 140 | 0 | 9 | 11 | 15 | 0 | 1 | — |
| c | Stuffing distribuido | 150 | 15 | 135 | 0 | 0 | 0 | — | — | 15 | 15 | 0 | — |
| d | Password spraying | 40 | 2 | 8 | 0 | 30 | 0 | — | 11 | 5 | 2 | 0 | — |
| e | Fuerza bruta, 1 cuenta | 40 | 0 | 39 | 1 | 0 | 0 | 30 | — | 1 | 0 | 1 | — |
| a2 | Legítimo durante ataque | 14 | 10 | 4 | 0 | 0 | 0 | — | — | 10 | — | — | 0 |

Columnas: *Comprometidas* = credenciales válidas que obtuvieron tokens (200). *Confirmadas 403* = credenciales válidas
que recibieron 403 CHALLENGE (sin tokens, pero el atacante sabe que son correctas). *Falsos +* = intentos legítimos
frenados con 403/429. *1.er 403 / 429* = número de intento del primer CHALLENGE / BLOCK visible para el cliente
(la zona CHALLENGE solo se ve con la contraseña correcta; con una incorrecta la respuesta sigue siendo 401).

## Escenarios

### a) Tráfico legítimo

10 usuarios, cada uno desde su IP (192.0.2.x); 1 de cada 3 se equivoca una vez.

- Frenados (403/429): 0 de 14 (0 %) · duración 3.0 s · 4.7 req/s reales
- Falsos positivos: 0 · usuarios con contraseña correcta rechazados: 0
- Secuencia de respuestas: `401, 200×3, 401, 200×3, 401, 200×3, 401, 200`

### b) Stuffing, 1 IP

Lista filtrada de 150 credenciales (15 válidas) desde 198.51.100.10.

- Frenados (403/429): 141 de 150 (94 %) · duración 30.0 s · 5 req/s reales
- Válidas en la lista: 15 → comprometidas 0, confirmadas por 403 1, bloqueadas con 429 14
- Secuencia de respuestas: `401×8, 403, 401, 429×140`

### c) Stuffing distribuido

La misma lista repartida en rotación entre 75 IPs (203.0.113.x), 2 intentos por IP.

- Frenados (403/429): 0 de 150 (0 %) · duración 30.0 s · 5 req/s reales
- Válidas en la lista: 15 → comprometidas 15, confirmadas por 403 0, bloqueadas con 429 0
- Secuencia de respuestas: `401×8, 200, 401, 200, 401×4, 200, 401×6, 200, 401×3, 200, 401×2, 200, 401×10, 200, 401×7, 200, 401×5, 200, 401×22, 200, 401×11, 200, 401×6, 200, 401×3, 200, 401×19, 200, 401×18, 200, 401×10`

### d) Password spraying

"Primavera2026!" contra 40 cuentas (5 la usan) desde 198.51.100.20.

- Frenados (403/429): 30 de 40 (75 %) · duración 8.0 s · 5 req/s reales
- Válidas en la lista: 5 → comprometidas 2, confirmadas por 403 0, bloqueadas con 429 3
- Secuencia de respuestas: `200×2, 401×8, 429×30`

### e) Fuerza bruta, 1 cuenta

40 contraseñas contra una cuenta desde 198.51.100.30; la correcta es la n.º 30.

- Frenados (403/429): 1 de 40 (2.5 %) · duración 8.0 s · 5 req/s reales
- Válidas en la lista: 1 → comprometidas 0, confirmadas por 403 1, bloqueadas con 429 0
- Secuencia de respuestas: `401×29, 403, 401×10`

### a2) Legítimo durante ataque

Los mismos usuarios legítimos desde otras IPs (192.0.2.101+), con la tasa global de fallos ya alta.

- Frenados (403/429): 0 de 14 (0 %) · duración 2.8 s · 5 req/s reales
- Falsos positivos: 0 · usuarios con contraseña correcta rechazados: 0
- Secuencia de respuestas: `401, 200×3, 401, 200×3, 401, 200×3, 401, 200`
