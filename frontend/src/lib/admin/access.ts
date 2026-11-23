import type { MeResult } from "../api/auth";

export type AdminAccess = "admin" | "denied" | "unauthorized" | "error";

/**
 * Decide qué muestra el panel según /api/auth/me. Es solo una guía para la UI:
 * la autorización real la hace el backend en cada endpoint de /api/admin (403 sin ROLE_ADMIN).
 */
export function adminAccess(result: MeResult | { kind: "unauthorized" }): AdminAccess {
  if (result.kind === "unauthorized") return "unauthorized";
  if (result.kind !== "ok") return "error";
  return result.user.role === "ADMIN" ? "admin" : "denied";
}
