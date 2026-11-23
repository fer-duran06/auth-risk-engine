# Fase 6 — Panel de administración (mínimo)

Panel de solo lectura con datos reales de `login_attempts` y la configuración efectiva del motor de riesgo.
Quedan fuera:
- editar reglas o umbrales;
- bloquear o desbloquear IPs a mano;
- exportar;
- la vista de resultados del simulador;
- las reglas de país/dispositivo nuevo y de contraseña filtrada (aún no existen).

## Crear un administrador

No se siembra ningún admin con contraseña. Se registra un usuario normal y se lo promueve por SQL en el contenedor de PostgreSQL. El usuario y la base (`authshield`) son los de `.env.example`. Este comando funciona igual en PowerShell y en bash:

```powershell
"UPDATE users SET role='ADMIN' WHERE email='tu@correo';" | docker exec -i authshield-postgres psql -U authshield -d authshield
```

- `psql` debe responder `UPDATE 1`. Si responde `UPDATE 0`, el email no está registrado. El email se guarda en minúsculas.
- El rol viaja en el JWT: hay que **cerrar sesión y volver a iniciarla**. Un access token emitido antes conserva el rol anterior hasta que se renueva (como máximo 15 minutos).
- Para revertirlo, ejecuta el mismo comando con `role='USER'`.

## Endpoints

Todos son `GET` y exigen `Bearer` con rol ADMIN:

- **anónimo:** `401`;
- **USER:** `403`;
- **otros métodos bajo `/api/admin`:** denegados;
- **parámetros fuera de rango o con tipo inválido:** `400 {"error":"Solicitud inválida"}`, sin repetir el valor recibido.

| Ruta | Parámetros | Respuesta |
|---|---|---|
| `/api/admin/stats` | — | Últimas 24 h. Ver el detalle abajo. |
| `/api/admin/events` | `zone` (ALLOW/CHALLENGE/BLOCK), `ip` (exacta, ≤ 45), `page` (0–10 000), `size` (1–100, por defecto 25) | `{items, page, size, total, totalPages}`, más recientes primero. Cada item: `id, createdAt, email, ip, stage, zone, score, triggeredRules, success`. |
| `/api/admin/ips` | `limit` (1–100, por defecto 50) | IPs con intentos de login en 24 h, ordenadas por el último intento. Cada una: `attempts, failures, lastSeen, lastZone, lastScore`. |
| `/api/admin/risk-config` | — | `enabled`, `challengeThreshold`, `blockThreshold`, `maxScore` y, por regla, `id, weight, threshold, minAttempts, windowSeconds`. |

**`/api/admin/stats`:**
- `loginAttempts` y `byZone` cuentan solo los intentos de login (stage `PASSWORD`).
- `loginWithoutTokens` son los intentos de login sin tokens.
- `sessionsIssued` cuenta los logins en ALLOW más los 2FA exitosos.
- `totpSuccesses` y `totpFailures` son las verificaciones 2FA (stage `TOTP`).
- `topIps` son las 10 IPs con más intentos de login.

## Decisiones

- **Autorización:** el claim `role` del JWT se traduce a `ROLE_<rol>`, y `SecurityConfig` exige `hasRole("ADMIN")` en `GET /api/admin/**`. Todo lo no declarado se sigue denegando.
- **Score y reglas** solo salen por `/api/admin`. Los endpoints de usuario no cambian (hay una prueba que lo verifica).
- **Métricas separadas por stage:** un login desafiado que se completa con 2FA genera dos filas (`PASSWORD` y `TOTP`). Contarlas juntas duplicaría intentos.
- **Consultas:**
  - `NamedParameterJdbcTemplate` con parámetros. Los fragmentos `WHERE` son constantes y los filtros se validan antes en el caso de uso.
  - Una consulta agregada por endpoint (`count(*) FILTER`, `array_agg … ORDER BY` para la zona más reciente), más el conteo de la paginación. No hay N+1.
  - Se usan los índices existentes de `created_at` e `ip`; no se agregaron índices.
- **Paginación por offset** con total, como máximo 100 por página y 10 000 páginas. Es simple y permite mostrar "página X de Y". Con tablas muy grandes, el `OFFSET` profundo y el `count(*)` se vuelven costosos: en ese caso convendría keyset.
- **"Zona más reciente"** es la del último intento de esa IP, no un bloqueo vigente. No hay listas de bloqueo: cada intento se evalúa con los contadores de su ventana.
- **Configuración:** se lee de `RiskProperties` al arrancar (`app.risk`, `application.yml`). La vista es de solo lectura y los ids coinciden con los de `triggered_rules`.

### Frontend

- **`AdminLayout`:** barra lateral (horizontal en móvil) con Dashboard, Eventos de login, IPs recientes y Reglas y umbrales. Su guardia consulta `/api/auth/me`: sin rol ADMIN muestra "Acceso denegado". Es solo una guía para la UI; la protección real es el `403` del backend. En `/me` aparece un enlace al panel solo para administradores.
- **Páginas:**
  - **`/admin`:** KPIs, barra apilada por zona y top de IPs con barras CSS.
  - **`/admin/events`:** tabla con filtros y paginación.
  - **`/admin/ips`** y **`/admin/rules`:** solo lectura. La escala 0–30–70–100 de reglas está dibujada con CSS.

  No se usan librerías de gráficas ni dependencias nuevas.
- **Filtros de eventos en la URL** (`/admin/events?zone=BLOCK&ip=…`). Con `cacheComponents`, Next conserva las páginas ocultas con `<Activity>`. Si los filtros vivieran en el estado del componente, quedarían desactualizados al volver desde un enlace (el mismo problema que el bug de la verificación 2FA). El formulario se monta con `key` igual a la query.
- **De las capturas de Stitch se omitió:**
  - cifras, países, mapas y gráficas de serie temporal (no hay datos geográficos ni de series);
  - "Exportar CSV/SIEM", "Añadir a lista negra", "Desbloquear", "Revocar tokens" y "alertas críticas";
  - ajuste de pesos con sliders e interruptores;
  - "SOC2/ISO" y textos de empresa ficticia.

## Limitaciones

- Solo lectura: cambiar pesos o umbrales exige editar `application.yml` y reiniciar.
- `email` de los eventos es el intentado y puede no corresponder a una cuenta. En verificaciones con `challengeId` desconocido queda vacío.
- No hay auditoría de quién consultó el panel.
- No hay caché: cada carga consulta la BD. Basta para el volumen académico.

## Pruebas

```
cd backend && ./gradlew test
cd frontend && npm test && npm run lint && npm run build
```

- **`AdminUseCasesTest`:** validación de parámetros, ventana de 24 h y total de páginas.
- **`AdminIntegrationTest`** (Testcontainers):
  - autorización: anónimo 401, USER 403 y ADMIN 200 en los 4 endpoints, y `POST` denegado;
  - `stats` separado por stage;
  - `events` con orden, filtros y paginación;
  - `ips` con la zona del último intento y la ventana de 24 h;
  - `risk-config`;
  - parámetros inválidos;
  - endpoints de usuario sin score.

  El admin se crea igual que en la vida real: registro, `UPDATE` y login.
- **Vitest (`lib/admin/admin.test.ts`):**
  - cliente (`401`, `403`, `400`, errores y validación de respuestas);
  - query y filtros desde la URL;
  - reparto por zona que suma 100;
  - anchos de barra, ventanas y condiciones de reglas;
  - decisión de acceso por rol.
