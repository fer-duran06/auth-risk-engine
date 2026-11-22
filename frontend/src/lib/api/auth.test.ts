import { describe, expect, it, vi } from "vitest";
import { createAuthApi } from "./auth";
import { normalizeBaseUrl, parseDemoMode } from "./config";
import { createHttp, type HttpConfig } from "./http";
import { isIpLiteral, SIMULATED_IP_HEADER } from "./simulated-ip";

const BASE = "http://api.test";

function json(status: number, body?: unknown): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/** Cliente con fetch simulado; devuelve también el mock para inspeccionar las llamadas. */
function setup(response: Response | Error, config: Partial<HttpConfig> = {}) {
  const fetchFn = vi.fn<typeof fetch>(async () => {
    if (response instanceof Error) throw response;
    return response;
  });
  const api = createAuthApi(createHttp({ baseUrl: BASE, demoMode: false, fetchFn, ...config }));
  const lastInit = () => fetchFn.mock.calls.at(-1)![1]!;
  const lastHeaders = () => lastInit().headers as Record<string, string>;
  return { api, fetchFn, lastInit, lastHeaders };
}

describe("configuración", () => {
  it("usa localhost:8080 por defecto y quita barras finales", () => {
    expect(normalizeBaseUrl(undefined)).toBe("http://localhost:8080");
    expect(normalizeBaseUrl("  ")).toBe("http://localhost:8080");
    expect(normalizeBaseUrl("https://api.example.test//")).toBe("https://api.example.test");
  });

  it("el modo demo solo se activa con 'true'", () => {
    expect(parseDemoMode("true")).toBe(true);
    for (const v of [undefined, "", "false", "1", "TRUE", "yes"]) expect(parseDemoMode(v)).toBe(false);
  });
});

describe("cliente HTTP", () => {
  it("envía credentials include, JSON y la URL base", async () => {
    const { api, fetchFn, lastInit, lastHeaders } = setup(json(202, { message: "ok" }));
    await api.register("a@b.test", "contraseña-larga-123");

    expect(fetchFn.mock.calls[0][0]).toBe(`${BASE}/api/auth/register`);
    expect(lastInit().credentials).toBe("include");
    expect(lastInit().method).toBe("POST");
    expect(lastHeaders()["Content-Type"]).toBe("application/json");
    expect(JSON.parse(lastInit().body as string)).toEqual({ email: "a@b.test", password: "contraseña-larga-123" });
  });

  it("el refresh también envía la cookie (credentials include)", async () => {
    const { api, lastInit } = setup(json(401));
    await api.refresh();
    expect(lastInit().credentials).toBe("include");
  });

  it("sin modo demo nunca envía X-Simulated-IP", async () => {
    const { api, lastHeaders } = setup(json(401), { demoMode: false, getSimulatedIp: () => "203.0.113.10" });
    await api.login("a@b.test", "x");
    expect(lastHeaders()).not.toHaveProperty(SIMULATED_IP_HEADER);
  });

  it("con modo demo envía X-Simulated-IP solo si hay IP elegida", async () => {
    let ip: string | null = null;
    const { api, lastHeaders } = setup(json(401), { demoMode: true, getSimulatedIp: () => ip });

    await api.login("a@b.test", "x");
    expect(lastHeaders()).not.toHaveProperty(SIMULATED_IP_HEADER);

    ip = "203.0.113.10";
    await api.login("a@b.test", "x");
    expect(lastHeaders()[SIMULATED_IP_HEADER]).toBe("203.0.113.10");
  });

  it("me envía el access token como Bearer", async () => {
    const { api, lastHeaders } = setup(json(401));
    await api.me("tok-123");
    expect(lastHeaders().Authorization).toBe("Bearer tok-123");
  });
});

