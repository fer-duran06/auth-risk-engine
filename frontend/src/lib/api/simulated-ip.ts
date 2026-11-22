/**
 * IP simulada del modo demo. Vive solo en memoria: se pierde al recargar y nunca se persiste.
 * El backend vuelve a validarla y la ignora si no está en modo demo.
 */

export const SIMULATED_IP_HEADER = "X-Simulated-IP";

/** IPs de documentación (RFC 5737), seguras para demos. */
export const DEMO_IPS = ["203.0.113.10", "203.0.113.66", "198.51.100.23", "192.0.2.45"] as const;

const IPV4 = /^((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\.){3}(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)$/;
// Validación de forma de un literal IPv6; el backend hace la validación definitiva.
const IPV6 = /^(?=.*:)[0-9a-fA-F:.]{2,45}$/;

/** True si el valor parece un literal IPv4 o IPv6 (sin nombres de host). */
export function isIpLiteral(value: string): boolean {
  const v = value.trim();
  return IPV4.test(v) || IPV6.test(v);
}

let current: string | null = null;
const listeners = new Set<() => void>();

export const simulatedIp = {
  get(): string | null {
    return current;
  },
  /** Fija la IP simulada; null o un valor no válido la desactivan. */
  set(value: string | null): void {
    const next = value && isIpLiteral(value) ? value.trim() : null;
    if (next === current) return;
    current = next;
    listeners.forEach((l) => l());
  },
  subscribe(listener: () => void): () => void {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
};
