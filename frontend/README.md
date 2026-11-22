# AuthShield — Frontend

Next.js 16 (App Router) + TypeScript + Tailwind CSS 4. Interfaz en español con estilo oscuro.
Detalle de diseño y decisiones: [`docs/fase-3-frontend.md`](../docs/fase-3-frontend.md).

## Requisitos

- Node.js ≥ 22.12 (Vitest 5 lo exige; Next 16 funciona desde 20.9).
- Backend en marcha (`cd backend && ./gradlew bootRun`) con `CORS_ALLOWED_ORIGINS` que incluya `http://localhost:3000`.

## Uso

```bash
cp .env.example .env.local   # ajusta NEXT_PUBLIC_API_URL si hace falta
npm install
npm run dev                  # http://localhost:3000
```

| Script | Qué hace |
|---|---|
| `npm run dev` | Servidor de desarrollo. |
| `npm run build` / `npm start` | Build de producción y servidor. |
| `npm run lint` | ESLint (config de Next). |
| `npm test` | Pruebas unitarias con Vitest. |

## Variables (`.env.local`)

| Variable | Por defecto | Descripción |
|---|---|---|
| `NEXT_PUBLIC_API_URL` | `http://localhost:8080` | URL base de la API. |
| `NEXT_PUBLIC_DEMO_MODE` | `false` | `true` muestra el selector de IP simulada (`X-Simulated-IP`). El backend también debe tener `app.demo-mode=true`. |

Se incrustan al compilar: reinicia `npm run dev` o vuelve a ejecutar `npm run build` tras cambiarlas. Nunca pongas secretos en ellas.

## Estructura

- `src/lib/api`: cliente HTTP (`credentials: "include"`) y endpoints de `/api/auth` con resultados tipados.
- `src/lib/session`: access token en memoria, refresh deduplicado y un único reintento ante 401.
- `src/components/ui`, `src/components/layout`: componentes y layouts reutilizables por las fases siguientes.
- `src/app/(auth)`: `/login`, `/register`. `src/app/(app)`: `/me`, protegido por sesión.
