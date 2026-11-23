# Fase 7 — Simulador de ataque

Mide, contra el backend local, cuánto frena el motor de riesgo un ataque de credential stuffing y sus variantes. Se compara la misma batería de ataques con el motor activo y desactivado.
Los resultados reflejan las 4 reglas de la Fase 2: velocidad por IP (+30), usuarios distintos por IP (+25), fallos por cuenta (+20) y tasa global de fallos (+15). La corrida de referencia es posterior a la Fase 4 (2FA): en CHALLENGE el 403 ya no revela si la contraseña es correcta. HIBP (fase 5) no está implementado, así que la regla de contraseña filtrada no participa.

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
  - credenciales comprometidas (200 en un ataque);
  - válidas que recibieron 403 o 429 (solo el simulador lo sabe: para el atacante son indistinguibles de las incorrectas);
  - falsos positivos (403 o 429 en tráfico legítimo);
  - tasa real.

## Resultados (corrida de referencia)

Corrida del 2026-11-22 tras la Fase 4, semilla `20261122`, a 5 req/s. El backend corrió en el puerto 8081, se reinició entre los dos modos y Redis se vació antes de cada corrida. Los reportes completos están en [`simulator/results/ejemplo/`](../simulator/results/ejemplo/).

| Total | Sin protección | Protegido |
|---|---:|---:|
| Credenciales válidas probadas por los atacantes | 36 | 36 |
| **Comprometidas (200 con tokens)** | **36** | **17** |
| Intentos de ataque frenados (403/429) | 0 | 214 de 380 |
| **Falsos positivos en tráfico legítimo** | **0** | **0** |

| Escenario (protegido) | Intentos | 200 | 401 | 403 | 429 | 1.er 403 | 1.er 429 | Comprometidas (sin → con) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| a) Legítimo | 14 | 10 | 4 | 0 | 0 | — | — | — |
| b) Stuffing, 1 IP | 150 | 0 | 6 | 4 | 140 | 7 | 11 | 15 → **0** |
| c) Stuffing distribuido | 150 | 15 | 135 | 0 | 0 | — | — | 15 → **15** |
| d) Password spraying | 40 | 2 | 3 | 5 | 30 | 6 | 11 | 5 → **2** |
| e) Fuerza bruta, 1 cuenta | 40 | 0 | 5 | 35 | 0 | 6 | — | 1 → **0** |
| a2) Legítimo durante ataque | 14 | 10 | 4 | 0 | 0 | — | — | — |

Sin protección, todos los escenarios dieron solo 200 y 401: las 36 credenciales válidas se comprometieron. En la fuerza bruta sin protección hubo 30 intentos, porque el atacante se detiene al obtener tokens. Protegido, nunca los obtiene y prueba las 40 contraseñas.

**Cambio respecto de la corrida anterior a la Fase 4.** Las comprometidas no cambian (17). Lo que cambia es la información que recibe el atacante:

- Antes, en CHALLENGE la contraseña incorrecta daba 401 y la correcta 403, así que 2 credenciales quedaron "confirmadas" sin tokens.
- Ahora cualquier intento en CHALLENGE recibe el mismo 403 con un `challengeId`. Por eso el primer 403 aparece cuando empieza la zona (intentos 7, 6 y 6) y no con la primera credencial válida.
- Las 2 válidas que recibieron 403 (una en b, una en e) son indistinguibles de las 42 incorrectas que también lo recibieron. El simulador ya no cuenta "confirmadas por 403".

## Interpretación

La evolución del score es la misma de la Fase 2: el 2FA no cambia las reglas, solo la respuesta en CHALLENGE.

- **b) Stuffing desde una IP: se detiene en el intento 11.**
  - Intentos 1 a 6: ALLOW. Score 0, y 25 en el 6.º, cuando se dispara la regla de usuarios distintos.
  - Intentos 7 a 10: score 40 (CHALLENGE), porque la tasa global supera el 50 %. Los 4 reciben 403, con contraseña correcta o no.
  - Desde el intento 11: score 70 (BLOCK), porque se suma la velocidad por IP.

  De las 15 válidas, 1 recibió 403 y 14 recibieron 429: ninguna quedó identificada. **Advertencia sobre la suerte:** en esta semilla ninguna credencial válida cayó en los 6 primeros intentos (ALLOW). Con un 10 % de válidas, la probabilidad de que al menos una caiga ahí es de aproximadamente 1 − 0,9⁶ ≈ 47 %.
