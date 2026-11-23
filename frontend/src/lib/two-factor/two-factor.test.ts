import { afterEach, describe, expect, it, vi } from "vitest";
import { createAuthApi } from "../api/auth";
import { createHttp } from "../api/http";
import { CHALLENGE_TTL_MS, createChallengeStore } from "./challenge-store";
import { formatSecret, isCompleteCode, normalizeCode } from "./code";
import { qrMatrix, qrPath } from "./qr";

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
  const api = createAuthApi(createHttp({ baseUrl: BASE, demoMode: false, fetchFn }));
  const lastCall = () => fetchFn.mock.calls.at(-1)!;
  return { api, lastCall };
}

describe("verificación del desafío", () => {
  it("envía challengeId y código, y guarda el token si responde 200", async () => {
    const { api, lastCall } = setup(json(200, { accessToken: "tok", tokenType: "Bearer", expiresIn: 900 }));
    expect(await api.verifyChallenge("ch-1", "123456")).toEqual({
      kind: "ok",
      token: { accessToken: "tok", expiresIn: 900 },
    });
    const [url, init] = lastCall();
    expect(url).toBe(`${BASE}/api/auth/2fa/verify`);
    expect(init!.method).toBe("POST");
    expect(init!.credentials).toBe("include");
    expect(JSON.parse(init!.body as string)).toEqual({ challengeId: "ch-1", code: "123456" });
  });

  it("401 y 400 son el mismo resultado genérico", async () => {
    expect(await setup(json(401, { error: "Credenciales inválidas" })).api.verifyChallenge("x", "1")).toEqual({ kind: "invalid" });
    expect(await setup(json(400)).api.verifyChallenge("x", "1")).toEqual({ kind: "invalid" });
  });

  it("5xx, red caída o 200 sin token son error", async () => {
    expect(await setup(json(500)).api.verifyChallenge("x", "1")).toEqual({ kind: "error" });
    expect(await setup(new TypeError("red")).api.verifyChallenge("x", "1")).toEqual({ kind: "error" });
    expect(await setup(json(200, {})).api.verifyChallenge("x", "1")).toEqual({ kind: "error" });
  });
});

describe("gestión del 2FA", () => {
  const uri = "otpauth://totp/AuthShield:a%40b.test?secret=JBSWY3DPEHPK3PXP&issuer=AuthShield";

  it("setup envía Bearer y valida la respuesta", async () => {
    const { api, lastCall } = setup(json(200, { secret: "JBSWY3DPEHPK3PXP", otpauthUri: uri }));
    expect(await api.totpSetup("tok")).toEqual({ kind: "ok", setup: { secret: "JBSWY3DPEHPK3PXP", otpauthUri: uri } });
    const [url, init] = lastCall();
    expect(url).toBe(`${BASE}/api/auth/2fa/setup`);
    expect((init!.headers as Record<string, string>).Authorization).toBe("Bearer tok");

    expect(await setup(json(200, { secret: "no base32!", otpauthUri: uri })).api.totpSetup("t")).toEqual({ kind: "error" });
    expect(await setup(json(200, { secret: "JBSW", otpauthUri: "https://x" })).api.totpSetup("t")).toEqual({ kind: "error" });
    expect(await setup(json(409)).api.totpSetup("t")).toEqual({ kind: "already-enabled" });
    expect(await setup(json(401)).api.totpSetup("t")).toEqual({ kind: "unauthorized" });
  });

  it("enable mapea 204, 400, 409, 401 y errores", async () => {
    const ok = setup(new Response(null, { status: 204 }));
    expect(await ok.api.totpEnable("tok", "123456")).toEqual({ kind: "ok" });
    expect(JSON.parse(ok.lastCall()[1]!.body as string)).toEqual({ code: "123456" });
    expect(await setup(json(400)).api.totpEnable("t", "1")).toEqual({ kind: "invalid" });
    expect(await setup(json(409)).api.totpEnable("t", "1")).toEqual({ kind: "already-enabled" });
    expect(await setup(json(401)).api.totpEnable("t", "1")).toEqual({ kind: "unauthorized" });
    expect(await setup(new TypeError("red")).api.totpEnable("t", "1")).toEqual({ kind: "error" });
  });

  it("status interpreta enabled y confirmedAt", async () => {
    expect(await setup(json(200, { enabled: true, confirmedAt: "2026-11-22T10:00:00Z" })).api.totpStatus("t")).toEqual({
      kind: "ok",
      status: { enabled: true, confirmedAt: "2026-11-22T10:00:00Z" },
    });
    expect(await setup(json(200, { enabled: false, confirmedAt: null })).api.totpStatus("t")).toEqual({
      kind: "ok",
      status: { enabled: false, confirmedAt: null },
    });
    expect(await setup(json(200, { enabled: "sí" })).api.totpStatus("t")).toEqual({ kind: "error" });
    expect(await setup(json(401)).api.totpStatus("t")).toEqual({ kind: "unauthorized" });
  });
});

