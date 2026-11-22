# Comparación: sin protección vs. protegido

- Sin protección: 2026-11-22T18:56:40.244Z (runId `s77eb9`)
- Protegido: 2026-11-22T18:58:43.845Z (runId `s7a1om`)
- Misma semilla: sí (`20261122` / `20261122`)

## Resumen

| Total | Sin protección | Protegido |
|---|---:|---:|
| Credenciales válidas probadas por los atacantes | 36 | 36 |
| Comprometidas (200 con tokens) | 36 | 17 |
| Confirmadas al atacante por 403 (sin tokens) | 0 | 2 |
| Intentos de ataque frenados (403/429) | 0 | 172 |
| Falsos positivos en tráfico legítimo | 0 | 0 |

## Por escenario

| Métrica (sin protección → protegido) | a) Tráfico legítimo | b) Stuffing, 1 IP | c) Stuffing distribuido | d) Password spraying | e) Fuerza bruta, 1 cuenta | a2) Legítimo durante ataque |
|---|---:|---:|---:|---:|---:|---:|
| Intentos | 14 → **14** | 150 → **150** | 150 → **150** | 40 → **40** | 30 → **40** | 14 → **14** |
| 200 | 10 → **10** | 15 → **0** | 15 → **15** | 5 → **2** | 1 → **0** | 10 → **10** |
| 401 | 4 → **4** | 135 → **9** | 135 → **135** | 35 → **8** | 29 → **39** | 4 → **4** |
| 403 | 0 → **0** | 0 → **1** | 0 → **0** | 0 → **0** | 0 → **1** | 0 → **0** |
| 429 | 0 → **0** | 0 → **140** | 0 → **0** | 0 → **30** | 0 → **0** | 0 → **0** |
| 1.er 403 (CHALLENGE) | — → **—** | — → **9** | — → **—** | — → **—** | — → **30** | — → **—** |
| 1.er 429 (BLOCK) | — → **—** | — → **11** | — → **—** | — → **11** | — → **—** | — → **—** |
| Comprometidas | — → **—** | 15 → **0** | 15 → **15** | 5 → **2** | 1 → **0** | — → **—** |
| Confirmadas 403 | — → **—** | 0 → **1** | 0 → **0** | 0 → **0** | 0 → **1** | — → **—** |
| Frenados % | 0 → **0** | 0 → **94** | 0 → **0** | 0 → **75** | 0 → **2.5** | 0 → **0** |
| Falsos positivos | 0 → **0** | — → **—** | — → **—** | — → **—** | — → **—** | 0 → **0** |
