import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { createClient } from "../src/client.mjs";
import { buildDataset, COMMON_PASSWORD, SIZES } from "../src/dataset.mjs";
import { checkMode, compactTimeline, summarize } from "../src/metrics.mjs";
import { validateBaseUrl } from "../src/target.mjs";

describe("objetivo permitido", () => {
  it("acepta solo localhost y 127.0.0.1", () => {
    assert.equal(validateBaseUrl("http://localhost:8080"), "http://localhost:8080");
    assert.equal(validateBaseUrl("http://127.0.0.1:8080/"), "http://127.0.0.1:8080");
    assert.equal(validateBaseUrl("https://localhost"), "https://localhost");
  });

  it("rechaza otros hosts y trucos de URL", () => {
    for (const bad of [
      "http://example.com",
      "http://localhost.evil.com",
      "http://127.0.0.1@evil.com",
      "http://user:pw@localhost:8080",
      "http://10.0.0.5:8080",
      "http://127.0.0.2",
      "http://[::1]:8080",
      "ftp://localhost",
      "file:///etc/passwd",
      "localhost:8080",
      "http://localhost:8080/?x=1",
    ]) {
      assert.throws(() => validateBaseUrl(bad), Error, bad);
    }
  });
});

describe("datos sintéticos", () => {
  const data = buildDataset({ seed: 42, runId: "abc123" });

  it("son deterministas con la misma semilla", () => {
    assert.deepEqual(buildDataset({ seed: 42, runId: "abc123" }), data);
    assert.notDeepEqual(buildDataset({ seed: 43, runId: "abc123" }).leakedList, data.leakedList);
  });

  it("la lista filtrada tiene el tamaño y la fracción válida esperados", () => {
    assert.equal(data.leakedList.length, SIZES.leakedEntries);
    const valid = data.leakedList.filter((c) => c.valid);
    assert.equal(valid.length, SIZES.leakedValid);
    // Cada entrada válida coincide con la contraseña real de una víctima; las inválidas no.
    const real = new Map(data.victims.map((v) => [v.email, v.password]));
    for (const c of data.leakedList) assert.equal(real.get(c.email) === c.password, c.valid);
  });

  it("todas las cuentas cumplen la política (12-128) y usan el dominio reservado", () => {
    for (const a of data.accountsToRegister) {
      assert.ok(a.password.length >= 12 && a.password.length <= 128);
      assert.match(a.email, /-abc123-\d{3}@ejemplo\.test$/);
    }
    assert.equal(new Set(data.accountsToRegister.map((a) => a.email)).size, data.accountsToRegister.length);
  });

  it("spraying y fuerza bruta tienen las víctimas esperadas", () => {
    assert.equal(data.sprayAccounts.filter((a) => a.password === COMMON_PASSWORD).length, SIZES.sprayVulnerable);
    assert.equal(data.dictionary.indexOf(data.bruteVictim.password), SIZES.bruteForceCorrectAt - 1);
  });
});

describe("métricas", () => {
  const at = (statuses, validAt = []) =>
    statuses.map((status, i) => ({
      n: i + 1,
      status,
      code: status === 403 ? "CHALLENGE_REQUIRED" : null,
      valid: validAt.includes(i + 1),
    }));

  it("ataque: conteos, primer CHALLENGE/BLOCK y comprometidas", () => {
    const s = summarize({ id: "b", name: "x", kind: "attack" }, at([401, 200, 401, 403, 401, 429, 429], [2, 4, 7]), 1000);
    assert.deepEqual(s.status, { 200: 1, 401: 3, 403: 1, 429: 2, otros: 0 });
    assert.equal(s.firstChallenge, 4);
    assert.equal(s.firstBlock, 6);
    assert.equal(s.compromised, 1);
    assert.equal(s.validChallenged, 1);
    assert.equal("confirmedBy403" in s, false);
    assert.equal(s.validBlocked, 1);
    assert.equal(s.validTried, 3);
    assert.equal(s.falsePositives, null);
    assert.equal(s.effectiveRate, 7);
  });

  it("la secuencia de respuestas se compacta por rachas", () => {
    assert.equal(compactTimeline([401, 401, 401, 403, 401, 429, 429]), "401×3, 403, 401, 429×2");
    assert.equal(compactTimeline([]), "");
  });

  it("legítimo: falsos positivos y usuarios rechazados", () => {
    const s = summarize({ id: "a", name: "x", kind: "legitimate" }, at([401, 200, 403], [2, 3]), 500);
    assert.equal(s.falsePositives, 1);
    assert.equal(s.legitDenied, 1);
    assert.equal(s.compromised, null);
    assert.equal(s.firstBlock, null);
  });

  it("avisa si el modo declarado no coincide con lo observado", () => {
    const quiet = [summarize({ id: "b", name: "x", kind: "attack" }, at([401, 200]), 1)];
    const loud = [summarize({ id: "b", name: "x", kind: "attack" }, at([401, 429]), 1)];
    assert.equal(checkMode("sin-proteccion", quiet).length, 0);
    assert.equal(checkMode("sin-proteccion", loud).length, 1);
    assert.equal(checkMode("protegido", loud).length, 0);
    assert.equal(checkMode("protegido", quiet).length, 1);
  });
});

describe("cliente", () => {
  it("envía X-Simulated-IP, no sigue redirecciones y respeta la tasa", async () => {
    const calls = [];
    const waits = [];
    let clock = 0;
    const client = createClient({
      baseUrl: "http://localhost:8080",
      rate: 5,
      now: () => clock,
      wait: async (ms) => {
        waits.push(ms);
        clock += ms;
      },
      fetchFn: async (url, init) => {
        calls.push({ url, init });
        return new Response(JSON.stringify({ code: "CHALLENGE_REQUIRED" }), { status: 403 });
      },
    });

    const r1 = await client.login("a@ejemplo.test", "x", "198.51.100.10");
    await client.login("a@ejemplo.test", "x", "198.51.100.10");

    assert.deepEqual([r1.status, r1.code], [403, "CHALLENGE_REQUIRED"]);
    assert.equal(calls[0].url, "http://localhost:8080/api/auth/login");
    assert.equal(calls[0].init.headers["X-Simulated-IP"], "198.51.100.10");
    assert.equal(calls[0].init.redirect, "error");
    assert.deepEqual(waits, [200]); // 5 req/s → 200 ms entre peticiones
  });

  it("un error de red se reporta como status 0", async () => {
    const client = createClient({
      baseUrl: "http://localhost:8080",
      rate: 50,
      fetchFn: async () => {
        throw new TypeError("fetch failed");
      },
    });
    const r = await client.register("a@ejemplo.test", "x");
    assert.equal(r.status, 0);
  });
});