describe("login", () => {
  it("200 devuelve el token", async () => {
    const { api } = setup(json(200, { accessToken: "jwt", tokenType: "Bearer", expiresIn: 900 }));
    expect(await api.login("a@b.test", "x")).toEqual({ kind: "ok", token: { accessToken: "jwt", expiresIn: 900 } });
  });

  it("200 sin token válido es error", async () => {
    const { api } = setup(json(200, { foo: 1 }));
    expect(await api.login("a@b.test", "x")).toEqual({ kind: "error" });
  });

  it("401 y 400 son credenciales inválidas", async () => {
    expect(await setup(json(401, { error: "Credenciales inválidas" })).api.login("a", "b")).toEqual({ kind: "invalid" });
    expect(await setup(json(400, { error: "Solicitud inválida" })).api.login("a", "b")).toEqual({ kind: "invalid" });
  });

  it("403 con CHALLENGE_REQUIRED y challengeId es desafío", async () => {
    const { api } = setup(
      json(403, { error: "Verificación adicional requerida", code: "CHALLENGE_REQUIRED", challengeId: "abc_-123" }),
    );
    expect(await api.login("a", "b")).toEqual({ kind: "challenge", challengeId: "abc_-123" });
  });

  it("403 CHALLENGE_REQUIRED sin challengeId válido es error (no se puede continuar)", async () => {
    for (const challengeId of [undefined, "", 42, "x".repeat(129)]) {
      const body = { error: "Verificación adicional requerida", code: "CHALLENGE_REQUIRED", challengeId };
      expect(await setup(json(403, body)).api.login("a", "b")).toEqual({ kind: "error" });
    }
  });

  it("403 sin el código es error genérico", async () => {
    expect(await setup(json(403, { error: "otro" })).api.login("a", "b")).toEqual({ kind: "error" });
  });

  it("429 es bloqueo por demasiados intentos", async () => {
    expect(await setup(json(429, { error: "Demasiados intentos. Intenta más tarde." })).api.login("a", "b")).toEqual({
      kind: "rate-limited",
    });
  });

  it("5xx y red caída son error genérico", async () => {
    expect(await setup(json(500, { error: "Error interno" })).api.login("a", "b")).toEqual({ kind: "error" });
    expect(await setup(json(503)).api.login("a", "b")).toEqual({ kind: "error" });
    expect(await setup(new TypeError("Failed to fetch")).api.login("a", "b")).toEqual({ kind: "error" });
  });
});

describe("registro, refresh, me y logout", () => {
  it("registro: 202 aceptado, 400 inválido, red caída error", async () => {
    expect(await setup(json(202, { message: "x" })).api.register("a", "b")).toEqual({ kind: "accepted" });
    expect(await setup(json(400, { error: "x" })).api.register("a", "b")).toEqual({ kind: "invalid" });
    expect(await setup(new TypeError("offline")).api.register("a", "b")).toEqual({ kind: "error" });
  });

  it("refresh: token o null", async () => {
    expect(await setup(json(200, { accessToken: "n", expiresIn: 900 })).api.refresh()).toEqual({
      accessToken: "n",
      expiresIn: 900,
    });
    expect(await setup(json(401)).api.refresh()).toBeNull();
    expect(await setup(new TypeError("offline")).api.refresh()).toBeNull();
  });

  it("me: usuario, unauthorized o error", async () => {
    const user = { id: "u1", email: "a@b.test", role: "USER", createdAt: "2026-01-01T00:00:00Z" };
    expect(await setup(json(200, user)).api.me("t")).toEqual({ kind: "ok", user });
    expect(await setup(json(401)).api.me("t")).toEqual({ kind: "unauthorized" });
    expect(await setup(json(500)).api.me("t")).toEqual({ kind: "error" });
    expect(await setup(json(200, { id: 1 })).api.me("t")).toEqual({ kind: "error" });
  });

  it("logout no lanza aunque falle la red", async () => {
    await expect(setup(new TypeError("offline")).api.logout()).resolves.toBeUndefined();
  });
});

describe("validación de IP simulada", () => {
  it("acepta literales IPv4/IPv6 y rechaza nombres de host", () => {
    for (const ok of ["203.0.113.10", "0.0.0.0", "2001:db8::1", "::1"]) expect(isIpLiteral(ok)).toBe(true);
    for (const bad of ["localhost", "evil.example.com", "1.2.3.999", "1.2.3", "", "10.0.0.1, 10.0.0.2"])
      expect(isIpLiteral(bad)).toBe(false);
  });
});