describe("almacén del desafío", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("guarda el challengeId solo en memoria y vence con el TTL del backend", () => {
    // Si se tocara el almacenamiento del navegador, estas funciones fallarían.
    const forbidden = () => {
      throw new Error("almacenamiento prohibido");
    };
    const storage = { setItem: forbidden, getItem: forbidden, removeItem: forbidden };
    vi.stubGlobal("localStorage", storage);
    vi.stubGlobal("sessionStorage", storage);

    let t = 1_000;
    const store = createChallengeStore(() => t);
    expect(store.get()).toBeNull();

    store.set("ch-1");
    expect(store.get()).toEqual({ id: "ch-1", expiresAt: 1_000 + CHALLENGE_TTL_MS });

    t += CHALLENGE_TTL_MS - 1;
    expect(store.get()?.id).toBe("ch-1");
    t += 1;
    expect(store.get()).toBeNull();

    store.set("ch-2");
    store.clear();
    expect(store.get()).toBeNull();
  });

  it("notifica a los suscriptores al fijar o borrar el desafío", () => {
    const store = createChallengeStore();
    const listener = vi.fn();
    const unsubscribe = store.subscribe(listener);
    store.set("a");
    store.clear();
    expect(listener).toHaveBeenCalledTimes(2);
    unsubscribe();
    store.set("b");
    expect(listener).toHaveBeenCalledTimes(2);
  });

  it("cada instancia es independiente", () => {
    const a = createChallengeStore();
    const b = createChallengeStore();
    a.set("x");
    expect(b.get()).toBeNull();
  });
});

describe("código TOTP", () => {
  it("normaliza a 6 dígitos ASCII", () => {
    expect(normalizeCode("123 456")).toBe("123456");
    expect(normalizeCode("123-4567")).toBe("123456");
    expect(normalizeCode("12a3")).toBe("123");
    expect(normalizeCode("١٢٣٤٥٦")).toBe("");
    expect(normalizeCode("")).toBe("");
  });

  it("solo 6 dígitos exactos son un código completo", () => {
    expect(isCompleteCode("123456")).toBe(true);
    for (const c of ["12345", "1234567", "12345a", " 123456", ""]) expect(isCompleteCode(c)).toBe(false);
  });

  it("agrupa la clave de 4 en 4", () => {
    expect(formatSecret("JBSWY3DPEHPK3PXP")).toBe("JBSW Y3DP EHPK 3PXP");
    expect(formatSecret("ABCDE")).toBe("ABCD E");
  });
});

describe("QR", () => {
  it("genera una matriz cuadrada con los patrones de posición", () => {
    const m = qrMatrix("otpauth://totp/AuthShield:a%40b.test?secret=JBSWY3DPEHPK3PXP&issuer=AuthShield");
    const size = m.length;
    expect(size).toBeGreaterThanOrEqual(21);
    expect((size - 17) % 4).toBe(0);
    m.forEach((row) => expect(row).toHaveLength(size));
    // Esquina superior izquierda del patrón de posición: borde 7x7 oscuro.
    for (let i = 0; i < 7; i++) {
      expect(m[0][i]).toBe(true);
      expect(m[i][0]).toBe(true);
    }
    expect(m[1][1]).toBe(false);
  });

  it("el path tiene un cuadrado por módulo oscuro", () => {
    expect(qrPath([[true, false], [false, true]])).toBe("M0 0h1v1h-1zM1 1h1v1h-1z");
  });
});
