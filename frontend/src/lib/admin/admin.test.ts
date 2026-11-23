import { describe, expect, it, vi } from "vitest";
import { createAdminApi, eventsQuery } from "../api/admin";
import { createHttp } from "../api/http";
import { adminAccess } from "./access";
import { parseEventFilter } from "./filters";
import { barWidth, formatWindow, ruleCondition, ruleLabel, zoneForScore, zoneShares } from "./format";

const BASE = "http://api.test";

function json(status: number, body?: unknown): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function setup(response: Response | Error) {
  const fetchFn = vi.fn<typeof fetch>(async () => {
    if (response instanceof Error) throw response;
    return response;
  });
  const api = createAdminApi(createHttp({ baseUrl: BASE, demoMode: false, fetchFn }));
  const lastCall = () => fetchFn.mock.calls.at(-1)!;
  return { api, lastCall };
}

const stats = {
  since: "2026-11-21T10:00:00Z",
  until: "2026-11-22T10:00:00Z",
  loginAttempts: 5,
  byZone: { allow: 2, challenge: 2, block: 1 },
  loginWithoutTokens: 3,
  sessionsIssued: 3,
  totpSuccesses: 1,
  totpFailures: 1,
  topIps: [{ ip: "198.51.100.1", attempts: 3 }],
};

const event = {
  id: "e1",
  createdAt: "2026-11-22T09:00:00Z",
  email: "a@b.test",
  ip: "198.51.100.1",
  stage: "PASSWORD",
  zone: "BLOCK",
  score: 70,
  triggeredRules: ["IP_VELOCITY"],
  success: false,
};

describe("cliente de administración", () => {
  it("envía el Bearer y parsea stats", async () => {
    const { api, lastCall } = setup(json(200, stats));
    expect(await api.stats("tok")).toEqual({ kind: "ok", data: stats });
    const [url, init] = lastCall();
    expect(url).toBe(`${BASE}/api/admin/stats`);
    expect((init!.headers as Record<string, string>).Authorization).toBe("Bearer tok");
    expect(init!.credentials).toBe("include");
  });

  it("mapea 401, 403, 400, 5xx, red caída y cuerpos inválidos", async () => {
    expect(await setup(json(401)).api.stats("t")).toEqual({ kind: "unauthorized" });
    expect(await setup(json(403)).api.stats("t")).toEqual({ kind: "forbidden" });
    expect(await setup(json(400)).api.events("t", { size: 500 })).toEqual({ kind: "invalid" });
    expect(await setup(json(500)).api.ips("t")).toEqual({ kind: "error" });
    expect(await setup(new TypeError("red")).api.riskConfig("t")).toEqual({ kind: "error" });
    expect(await setup(json(200, { ...stats, byZone: null })).api.stats("t")).toEqual({ kind: "error" });
    expect(await setup(new Response("no json", { status: 200 })).api.stats("t")).toEqual({ kind: "error" });
  });

  it("events arma la query y valida cada fila", async () => {
    const page = { items: [event], page: 1, size: 10, total: 11, totalPages: 2 };
    const { api, lastCall } = setup(json(200, page));
    expect(await api.events("t", { zone: "BLOCK", ip: " 198.51.100.1 ", page: 1, size: 10 })).toEqual({ kind: "ok", data: page });
    expect(lastCall()[0]).toBe(`${BASE}/api/admin/events?zone=BLOCK&ip=198.51.100.1&page=1&size=10`);

    const bad = { ...page, items: [{ ...event, zone: "OTRA" }] };
    expect(await setup(json(200, bad)).api.events("t")).toEqual({ kind: "error" });
    const nullScore = { ...page, items: [{ ...event, score: null }] };
    expect((await setup(json(200, nullScore)).api.events("t")).kind).toBe("ok");
  });

  it("ips y risk-config", async () => {
    const ips = [{ ip: "198.51.100.1", attempts: 3, failures: 2, lastSeen: "2026-11-22T09:00:00Z", lastZone: "BLOCK", lastScore: null }];
    const { api, lastCall } = setup(json(200, ips));
    expect(await api.ips("t", 20)).toEqual({ kind: "ok", data: ips });
    expect(lastCall()[0]).toBe(`${BASE}/api/admin/ips?limit=20`);

    const config = {
      enabled: true,
      challengeThreshold: 30,
      blockThreshold: 70,
      maxScore: 100,
      rules: [{ id: "GLOBAL_FAILURE_RATE", weight: 15, threshold: 0.5, minAttempts: 20, windowSeconds: 300 }],
    };
    expect(await setup(json(200, config)).api.riskConfig("t")).toEqual({ kind: "ok", data: config });
    expect(await setup(json(200, { ...config, rules: [{ id: 1 }] })).api.riskConfig("t")).toEqual({ kind: "error" });
  });
});

