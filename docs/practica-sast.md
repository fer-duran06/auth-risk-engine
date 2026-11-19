# Práctica SAST — vulnerabilidades intencionales

Rama: `practica/sast-vulnerabilidades` (no se integra en `main`). Código de laboratorio para que Semgrep y CodeQL
detecten 5 vulnerabilidades. La práctica siguiente consiste en corregirlas.

**Aislamiento:**
- Backend: paquete `com.authrisk.lab`. Todos los componentes llevan `@Profile("lab")`, así que con el perfil `dev`
  no se cargan. Aunque se activara `lab`, `SecurityConfig` sigue denegando `/lab/**` (403).
- Frontend: `src/app/lab/xss/page.tsx` responde 404 salvo que la variable de servidor `LAB_MODE=true`.

| # | Vulnerabilidad | CWE | Archivo:línea | Severidad | Corrección (descripción) |
|---|----------------|-----|---------------|-----------|--------------------------|
| 1 | Inyección SQL por concatenación de `@RequestParam` | CWE-89 | `backend/src/main/kotlin/com/authrisk/lab/LabUserController.kt:19-20` | Alta | Usar consulta parametrizada (`WHERE email = ?` pasando `email` como argumento de `queryForList`). |
| 2 | Secreto JWT hardcodeado | CWE-798 | `backend/src/main/kotlin/com/authrisk/lab/LabTokenSigner.kt:16` (uso en `:22`) | Alta | Leer la clave de una variable de entorno/gestor de secretos y rotar la clave expuesta. |
| 3 | Hash débil MD5 para contraseñas | CWE-327 | `backend/src/main/kotlin/com/authrisk/lab/LabPasswordHasher.kt:14` | Media | Reemplazar MD5 por Argon2id con salt (p. ej. `Argon2PasswordEncoder` de Spring Security). |
| 4 | Email y contraseña en logs | CWE-532 | `backend/src/main/kotlin/com/authrisk/lab/LabLoginController.kt:26` | Media | No registrar la contraseña nunca y enmascarar o omitir el email en el log. |
| 5 | XSS reflejado con `dangerouslySetInnerHTML` | CWE-79 | `frontend/src/app/lab/xss/page.tsx:21` | Media | Renderizar el valor como texto con JSX (`<p>Hola, {valor}</p>`) en lugar de `dangerouslySetInnerHTML`. |

Cada punto está marcado en el código con `// VULNERABILIDAD INTENCIONAL (práctica SAST #N)`.
