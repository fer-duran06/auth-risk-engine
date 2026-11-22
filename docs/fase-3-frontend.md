# Fase 3 — Frontend

Login, registro y área protegida `/me` en Next.js 16 (App Router), TypeScript y Tailwind 4. Todo funciona contra la API real, sin datos simulados.
Quedan para fases posteriores: 2FA (verificación y activación), la seguridad de la cuenta y todas las vistas de administración.

## Configuración

| Variable | Por defecto | Descripción |
|---|---|---|
| `NEXT_PUBLIC_API_URL` | `http://localhost:8080` | URL base del backend. |
| `NEXT_PUBLIC_DEMO_MODE` | `false` | Solo el valor `true` muestra el selector de IP simulada. |

Next incrusta estas variables al compilar, así que cambiarlas exige reiniciar `npm run dev` o volver a ejecutar `npm run build`. Son públicas: nunca deben contener secretos.

## Rutas

| Ruta | Acceso | Contenido |
|---|---|---|
| `/` | — | Redirige a `/me`. |
| `/login` | pública | Formulario de inicio de sesión. Muestra un estado especial ante un desafío. |
| `/register` | pública | Registro con validación en cliente y mensaje neutro. |
| `/me` | sesión | Datos de `GET /api/auth/me` y botón para cerrar sesión. |

Respuestas del login:

| Respuesta | Qué ve el usuario |
|---|---|
| `200` | Se guarda el token y se redirige a `/me`. |
| `401` / `400` | "Credenciales inválidas". |
| `403` con `code: CHALLENGE_REQUIRED` | "Verificación adicional requerida". El 2FA aparece como pendiente. |
| `429` | "Demasiados intentos. Intenta más tarde." |
| `5xx`, red caída u otro código | Mensaje genérico de error. |

## Estructura

```
src/lib/api/        config, http (fetch base), auth (endpoints con resultados tipados), simulated-ip
src/lib/session/    session.ts (lógica pura) + useSessionStatus (React)
src/components/ui/      Button, TextField, PasswordField, Alert, Card, Badge, Logo, Icon
src/components/layout/  AuthLayout (pantallas públicas y vistas 2FA futuras), AppLayout (área de usuario)
src/components/auth/    RequireSession (guard), DemoIpSelector
src/app/(auth)/     login, register
src/app/(app)/      me (el layout del grupo exige sesión)
```

## Sesión

- **Access token solo en memoria**, dentro de una closure del módulo `session`. Nunca se guarda en `localStorage`, `sessionStorage` ni en cookies legibles por JS. Al recargar la página se pierde y se recupera con la cookie de refresh.
- **Refresh:** `RequireSession` ejecuta `POST /api/auth/refresh` al cargar una ruta protegida. Si falla, redirige a `/login`. `withAuth` reintenta una sola vez cuando recibe un 401: hace un refresh y repite la llamada. Si vuelve a fallar, la sesión queda anónima.
- **Refresh deduplicado:** las llamadas simultáneas comparten una misma petición. Si el mismo refresh token se enviara dos veces (pasa con el doble montaje de React en desarrollo), el backend lo trataría como reutilización y revocaría toda la familia.
- **Guard en el cliente:** la cookie de refresh pertenece al dominio del backend (`Path=/api/auth`), así que un `proxy` de Next no puede verla. Las rutas protegidas solo muestran un indicador hasta confirmar la sesión. La autorización real la hace siempre el backend.
- **Fetch:** todas las peticiones usan `credentials: "include"` y `cache: "no-store"`. `localhost:3000` y `localhost:8080` son el mismo *site*, así que la cookie `SameSite=Strict` viaja sin problema.

## Modo demo

Con `NEXT_PUBLIC_DEMO_MODE=true`, el login y el registro muestran un selector de IP. Ofrece IPs de documentación (RFC 5737) o una IPv4/IPv6 escrita a mano. La IP elegida viaja en `X-Simulated-IP` y solo se guarda en memoria. Si la variable no vale `true`, el selector no se renderiza y el header nunca se envía.

El backend solo acepta el header con `app.demo-mode=true`, tanto en `ClientIpResolver` como en CORS (`SecurityConfig`). Fuera del modo demo, el preflight lo rechaza.

## Decisiones de UI y seguridad

- El score y las reglas disparadas nunca se muestran; el backend tampoco los envía. El bloque "SCORE" de la captura de login no se implementó.
- Los mensajes son genéricos y no revelan si el email existe. El registro muestra siempre el mismo texto tras el `202`.
- Tras un intento fallido, bloqueado o desafiado se borra la contraseña del formulario.
- De las capturas de Stitch se omitieron los elementos prohibidos o inventados: "ISO/IEC 27001", "encriptación en reposo", TLS, IDs de nodo, estadísticas de filtraciones, el campo "Nombre completo" (la API no lo recibe) y "¿Olvidaste tu contraseña?" (fuera de alcance).
- La comprobación con HIBP llega en su propia fase. Irá en el panel `PasswordPolicy` del registro y deshabilitará el botón si la contraseña está filtrada.
- **Accesibilidad:**
  - Todos los campos tienen `label` visible.
  - Los errores de cada campo se enlazan con `aria-describedby` y marcan `aria-invalid`.
  - Las alertas usan `role="alert"` o `role="status"` según su urgencia.
  - El foco es visible en toda la app, va al primer campo inválido y a los títulos de los estados de desafío y de registro enviado.
  - Se respeta `prefers-reduced-motion`.

## Pruebas

```
cd frontend
npm test        # Vitest: cliente API y sesión (fetch simulado, sin DOM)
npm run lint
npm run build
```

- `auth.test.ts`:
  - configuración y URL base;
  - `credentials: "include"` y `Authorization: Bearer`;
  - `X-Simulated-IP` según el modo demo;
  - el mapeo de 200, 400, 401, 403 (con y sin código), 429, 5xx y red caída;
  - registro, refresh, `me`, `logout` y la validación de IP.
- `session.test.ts`:
  - `restore`;
  - refresh deduplicado ante llamadas concurrentes;
  - un único reintento tras un 401, sin bucles;
  - comportamiento cuando falla el refresh;
  - `logout`, incluido el caso de error del backend.
- Backend: `CorsConfigTest` comprueba que el preflight acepta `X-Simulated-IP` solo en modo demo.

Vitest 5 necesita Node ≥ 22.12.