describe("query de eventos", () => {
  it("omite filtros vacíos y codifica valores", () => {
    expect(eventsQuery({})).toBe("");
    expect(eventsQuery({ zone: null, ip: "  ", page: 0 })).toBe("?page=0");
    expect(eventsQuery({ ip: "2001:db8::1" })).toBe("?ip=2001%3Adb8%3A%3A1");
  });
});

describe("formato y cálculos", () => {
  it("el reparto por zona suma 100 y sin datos es 0", () => {
    const shares = zoneShares({ allow: 1, challenge: 1, block: 1 });
    expect(shares.map((s) => s.percent)).toEqual([34, 33, 33]);
    expect(shares.reduce((a, s) => a + s.percent, 0)).toBe(100);
    expect(zoneShares({ allow: 0, challenge: 0, block: 0 }).map((s) => s.percent)).toEqual([0, 0, 0]);
    expect(zoneShares({ allow: 2, challenge: 2, block: 1 }).map((s) => s.percent)).toEqual([40, 40, 20]);
  });

  it("ancho de barra relativo, con mínimo visible y acotado", () => {
    expect(barWidth(0, 10)).toBe(0);
    expect(barWidth(5, 0)).toBe(0);
    expect(barWidth(10, 10)).toBe(100);
    expect(barWidth(1, 1000)).toBe(2);
    expect(barWidth(30, 10)).toBe(100);
  });

  it("ventanas y condiciones de las reglas", () => {
    expect(formatWindow(60)).toBe("1 min");
    expect(formatWindow(900)).toBe("15 min");
    expect(formatWindow(3600)).toBe("1 h");
    expect(formatWindow(45)).toBe("45 s");
    expect(ruleCondition({ id: "IP_VELOCITY", weight: 30, threshold: 10, minAttempts: null, windowSeconds: 60 })).toBe(
      "≥ 10 intentos desde la IP en 1 min",
    );
    expect(
      ruleCondition({ id: "GLOBAL_FAILURE_RATE", weight: 15, threshold: 0.5, minAttempts: 20, windowSeconds: 300 }),
    ).toBe("≥ 50 % de fallos en todo el sistema en 5 min, con al menos 20 intentos");
    expect(ruleLabel("ACCOUNT_FAILURES")).toBe("Fallos por cuenta");
    expect(ruleLabel("DESCONOCIDA")).toBe("DESCONOCIDA");
  });

  it("zona por score con los umbrales", () => {
    expect([0, 29, 30, 69, 70, 100].map((s) => zoneForScore(s, 30, 70))).toEqual([
      "ALLOW", "ALLOW", "CHALLENGE", "CHALLENGE", "BLOCK", "BLOCK",
    ]);
  });
});

describe("acceso al panel", () => {
  const user = (role: string) => ({ kind: "ok" as const, user: { id: "1", email: "a@b.test", role, createdAt: "" } });

  it("solo ADMIN entra; el resto ve acceso denegado o vuelve al login", () => {
    expect(adminAccess(user("ADMIN"))).toBe("admin");
    expect(adminAccess(user("USER"))).toBe("denied");
    expect(adminAccess({ kind: "unauthorized" })).toBe("unauthorized");
    expect(adminAccess({ kind: "error" })).toBe("error");
  });
});

describe("filtros de eventos desde la URL", () => {
  const parse = (qs: string) => parseEventFilter(new URLSearchParams(qs));

  it("valores por defecto", () => {
    expect(parse("")).toEqual({ zone: null, ip: "", page: 0, size: 25 });
  });

  it("acepta valores válidos y normaliza la zona", () => {
    expect(parse("zone=block&ip=%20198.51.100.1%20&page=3&size=100")).toEqual({ zone: "BLOCK", ip: "198.51.100.1", page: 3, size: 100 });
  });

  it("ignora valores inválidos en vez de enviarlos al backend", () => {
    expect(parse("zone=PELIGRO&page=-1&size=500")).toEqual({ zone: null, ip: "", page: 0, size: 25 });
    expect(parse("page=1.5&size=abc")).toMatchObject({ page: 0, size: 25 });
    expect(parse(`ip=${"9".repeat(60)}`).ip).toHaveLength(45);
  });
});
