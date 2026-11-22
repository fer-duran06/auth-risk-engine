# Comparación: sin protección vs. protegido

- Sin protección: 2026-11-22T19:42:55.107Z (runId `s8uver`)
- Protegido: 2026-11-22T19:44:56.046Z (runId `s8xgq7`)
- Misma semilla: sí (`20261122` / `20261122`)

## Resumen

| Total | Sin protección | Protegido |
|---|---:|---:|
| Credenciales válidas probadas por los atacantes | 36 | 36 |
| Comprometidas (200 con tokens) | 36 | 17 |
| Intentos de ataque frenados (403/429) | 0 | 214 |
| Falsos positivos en tráfico legítimo | 0 | 0 |

## Por escenario

| Métrica (sin protección → protegido) | a) Tráfico legítimo | b) Stuffing, 1 IP | c) Stuffing distribuido | d) Password spraying | e) Fuerza bruta, 1 cuenta | a2) Legítimo durante ataque |
|---|---:|---:|---:|---:|---:|---:|
| Intentos | 14 → **14** | 150 → **150** | 150 → **150** | 40 → **40** | 30 → **40** | 14 → **14** |
| 200 | 10 → **10** | 15 → **0** | 15 → **15** | 5 → **2** | 1 → **0** | 10 → **10** |
| 401 | 4 → **4** | 135 → **6** | 135 → **135** | 35 → **3** | 29 → **5** | 4 → **4** |
| 403 | 0 → **0** | 0 → **4** | 0 → **0** | 0 → **5** | 0 → **35** | 0 → **0** |
| 429 | 0 → **0** | 0 → **140** | 0 → **0** | 0 → **30** | 0 → **0** | 0 → **0** |
| 1.er 403 (CHALLENGE) | — → **—** | — → **7** | — → **—** | — → **6** | — → **6** | — → **—** |
| 1.er 429 (BLOCK) | — → **—** | — → **11** | — → **—** | — → **11** | — → **—** | — → **—** |
| Comprometidas | — → **—** | 15 → **0** | 15 → **15** | 5 → **2** | 1 → **0** | — → **—** |
| Frenados % | 0 → **0** | 0 → **96** | 0 → **0** | 0 → **87.5** | 0 → **87.5** | 0 → **0** |
| Falsos positivos | 0 → **0** | — → **—** | — → **—** | — → **—** | — → **—** | 0 → **0** |
