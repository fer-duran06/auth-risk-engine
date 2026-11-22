# Documentación

Contenido previsto para esta carpeta (se irá completando en fases posteriores):

- **Propuesta** del proyecto: problema (credential stuffing), objetivos y alcance.
- **Diagramas**: arquitectura hexagonal, flujo de login con evaluación de riesgo, modelo de datos.
- **Decisiones** (ADR breves): Argon2id, JWT + refresh rotativo, TOTP, HIBP con k-anonymity, comportamiento ante fallos de Redis.
- **Reglas de riesgo**: descripción, pesos por defecto y umbrales (ALLOW < 30, CHALLENGE 30-69, BLOCK >= 70).
- **Simulador**: cómo ejecutar el ataque simulado contra localhost y cómo interpretar los resultados.

## Fases

- [Fase 1 — Autenticación básica](fase-1-auth.md)
- [Fase 2 — Motor de riesgo](fase-2-riesgo.md)
- [Fase 3 — Frontend](fase-3-frontend.md)