- **c) Stuffing distribuido: no se detecta.** Los 150 intentos quedaron en ALLOW con score 15; solo se disparó la tasa global. Con 2 intentos por IP no se activa ninguna regla por IP, y cada cuenta recibe un único intento, así que los fallos por cuenta tampoco. Las 15 credenciales válidas se comprometieron igual que sin protección.
- **d) Spraying: frenado, con 2 cuentas comprometidas.** En los intentos 1 a 5 (ALLOW, score 15) cayeron 2 de las 5 cuentas con la contraseña común. Del 6 al 10 hubo CHALLENGE (403) y desde el 11, BLOCK.
- **e) Fuerza bruta: CHALLENGE, nunca BLOCK.**
  - Intentos 1 a 5: score 15 (401).
  - Desde el 6.º: score 35 y luego 65 (403).

  Con un solo email, la regla de usuarios distintos nunca suma y el score no llega a 70. La contraseña correcta (intento 30) recibió el mismo 403 que las otras 34. Antes de la Fase 4 eso le revelaba al atacante que había acertado. Ahora no sabe cuál era y siguió probando hasta la 40.
- **a y a2) Tráfico legítimo: 0 falsos positivos.** Durante el ataque (a2), todos los intentos legítimos tuvieron score 15 por la tasa global y siguieron en ALLOW.

**Conclusión:** contra ataques desde una sola IP (stuffing, spraying y fuerza bruta), el motor reduce las credenciales comprometidas de 21 a 2 sin afectar al tráfico legítimo. Desde la Fase 4, además, en CHALLENGE no se filtra qué credenciales son válidas. Contra el ataque distribuido, el más realista, las reglas actuales siguen sin aportar nada. En total, las comprometidas pasan de 36 a 17 (−53 %).

## Limitaciones y trabajo futuro

### Limitaciones observadas

1. **El ataque distribuido casi no se detecta.** Tres de las cuatro reglas usan como clave la IP o la cuenta. Un atacante que rota IPs (botnets, proxies residenciales) y prueba una sola vez cada cuenta no dispara ninguna. La única señal agregada, la tasa global de fallos, suma +15 y nunca alcanza por sí sola el umbral de CHALLENGE (30).
2. **La fuerza bruta contra una cuenta no llega a BLOCK.** Desde una IP el máximo es 65: velocidad 30 + fallos por cuenta 20 + tasa global 15. El atacante puede seguir probando indefinidamente en CHALLENGE. No obtiene tokens, pero cada contraseña se verifica con Argon2 y el atacante conoce el resultado.
3. ~~**El 403 en CHALLENGE confirmaba al atacante que la credencial era válida.**~~ **Resuelto en la Fase 4.** Ahora toda respuesta en CHALLENGE es el mismo 403 con un `challengeId`. Solo el paso TOTP, que necesita el código de la víctima, emite tokens. Ver [`fase-4-2fa.md`](fase-4-2fa.md). Sí quedan dos efectos: en CHALLENGE el atacante tampoco ve un 401 que le permita descartar credenciales, y un usuario legítimo sin 2FA activo no puede entrar mientras su IP esté en esa zona.
4. **Hay una ventana inicial en ALLOW.** Los primeros intentos de una IP nueva siempre son ALLOW, porque los contadores solo cuentan intentos previos. En la corrida, el spraying comprometió ahí 2 cuentas. Un atacante paciente (*low and slow*, menos de 10 intentos por minuto y menos de 5 emails cada 10 minutos por IP) se mantiene siempre por debajo de los umbrales.
5. **Validez del experimento:**
   - una sola semilla y un solo orden de ataque (ver la advertencia del escenario b);
   - tasa fija de 5 req/s;
   - IPs simuladas mediante un header que solo existe en modo demo;
   - el backend corre en local con un único usuario atacante.

   Los números ilustran el comportamiento de las reglas y no son una estimación de efectividad en producción.

### Trabajo futuro

- **Reglas que no dependan de la IP:**
  - IPs distintas por cuenta;
  - fallos por cuenta con más peso o con un umbral alto que lleve directamente a BLOCK de esa cuenta (sin castigarla con intentos bloqueados, como ya se hace);
  - política global adaptativa: cuando la tasa global de fallos es alta, bajar los umbrales o exigir CHALLENGE a los logins sin historial.
- **Señales de las fases pendientes:** país o dispositivo nuevo (+15) y contraseña filtrada (+10, HIBP) añadirían puntos justo en el ataque distribuido. Hay que volver a ejecutar el simulador cuando existan.
- **Simulador:** varias semillas con media y desviación, el modo *low and slow*, concurrencia y un escenario con IPs reutilizadas entre cuentas.
