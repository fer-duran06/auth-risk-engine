# Fase 7 — Simulador de ataque

Mide, contra el backend local, cuánto frena el motor de riesgo un ataque de credential stuffing y sus variantes. Se compara la misma batería de ataques con el motor activo y desactivado.
Las fases 4 a 6 (2FA, HIBP, administración) aún no existen. Los resultados reflejan las 4 reglas de la Fase 2: velocidad por IP (+30), usuarios distintos por IP (+25), fallos por cuenta (+20) y tasa global de fallos (+15).

Uso: [`simulator/README.md`](../simulator/README.md).

## Diseño

- **Herramienta:** Node 22, ESM, `fetch` nativo y sin dependencias.
  - Solo acepta `localhost` o `127.0.0.1` como objetivo, validando el hostname ya parseado.
  - No sigue redirecciones.
  - Envía las peticiones en secuencia, al ritmo de `--rate`.
- **Datos sintéticos** generados con semilla: el mismo `--seed` produce las mismas cuentas, contraseñas y orden de ataque.
  - Cada corrida usa un `runId` en los emails, así los fallos por cuenta de corridas anteriores no la afectan.
  - Se registran 81 cuentas:
    - 10 legítimas;
    - 30 víctimas del stuffing (15 con su contraseña real en la filtración y 15 con una contraseña antigua);
    - 40 del spraying (5 usan la contraseña común);
    - 1 de la fuerza bruta.
  - La lista filtrada tiene 150 entradas: 15 válidas (10 %) y 120 de cuentas inexistentes.
- **Aislamiento por IP:** cada escenario tiene su bloque RFC 5737 y lo envía en `X-Simulated-IP`, que el backend solo acepta con `app.demo-mode=true`. La tasa global de fallos, en cambio, es compartida por diseño: refleja el estado de todo el sistema.
- **`app.risk.enabled=false`** (variable `RISK_ENABLED`):
  - el login omite la evaluación, no usa Redis, todo intento es ALLOW y queda registrado con la regla `RISK_DISABLED`;
  - el backend no arranca así salvo con `app.demo-mode=true`;
  - con el motor desactivado no se escriben contadores, así que la corrida sin protección no contamina la protegida. Se verificó: Redis quedó con 0 claves después de esa corrida.
- **Métricas por escenario:**
  - intentos;
  - respuestas 200, 401, 403, 429 y otras;
  - primer 403 y primer 429;
  - credenciales comprometidas (200 en un ataque) y confirmadas por 403;
  - falsos positivos (403 o 429 en tráfico legítimo);
  - tasa real.

## Resultados (corrida de referencia)

Corrida del 2026-11-22, semilla `20261122`, a 5 req/s (tasa real de 4,7 a 5 req/s). El backend corrió en el puerto 8081 y se reinició entre los dos modos. Los reportes completos están en [`simulator/results/ejemplo/`](../simulator/results/ejemplo/).

| Total | Sin protección | Protegido |
|---|---:|---:|
| Credenciales válidas probadas por los atacantes | 36 | 36 |
| **Comprometidas (200 con tokens)** | **36** | **17** |
| Confirmadas al atacante por 403 (sin tokens) | 0 | 2 |
| Intentos de ataque frenados (403/429) | 0 | 172 de 380 |
| **Falsos positivos en tráfico legítimo** | **0** | **0** |

| Escenario (protegido) | Intentos | 200 | 401 | 403 | 429 | 1.er 403 | 1.er 429 | Comprometidas (sin → con) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| a) Legítimo | 14 | 10 | 4 | 0 | 0 | — | — | — |
| b) Stuffing, 1 IP | 150 | 0 | 9 | 1 | 140 | 9 | 11 | 15 → **0** |
| c) Stuffing distribuido | 150 | 15 | 135 | 0 | 0 | — | — | 15 → **15** |
| d) Password spraying | 40 | 2 | 8 | 0 | 30 | — | 11 | 5 → **2** |
| e) Fuerza bruta, 1 cuenta | 40 | 0 | 39 | 1 | 0 | 30 | — | 1 → **0** |
| a2) Legítimo durante ataque | 14 | 10 | 4 | 0 | 0 | — | — | — |

Sin protección, todos los escenarios dieron solo 200 y 401: las 36 credenciales válidas se comprometieron. En la fuerza bruta hubo 30 intentos y no 40, porque el atacante se detiene al obtener tokens.

## Interpretación

La evolución del score se tomó de `login_attempts` (corrida protegida).

- **b) Stuffing desde una IP: se detiene en el intento 11.**
  - Intentos 1 a 5: score 0 (ALLOW).
  - Intento 6: score 25, porque se dispara la regla de usuarios distintos.
  - Intentos 7 a 10: score 40 (CHALLENGE), porque la tasa global supera el 50 %.
  - Desde el intento 11: score 70 (BLOCK), porque se suma la velocidad por IP.

  La zona CHALLENGE empezó en el intento 7, pero solo se ve en el 9, la primera credencial válida (403). Con contraseña incorrecta la respuesta sigue siendo 401. De las 15 válidas, 14 recibieron 429 y el atacante no supo que eran correctas. **Advertencia sobre la suerte:** en esta semilla ninguna credencial válida cayó en los 6 primeros intentos (ALLOW). Con un 10 % de válidas, la probabilidad de que al menos una caiga ahí es de aproximadamente 1 − 0,9⁶ ≈ 47 %. Con otro orden podría haber 1 o 2 comprometidas.
