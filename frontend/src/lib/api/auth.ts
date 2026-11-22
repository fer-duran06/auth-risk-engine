import type { HttpRequest } from "./http";

/** Respuesta de login y refresh. */
export interface AccessToken {
  accessToken: string;
  expiresIn: number;
}

/** Datos de GET /api/auth/me. */
export interface CurrentUser {
  id: string;
  email: string;
  role: string;
  createdAt: string;
}

/**
 * Resultados tipados: las páginas no interpretan códigos HTTP.
 * Nunca se exponen score ni reglas (el backend tampoco los envía).
 */
export type LoginResult =
  | { kind: "ok"; token: AccessToken }
  | { kind: "invalid" }
  | { kind: "challenge" }
  | { kind: "rate-limited" }
  | { kind: "error" };

export type RegisterResult = { kind: "accepted" } | { kind: "invalid" } | { kind: "rate-limited" } | { kind: "error" };

export type MeResult = { kind: "ok"; user: CurrentUser } | { kind: "unauthorized" } | { kind: "error" };

export const CHALLENGE_REQUIRED = "CHALLENGE_REQUIRED";

async function readJson(res: Response): Promise<unknown> {
  try {
    return await res.json();
  } catch {
    return null;
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function parseToken(body: unknown): AccessToken | null {
  if (!isRecord(body) || typeof body.accessToken !== "string" || body.accessToken === "") return null;
  return { accessToken: body.accessToken, expiresIn: typeof body.expiresIn === "number" ? body.expiresIn : 0 };
}

function parseUser(body: unknown): CurrentUser | null {
  if (!isRecord(body)) return null;
  const { id, email, role, createdAt } = body;
  if ([id, email, role, createdAt].some((v) => typeof v !== "string")) return null;
  return { id, email, role, createdAt } as CurrentUser;
}

/** Endpoints de /api/auth sobre la función HTTP base. */
export function createAuthApi(http: HttpRequest) {
  return {
    async login(email: string, password: string): Promise<LoginResult> {
      try {
        const res = await http("/api/auth/login", { method: "POST", json: { email, password } });
        if (res.ok) {
          const token = parseToken(await readJson(res));
          return token ? { kind: "ok", token } : { kind: "error" };
        }
        // 400 (datos mal formados) se trata igual que 401: mensaje genérico.
        if (res.status === 401 || res.status === 400) return { kind: "invalid" };
        if (res.status === 429) return { kind: "rate-limited" };
        if (res.status === 403) {
          const body = await readJson(res);
          if (isRecord(body) && body.code === CHALLENGE_REQUIRED) return { kind: "challenge" };
        }
        return { kind: "error" };
      } catch {
        return { kind: "error" };
      }
    },

    async register(email: string, password: string): Promise<RegisterResult> {
      try {
        const res = await http("/api/auth/register", { method: "POST", json: { email, password } });
        if (res.status === 202 || res.ok) return { kind: "accepted" };
        if (res.status === 400) return { kind: "invalid" };
        if (res.status === 429) return { kind: "rate-limited" };
        return { kind: "error" };
      } catch {
        return { kind: "error" };
      }
    },

    /** Rota la cookie de refresh. Devuelve null si no hay sesión válida o falla la red. */
    async refresh(): Promise<AccessToken | null> {
      try {
        const res = await http("/api/auth/refresh", { method: "POST" });
        return res.ok ? parseToken(await readJson(res)) : null;
      } catch {
        return null;
      }
    },

    /** Revoca la familia del refresh. Idempotente; los errores se ignoran. */
    async logout(): Promise<void> {
      try {
        await http("/api/auth/logout", { method: "POST" });
      } catch {
        // Sin red: la sesión local se borra igual.
      }
    },

    async me(token: string): Promise<MeResult> {
      try {
        const res = await http("/api/auth/me", { token });
        if (res.status === 401) return { kind: "unauthorized" };
        if (!res.ok) return { kind: "error" };
        const user = parseUser(await readJson(res));
        return user ? { kind: "ok", user } : { kind: "error" };
      } catch {
        return { kind: "error" };
      }
    },
  };
}

export type AuthApi = ReturnType<typeof createAuthApi>;
