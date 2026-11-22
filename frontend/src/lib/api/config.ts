/**
 * Configuración pública del frontend. Next.js incrusta las variables NEXT_PUBLIC_* al compilar,
 * por eso se leen con acceso directo (no dinámico) a process.env.
 */

const DEFAULT_API_URL = "http://localhost:8080";

/** Normaliza la URL base: sin espacios ni barras finales. */
export function normalizeBaseUrl(raw: string | undefined): string {
  const value = raw?.trim();
  return (value ? value : DEFAULT_API_URL).replace(/\/+$/, "");
}

/** El modo demo solo se activa con el valor exacto "true". */
export function parseDemoMode(raw: string | undefined): boolean {
  return raw?.trim() === "true";
}

export const API_URL = normalizeBaseUrl(process.env.NEXT_PUBLIC_API_URL);
export const DEMO_MODE = parseDemoMode(process.env.NEXT_PUBLIC_DEMO_MODE);

/** Límites alineados con el backend (PasswordPolicy y Email). */
export const PASSWORD_MIN_LENGTH = 12;
export const PASSWORD_MAX_LENGTH = 128;
export const EMAIL_MAX_LENGTH = 254;
