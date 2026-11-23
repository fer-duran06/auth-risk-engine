import type { RuleConfig, Zone, ZoneCounts } from "../api/admin";

/** Etiquetas en español de las zonas (las mismas del resto de la UI). */
export const ZONE_LABELS: Record<Zone, string> = { ALLOW: "Permitido", CHALLENGE: "Desafío", BLOCK: "Bloqueo" };

/** Nombres legibles de los ids de regla que guarda el backend. Un id desconocido se muestra tal cual. */
const RULE_LABELS: Record<string, string> = {
  IP_VELOCITY: "Velocidad por IP",
  DISTINCT_USERS_PER_IP: "Usuarios distintos por IP",
  ACCOUNT_FAILURES: "Fallos por cuenta",
  GLOBAL_FAILURE_RATE: "Tasa global de fallos",
  COUNTERS_UNAVAILABLE: "Contadores no disponibles (fail-safe)",
  RISK_DISABLED: "Motor desactivado",
  CHALLENGE_INVALID: "Desafío inválido o vencido",
};

export function ruleLabel(id: string): string {
  return RULE_LABELS[id] ?? id;
}

export interface ZoneShare {
  zone: Zone;
  count: number;
  /** Porcentaje entero; los tres suman 100 si hay datos (método del resto mayor). */
  percent: number;
}

/** Reparto por zona para la barra apilada. Sin intentos, todo es 0. */
export function zoneShares(counts: ZoneCounts): ZoneShare[] {
  const raw: [Zone, number][] = [
    ["ALLOW", counts.allow],
    ["CHALLENGE", counts.challenge],
    ["BLOCK", counts.block],
  ];
  const total = raw.reduce((acc, [, n]) => acc + n, 0);
  if (total === 0) return raw.map(([zone, count]) => ({ zone, count, percent: 0 }));

  const exact = raw.map(([, n]) => (n / total) * 100);
  const floors = exact.map(Math.floor);
  let missing = 100 - floors.reduce((a, b) => a + b, 0);
  const order = exact.map((v, i) => [v - floors[i], i] as const).sort((a, b) => b[0] - a[0]);
  for (const [, i] of order) {
    if (missing <= 0) break;
    floors[i]++;
    missing--;
  }
  return raw.map(([zone, count], i) => ({ zone, count, percent: floors[i] }));
}

/** Ancho relativo (0–100) de una barra respecto del máximo de la serie. */
export function barWidth(value: number, max: number): number {
  if (max <= 0 || value <= 0) return 0;
  return Math.max(2, Math.min(100, Math.round((value / max) * 100)));
}

/** Duración legible: 60 → "1 min", 900 → "15 min", 3600 → "1 h", 45 → "45 s". */
export function formatWindow(seconds: number): string {
  if (seconds % 3600 === 0 && seconds >= 3600) return `${seconds / 3600} h`;
  if (seconds % 60 === 0 && seconds >= 60) return `${seconds / 60} min`;
  return `${seconds} s`;
}

/** Condición de disparo de una regla en lenguaje natural. */
export function ruleCondition(rule: RuleConfig): string {
  const window = formatWindow(rule.windowSeconds);
  switch (rule.id) {
    case "IP_VELOCITY":
      return `≥ ${rule.threshold} intentos desde la IP en ${window}`;
    case "DISTINCT_USERS_PER_IP":
      return `≥ ${rule.threshold} emails distintos desde la IP en ${window}`;
    case "ACCOUNT_FAILURES":
      return `≥ ${rule.threshold} fallos contra la cuenta en ${window}`;
    case "GLOBAL_FAILURE_RATE":
      return `≥ ${Math.round(rule.threshold * 100)} % de fallos en todo el sistema en ${window}` +
        (rule.minAttempts ? `, con al menos ${rule.minAttempts} intentos` : "");
    default:
      return `Umbral ${rule.threshold} en ${window}`;
  }
}

/** Zona que correspondería a un score con los umbrales dados (para la escala de reglas). */
export function zoneForScore(score: number, challenge: number, block: number): Zone {
  if (score >= block) return "BLOCK";
  if (score >= challenge) return "CHALLENGE";
  return "ALLOW";
}

const dateTime = new Intl.DateTimeFormat("es", { dateStyle: "short", timeStyle: "medium" });

export function formatDateTime(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : dateTime.format(d);
}

const integer = new Intl.NumberFormat("es");

export function formatCount(n: number): string {
  return integer.format(n);
}
