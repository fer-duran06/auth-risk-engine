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

/** Datos para registrar la cuenta en la app autenticadora (POST /api/auth/2fa/setup). */
export interface TotpSetup {
  secret: string;
  otpauthUri: string;
}

/** Estado del 2FA (GET /api/auth/2fa/status). */
export interface TotpStatus {
  enabled: boolean;
  confirmedAt: string | null;
}

/** Brechas conocidas del correo (GET /api/auth/breach-status). null: el tercero no respondió. */
export interface BreachStatus {
  breachCount: number | null;
}

/**
 * Resultados tipados: las páginas no interpretan códigos HTTP.
 * Nunca se exponen score ni reglas (el backend tampoco los envía).
 * En "challenge" no se sabe (ni el backend lo dice) si la contraseña era correcta.
 */
export type LoginResult =
  | { kind: "ok"; token: AccessToken }
  | { kind: "invalid" }
  | { kind: "challenge"; challengeId: string }
  | { kind: "rate-limited" }
  | { kind: "error" };

/** "invalid" agrupa código incorrecto, desafío vencido o agotado y contraseña incorrecta: el backend no distingue. */
export type VerifyResult = { kind: "ok"; token: AccessToken } | { kind: "invalid" } | { kind: "error" };

export type TotpSetupResult =
  | { kind: "ok"; setup: TotpSetup }
  | { kind: "already-enabled" }
  | { kind: "unauthorized" }
  | { kind: "error" };

export type TotpEnableResult =
  | { kind: "ok" }
  | { kind: "invalid" }
  | { kind: "already-enabled" }
  | { kind: "unauthorized" }
  | { kind: "error" };

export type TotpStatusResult = { kind: "ok"; status: TotpStatus } | { kind: "unauthorized" } | { kind: "error" };

export type BreachStatusResult = { kind: "ok"; status: BreachStatus } | { kind: "unauthorized" } | { kind: "error" };

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

/** Longitud máxima aceptada por el backend para el challengeId (32 bytes en base64url son 43). */
const CHALLENGE_ID_MAX_LENGTH = 128;

function parseChallengeId(body: unknown): string | null {
  if (!isRecord(body) || body.code !== CHALLENGE_REQUIRED) return null;
  const id = body.challengeId;
  return typeof id === "string" && id !== "" && id.length <= CHALLENGE_ID_MAX_LENGTH ? id : null;
}

function parseSetup(body: unknown): TotpSetup | null {
  if (!isRecord(body)) return null;
  const { secret, otpauthUri } = body;
  if (typeof secret !== "string" || typeof otpauthUri !== "string") return null;
  if (!/^[A-Z2-7]+$/.test(secret) || !otpauthUri.startsWith("otpauth://totp/")) return null;
  return { secret, otpauthUri };
}

function parseStatus(body: unknown): TotpStatus | null {
  if (!isRecord(body) || typeof body.enabled !== "boolean") return null;
  return { enabled: body.enabled, confirmedAt: typeof body.confirmedAt === "string" ? body.confirmedAt : null };
}

function parseBreachStatus(body: unknown): BreachStatus | null {
  if (!isRecord(body)) return null;
  const { breachCount } = body;
  if (breachCount !== null && typeof breachCount !== "number") return null;
  return { breachCount };
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
          // Sin challengeId válido no hay forma de continuar: se trata como error.
          const challengeId = parseChallengeId(await readJson(res));
          if (challengeId) return { kind: "challenge", challengeId };
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

    /** Segundo paso del login en zona CHALLENGE. */
    async verifyChallenge(challengeId: string, code: string): Promise<VerifyResult> {
      try {
        const res = await http("/api/auth/2fa/verify", { method: "POST", json: { challengeId, code } });
        if (res.ok) {
          const token = parseToken(await readJson(res));
          return token ? { kind: "ok", token } : { kind: "error" };
        }
        if (res.status === 401 || res.status === 400) return { kind: "invalid" };
        return { kind: "error" };
      } catch {
        return { kind: "error" };
      }
    },

    async totpStatus(token: string): Promise<TotpStatusResult> {
      try {
        const res = await http("/api/auth/2fa/status", { token });
        if (res.status === 401) return { kind: "unauthorized" };
        if (!res.ok) return { kind: "error" };
        const status = parseStatus(await readJson(res));
        return status ? { kind: "ok", status } : { kind: "error" };
      } catch {
        return { kind: "error" };
      }
    },

    async totpSetup(token: string): Promise<TotpSetupResult> {
      try {
        const res = await http("/api/auth/2fa/setup", { method: "POST", token });
        if (res.status === 401) return { kind: "unauthorized" };
        if (res.status === 409) return { kind: "already-enabled" };
        if (!res.ok) return { kind: "error" };
        const setup = parseSetup(await readJson(res));
        return setup ? { kind: "ok", setup } : { kind: "error" };
      } catch {
        return { kind: "error" };
      }
    },

    async totpEnable(token: string, code: string): Promise<TotpEnableResult> {
      try {
        const res = await http("/api/auth/2fa/enable", { method: "POST", token, json: { code } });
        if (res.status === 204 || res.ok) return { kind: "ok" };
        if (res.status === 401) return { kind: "unauthorized" };
        if (res.status === 400) return { kind: "invalid" };
        if (res.status === 409) return { kind: "already-enabled" };
        return { kind: "error" };
      } catch {
        return { kind: "error" };
      }
    },

    /** Consulta en vivo (no se guarda nada): brechas conocidas del correo del usuario autenticado. */
    async breachStatus(token: string): Promise<BreachStatusResult> {
      try {
        const res = await http("/api/auth/breach-status", { token });
        if (res.status === 401) return { kind: "unauthorized" };
        if (!res.ok) return { kind: "error" };
        const status = parseBreachStatus(await readJson(res));
        return status ? { kind: "ok", status } : { kind: "error" };
      } catch {
        return { kind: "error" };
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
