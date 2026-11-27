import type { HttpRequest } from "./http";

/** Notificación de la app (GET /api/notifications). */
export interface AppNotification {
  id: string;
  kind: string;
  message: string;
  createdAt: string;
  read: boolean;
}

export type NotificationsResult =
  | { kind: "ok"; items: AppNotification[] }
  | { kind: "unauthorized" }
  | { kind: "error" };

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function parseNotification(body: unknown): AppNotification | null {
  if (!isRecord(body)) return null;
  const { id, kind, message, createdAt, read } = body;
  if (typeof id !== "string" || typeof kind !== "string" || typeof message !== "string") return null;
  if (typeof createdAt !== "string" || typeof read !== "boolean") return null;
  return { id, kind, message, createdAt, read };
}

/** Endpoints de /api/notifications sobre la función HTTP base. */
export function createNotificationsApi(http: HttpRequest) {
  return {
    async list(token: string): Promise<NotificationsResult> {
      try {
        const res = await http("/api/notifications", { token });
        if (res.status === 401) return { kind: "unauthorized" };
        if (!res.ok) return { kind: "error" };
        const body = await res.json().catch(() => null);
        if (!Array.isArray(body)) return { kind: "error" };
        const items = body.map(parseNotification).filter((n): n is AppNotification => n !== null);
        return { kind: "ok", items };
      } catch {
        return { kind: "error" };
      }
    },

    /** Idempotente: no distingue "ya estaba leída" de "se acaba de marcar". */
    async markRead(token: string, id: string): Promise<boolean> {
      try {
        const res = await http(`/api/notifications/${encodeURIComponent(id)}/read`, { method: "POST", token });
        return res.ok || res.status === 204;
      } catch {
        return false;
      }
    },
  };
}

export type NotificationsApi = ReturnType<typeof createNotificationsApi>;
