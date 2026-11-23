import type { AccessToken } from "../api/auth";

/** Lo mínimo que la sesión necesita del cliente API (facilita las pruebas). */
export interface SessionApi {
  refresh(): Promise<AccessToken | null>;
  logout(): Promise<void>;
}

/** unknown: aún no se intentó restaurar; anonymous: sin sesión; authenticated: hay access token. */
export type SessionStatus = "unknown" | "authenticated" | "anonymous";

export type Unauthorized = { kind: "unauthorized" };

/**
 * Sesión del cliente. El access token vive solo en esta closure (memoria):
 * nunca en localStorage, sessionStorage ni cookies legibles por JS.
 * [onReset] se llama al iniciar y al cerrar sesión, para descartar estado efímero de otra
 * sesión (p. ej. un desafío 2FA pendiente).
 */
export function createSession(api: SessionApi, onReset: () => void = () => {}) {
  let token: string | null = null;
  let status: SessionStatus = "unknown";
  let refreshing: Promise<boolean> | null = null;
  const listeners = new Set<() => void>();

  function setState(nextToken: string | null, nextStatus: SessionStatus) {
    token = nextToken;
    if (status === nextStatus) return;
    status = nextStatus;
    listeners.forEach((l) => l());
  }

  /**
   * Pide un access token nuevo con la cookie de refresh. Las llamadas concurrentes comparten
   * la misma petición: enviar dos veces el mismo refresh token haría que el backend lo detecte
   * como reutilización y revoque toda la familia (pasa, p. ej., con el doble montaje de React).
   */
  function refresh(): Promise<boolean> {
    if (!refreshing) {
      refreshing = api
        .refresh()
        .then((result) => {
          if (result) setState(result.accessToken, "authenticated");
          else setState(null, "anonymous");
          return result !== null;
        })
        .finally(() => {
          refreshing = null;
        });
    }
    return refreshing;
  }

  return {
    getStatus: (): SessionStatus => status,
    getToken: (): string | null => token,

    subscribe(listener: () => void): () => void {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },

    /** Guarda el access token recibido en el login. */
    start(accessToken: AccessToken): void {
      onReset();
      setState(accessToken.accessToken, "authenticated");
    },

    /** Al cargar la app: si no hay token en memoria, intenta un refresh. */
    async restore(): Promise<boolean> {
      if (token) return true;
      return refresh();
    },

    /**
     * Ejecuta una llamada autenticada. Si responde "unauthorized", refresca una sola vez y reintenta;
     * si vuelve a fallar, la sesión queda anónima (la UI redirige a /login).
     */
    async withAuth<R extends { kind: string }>(call: (accessToken: string) => Promise<R>): Promise<R | Unauthorized> {
      if (!token && !(await refresh())) return { kind: "unauthorized" };

      const first = await call(token!);
      if (first.kind !== "unauthorized") return first;

      if (!(await refresh())) return { kind: "unauthorized" };
      const second = await call(token!);
      if (second.kind === "unauthorized") setState(null, "anonymous");
      return second;
    },

    /** Revoca el refresh en el backend y borra el token de memoria. */
    async logout(): Promise<void> {
      try {
        await api.logout();
      } finally {
        onReset();
        setState(null, "anonymous");
      }
    },
  };
}

export type Session = ReturnType<typeof createSession>;