- **c) Stuffing distribuido: no se detecta.** Los 150 intentos quedaron en ALLOW con score 15; solo se disparó la tasa global. Con 2 intentos por IP no se activa ninguna regla por IP, y cada cuenta recibe un único intento, así que los fallos por cuenta tampoco. Las 15 credenciales válidas se comprometieron igual que sin protección.
- **d) Spraying: frenado, con 2 cuentas comprometidas.** Los intentos 1 a 5 quedaron en ALLOW con score 15 (tasa global). Ahí cayeron 2 de las 5 cuentas con la contraseña común. Del 6 al 10 el score fue 40 (CHALLENGE) y desde el 11 fue 70 (BLOCK).
- **e) Fuerza bruta: CHALLENGE, nunca BLOCK.**
  - Intentos 1 a 5: score 15.
  - Del 6.º al 10.º: score 35, porque se suman los fallos por cuenta.
  - Desde el 11.º: score 65, porque se suma la velocidad por IP.

  65 queda por debajo de 70: como hay un solo email, la regla de usuarios distintos nunca suma. La contraseña correcta (intento 30) recibió 403. No se emitieron tokens, pero el atacante quedó informado.
- **a y a2) Tráfico legítimo: 0 falsos positivos.** Durante el ataque (a2), todos los intentos legítimos tuvieron score 15 por la tasa global y siguieron en ALLOW, porque esa regla por sí sola no alcanza el umbral de 30.

**Conclusión:** contra ataques desde una sola IP (stuffing, spraying y fuerza bruta) el motor reduce las credenciales comprometidas de 21 a 2, sin afectar al tráfico legítimo. Contra el ataque distribuido, el más realista en la práctica, las reglas actuales no aportan nada. En total, las comprometidas pasan de 36 a 17 (−53 %).

## Limitaciones y trabajo futuro

### Limitaciones observadas

1. **El ataque distribuido casi no se detecta.** Tres de las cuatro reglas usan como clave la IP o la cuenta. Un atacante que rota IPs (botnets, proxies residenciales) y prueba una sola vez cada cuenta no dispara ninguna. La única señal agregada, la tasa global de fallos, suma +15 y nunca alcanza por sí sola el umbral de CHALLENGE (30).
2. **La fuerza bruta contra una cuenta no llega a BLOCK.** Desde una IP el máximo es 65: velocidad 30 + fallos por cuenta 20 + tasa global 15. El atacante puede seguir probando indefinidamente en CHALLENGE. No obtiene tokens, pero cada contraseña se verifica con Argon2 y el atacante conoce el resultado.
3. **El 403 en CHALLENGE confirma al atacante que la credencial es válida.** En CHALLENGE la contraseña se verifica: si es incorrecta responde 401 y si es correcta, 403 `CHALLENGE_REQUIRED`. Aunque no se emitan tokens, el atacante aprende qué credenciales funcionan (2 en la corrida protegida) y puede usarlas en otros servicios o reintentar con otra IP más tarde. Para los ataques automatizados, el CHALLENGE actual funciona como un oráculo.
4. **Hay una ventana inicial en ALLOW.** Los primeros intentos de una IP nueva siempre son ALLOW, porque los contadores solo cuentan intentos previos. En la corrida, el spraying comprometió ahí 2 cuentas. Un atacante paciente (*low and slow*, menos de 10 intentos por minuto y menos de 5 emails cada 10 minutos por IP) se mantiene siempre por debajo de los umbrales.
5. **Validez del experimento:**
   - una sola semilla y un solo orden de ataque (ver la advertencia del escenario b);
   - tasa fija de 5 req/s;
   - IPs simuladas mediante un header que solo existe en modo demo;
   - el backend corre en local con un único usuario atacante.

   Los números ilustran el comportamiento de las reglas y no son una estimación de efectividad en producción.

### Trabajo futuro

- **Resolver el oráculo del 403:** en CHALLENGE, responder igual sea la contraseña correcta o no. Por ejemplo, pedir siempre el segundo factor y verificar contraseña y TOTP juntos, sin revelar cuál falló. Encaja con la fase de 2FA.
- **Reglas que no dependan de la IP:**
  - IPs distintas por cuenta;
  - fallos por cuenta con más peso o con un umbral alto que lleve directamente a BLOCK de esa cuenta (sin castigarla con intentos bloqueados, como ya se hace);
  - política global adaptativa: cuando la tasa global de fallos es alta, bajar los umbrales o exigir CHALLENGE a los logins sin historial.
- **Señales de las fases pendientes:** país o dispositivo nuevo (+15) y contraseña filtrada (+10, HIBP) añadirían puntos justo en el ataque distribuido. Hay que volver a ejecutar el simulador cuando existan.
- **Simulador:** varias semillas con media y desviación, el modo *low and slow*, concurrencia y un escenario con IPs reutilizadas entre cuentas.
