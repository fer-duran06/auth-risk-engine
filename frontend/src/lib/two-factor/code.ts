/** Dígitos del código TOTP (RFC 6238, configuración del backend). */
export const TOTP_DIGITS = 6;

/**
 * Normaliza lo que escribe o pega el usuario: solo dígitos ASCII, como máximo 6.
 * Acepta "123 456" o "123-456" (formatos comunes de las apps autenticadoras).
 */
export function normalizeCode(input: string): string {
  return input.replace(/[^0-9]/g, "").slice(0, TOTP_DIGITS);
}

export function isCompleteCode(code: string): boolean {
  return new RegExp(`^[0-9]{${TOTP_DIGITS}}$`).test(code);
}

/** Agrupa la clave Base32 de 4 en 4 para copiarla a mano con menos errores. */
export function formatSecret(secret: string): string {
  return secret.replace(/(.{4})(?=.)/g, "$1 ");
}
