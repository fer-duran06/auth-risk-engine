import type { HttpRequest } from "./http";

/** Zonas del motor de riesgo, tal como las devuelve el backend. */
export const ZONES = ["ALLOW", "CHALLENGE", "BLOCK"] as const;
export type Zone = (typeof ZONES)[number];
export type Stage = "PASSWORD" | "TOTP";

export interface ZoneCounts {
  allow: number;
  challenge: number;
  block: number;
}

export interface AdminStats {
  since: string;
  until: string;
  loginAttempts: number;
  byZone: ZoneCounts;
  loginWithoutTokens: number;
  sessionsIssued: number;
  totpSuccesses: number;
  totpFailures: number;
  topIps: { ip: string; attempts: number }[];
}

export interface AdminEvent {
  id: string;
  createdAt: string;
  email: string;
  ip: string;
  stage: Stage;
  zone: Zone;
  score: number | null;
  triggeredRules: string[];
  success: boolean;
}

export interface EventPage {
  items: AdminEvent[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
}

export interface IpActivity {
  ip: string;
  attempts: number;
  failures: number;
  lastSeen: string;
  lastZone: Zone;
  lastScore: number | null;
}

export interface RuleConfig {
  id: string;
  weight: number;
  threshold: number;
  minAttempts: number | null;
  windowSeconds: number;
}

export interface RiskConfig {
  enabled: boolean;
  challengeThreshold: number;
  blockThreshold: number;
  maxScore: number;
  rules: RuleConfig[];
}

export interface EventFilter {
  zone?: Zone | null;
  ip?: string | null;
  page?: number;
  size?: number;
}

/** "forbidden": sesión válida sin rol ADMIN. "invalid": parámetros rechazados (400). */
export type AdminResult<T> =
  | { kind: "ok"; data: T }
  | { kind: "unauthorized" }
  | { kind: "forbidden" }
  | { kind: "invalid" }
  | { kind: "error" };

const isRecord = (v: unknown): v is Record<string, unknown> => typeof v === "object" && v !== null;
const isNum = (v: unknown): v is number => typeof v === "number" && Number.isFinite(v);
const isStr = (v: unknown): v is string => typeof v === "string";
const isNumOrNull = (v: unknown): v is number | null => v === null || isNum(v);
const isZone = (v: unknown): v is Zone => ZONES.includes(v as Zone);

export function parseStats(b: unknown): AdminStats | null {
  if (!isRecord(b) || !isRecord(b.byZone) || !Array.isArray(b.topIps)) return null;
  const z = b.byZone;
  const nums = [b.loginAttempts, b.loginWithoutTokens, b.sessionsIssued, b.totpSuccesses, b.totpFailures, z.allow, z.challenge, z.block];
  if (!nums.every(isNum) || !isStr(b.since) || !isStr(b.until)) return null;
  if (!b.topIps.every((t) => isRecord(t) && isStr(t.ip) && isNum(t.attempts))) return null;
  return b as unknown as AdminStats;
}

function isEvent(e: unknown): e is AdminEvent {
  return (
    isRecord(e) &&
    [e.id, e.createdAt, e.email, e.ip].every(isStr) &&
    (e.stage === "PASSWORD" || e.stage === "TOTP") &&
    isZone(e.zone) &&
    isNumOrNull(e.score) &&
    Array.isArray(e.triggeredRules) &&
    e.triggeredRules.every(isStr) &&
    typeof e.success === "boolean"
  );
}

export function parseEventPage(b: unknown): EventPage | null {
  if (!isRecord(b) || !Array.isArray(b.items) || ![b.page, b.size, b.total, b.totalPages].every(isNum)) return null;
  return b.items.every(isEvent) ? (b as unknown as EventPage) : null;
}

export function parseIps(b: unknown): IpActivity[] | null {
  if (!Array.isArray(b)) return null;
  const ok = b.every(
    (i) =>
      isRecord(i) && isStr(i.ip) && isNum(i.attempts) && isNum(i.failures) && isStr(i.lastSeen) && isZone(i.lastZone) && isNumOrNull(i.lastScore),
  );
  return ok ? (b as IpActivity[]) : null;
}

export function parseRiskConfig(b: unknown): RiskConfig | null {
  if (!isRecord(b) || typeof b.enabled !== "boolean" || !Array.isArray(b.rules)) return null;
  if (![b.challengeThreshold, b.blockThreshold, b.maxScore].every(isNum)) return null;
  const ok = b.rules.every(
    (r) => isRecord(r) && isStr(r.id) && isNum(r.weight) && isNum(r.threshold) && isNumOrNull(r.minAttempts) && isNum(r.windowSeconds),
  );
  return ok ? (b as unknown as RiskConfig) : null;
}

/** Query string de /api/admin/events: solo incluye los filtros con valor. */
export function eventsQuery(filter: EventFilter): string {
  const params = new URLSearchParams();
  if (filter.zone) params.set("zone", filter.zone);
  const ip = filter.ip?.trim();
  if (ip) params.set("ip", ip);
  if (filter.page !== undefined) params.set("page", String(filter.page));
  if (filter.size !== undefined) params.set("size", String(filter.size));
  const qs = params.toString();
  return qs ? `?${qs}` : "";
}

/** Endpoints de /api/admin (solo lectura, Bearer con rol ADMIN). */
export function createAdminApi(http: HttpRequest) {
  async function get<T>(path: string, token: string, parse: (b: unknown) => T | null): Promise<AdminResult<T>> {
    try {
      const res = await http(path, { token });
      if (res.status === 401) return { kind: "unauthorized" };
      if (res.status === 403) return { kind: "forbidden" };
      if (res.status === 400) return { kind: "invalid" };
      if (!res.ok) return { kind: "error" };
      let body: unknown = null;
      try {
        body = await res.json();
      } catch {
        return { kind: "error" };
      }
      const data = parse(body);
      return data === null ? { kind: "error" } : { kind: "ok", data };
    } catch {
      return { kind: "error" };
    }
  }

  return {
    stats: (token: string) => get("/api/admin/stats", token, parseStats),
    events: (token: string, filter: EventFilter = {}) => get(`/api/admin/events${eventsQuery(filter)}`, token, parseEventPage),
    ips: (token: string, limit?: number) =>
      get(`/api/admin/ips${limit === undefined ? "" : `?limit=${limit}`}`, token, parseIps),
    riskConfig: (token: string) => get("/api/admin/risk-config", token, parseRiskConfig),
  };
}

export type AdminApi = ReturnType<typeof createAdminApi>;
