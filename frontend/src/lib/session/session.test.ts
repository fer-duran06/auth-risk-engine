import { describe, expect, it, vi } from "vitest";
import type { AccessToken } from "../api/auth";
import { createSession, type SessionApi } from "./session";

const token = (t: string): AccessToken => ({ accessToken: t, expiresIn: 900 });

/** API falsa: cada refresh devuelve el siguiente valor de la lista. */
function fakeApi(refreshResults: (AccessToken | null)[]) {
  const queue = [...refreshResults];
  const api: SessionApi = {
    refresh: vi.fn(async () => {
      await Promise.resolve();
      return queue.shift() ?? null;
    }),
    logout: vi.fn(async () => {}),
  };
  return api;
}

type Result = { kind: "ok"; usedToken: string } | { kind: "unauthorized" };

describe("sesión en memoria", () => {
  it("arranca en unknown y start la autentica", () => {
    const s = createSession(fakeApi([]));
    expect(s.getStatus()).toBe("unknown");
    s.start(token("a"));
    expect(s.getStatus()).toBe("authenticated");
    expect(s.getToken()).toBe("a");
  });

  it("restore usa refresh si no hay token", async () => {
    const api = fakeApi([token("r1")]);
    const s = createSession(api);
    expect(await s.restore()).toBe(true);
    expect(s.getToken()).toBe("r1");
    expect(api.refresh).toHaveBeenCalledTimes(1);
  });

  it("restore no llama al backend si ya hay token", async () => {
    const api = fakeApi([]);
    const s = createSession(api);
    s.start(token("a"));
    expect(await s.restore()).toBe(true);
    expect(api.refresh).not.toHaveBeenCalled();
  });

  it("restore fallido deja la sesión anónima", async () => {
    const s = createSession(fakeApi([null]));
    expect(await s.restore()).toBe(false);
    expect(s.getStatus()).toBe("anonymous");
    expect(s.getToken()).toBeNull();
  });

  it("refrescos concurrentes comparten una sola petición (evita falsa reutilización)", async () => {
    const api = fakeApi([token("r1"), token("r2")]);
    const s = createSession(api);
    const results = await Promise.all([s.restore(), s.restore(), s.restore()]);
    expect(results).toEqual([true, true, true]);
    expect(api.refresh).toHaveBeenCalledTimes(1);
    expect(s.getToken()).toBe("r1");
  });

  it("withAuth: ante 401 refresca una vez y reintenta con el token nuevo", async () => {
    const api = fakeApi([token("nuevo")]);
    const s = createSession(api);
    s.start(token("viejo"));
    const call = vi.fn(async (t: string): Promise<Result> => (t === "viejo" ? { kind: "unauthorized" } : { kind: "ok", usedToken: t }));

    expect(await s.withAuth(call)).toEqual({ kind: "ok", usedToken: "nuevo" });
    expect(call).toHaveBeenCalledTimes(2);
    expect(api.refresh).toHaveBeenCalledTimes(1);
  });

  it("withAuth: si el refresh falla, la sesión queda anónima sin reintentar", async () => {
    const api = fakeApi([null]);
    const s = createSession(api);
    s.start(token("viejo"));
    const call = vi.fn(async (): Promise<Result> => ({ kind: "unauthorized" }));

    expect(await s.withAuth(call)).toEqual({ kind: "unauthorized" });
    expect(call).toHaveBeenCalledTimes(1);
    expect(s.getStatus()).toBe("anonymous");
  });

  it("withAuth: un segundo 401 tras refrescar no entra en bucle", async () => {
    const api = fakeApi([token("r1"), token("r2")]);
    const s = createSession(api);
    s.start(token("a"));
    const call = vi.fn(async (): Promise<Result> => ({ kind: "unauthorized" }));

    expect(await s.withAuth(call)).toEqual({ kind: "unauthorized" });
    expect(call).toHaveBeenCalledTimes(2);
    expect(api.refresh).toHaveBeenCalledTimes(1);
    expect(s.getStatus()).toBe("anonymous");
  });

  it("withAuth sin token intenta restaurar antes de llamar", async () => {
    const api = fakeApi([null]);
    const s = createSession(api);
    const call = vi.fn(async (): Promise<Result> => ({ kind: "ok", usedToken: "x" }));
    expect(await s.withAuth(call)).toEqual({ kind: "unauthorized" });
    expect(call).not.toHaveBeenCalled();
  });

  it("logout revoca en el backend, borra el token y notifica", async () => {
    const api = fakeApi([]);
    const s = createSession(api);
    s.start(token("a"));
    const listener = vi.fn();
    s.subscribe(listener);

    await s.logout();
    expect(api.logout).toHaveBeenCalledTimes(1);
    expect(s.getToken()).toBeNull();
    expect(s.getStatus()).toBe("anonymous");
    expect(listener).toHaveBeenCalledTimes(1);
  });

  it("logout borra la sesión local aunque el backend falle", async () => {
    const api = fakeApi([]);
    api.logout = vi.fn(async () => {
      throw new Error("boom");
    });
    const s = createSession(api);
    s.start(token("a"));
    await expect(s.logout()).rejects.toThrow();
    expect(s.getToken()).toBeNull();
  });
});
