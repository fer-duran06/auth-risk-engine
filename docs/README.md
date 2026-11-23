# Documentación

Un documento por fase con su alcance, decisiones de seguridad, limitaciones y pruebas. Cómo levantar el proyecto y el resumen general están en el [README raíz](../README.md).

| Fase | Documento | Contenido |
|---|---|---|
| 1 | [Autenticación básica](fase-1-auth.md) | Registro, login, JWT de acceso y refresh token rotativo con detección de reutilización. |
| 2 | [Motor de riesgo](fase-2-riesgo.md) | Reglas ponderadas sobre contadores en Redis, zonas ALLOW/CHALLENGE/BLOCK y fail-safe. |
| 3 | [Frontend](fase-3-frontend.md) | Login, registro y `/me` en Next.js, sesión en memoria y modo demo. |
| 4 | [2FA con TOTP](fase-4-2fa.md) | TOTP RFC 6238, secreto cifrado con AES-GCM y CHALLENGE sin oráculo de contraseña. |
| 5 | — | Contraseñas filtradas (HIBP): **no implementada**. |
| 6 | [Panel de administración](fase-6-admin.md) | Endpoints `/api/admin` (solo ADMIN), dashboard, eventos, IPs y reglas. |
| 7 | [Simulador de ataque](fase-7-simulador.md) | Credential stuffing contra el backend local, con y sin protección. |

Las fases se documentaron en el orden en que se hicieron. Cuando una fase posterior cambió un comportamiento, el documento anterior lo indica con un enlace.

Material de referencia:
- `design/`: capturas de Stitch usadas como referencia visual. Contienen cifras y textos de ejemplo que **no** se implementaron.
- `../simulator/results/ejemplo/`: reportes de la corrida de referencia del simulador.
