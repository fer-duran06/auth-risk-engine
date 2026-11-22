/**
 * Cliente HTTP del simulador: fetch nativo, ritmo controlado (--rate) y header X-Simulated-IP.
 * redirect: "error" impide que una redirección lleve las peticiones fuera de localhost.
 */

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const TIMEOUT_MS = 15_000;

export function createClient({ baseUrl, rate, fetchFn = fetch, now = () => performance.now(), wait = sleep }) {
  const interval = 1000 / rate;
  let nextSlot = 0;

  /** Espera hasta el siguiente hueco según la tasa configurada (peticiones secuenciales). */
  async function pace() {
    const t = now();
    if (t < nextSlot) await wait(nextSlot - t);
    nextSlot = Math.max(t, nextSlot) + interval;
  }

  async function post(path, body, simulatedIp) {
    await pace();
    const headers = { "Content-Type": "application/json", Accept: "application/json" };
    if (simulatedIp) headers["X-Simulated-IP"] = simulatedIp;
    const started = now();
    try {
      const res = await fetchFn(`${baseUrl}${path}`, {
        method: "POST",
        headers,
        body: JSON.stringify(body),
        redirect: "error",
        signal: AbortSignal.timeout(TIMEOUT_MS),
      });
      let code = null;
      try {
        const data = await res.json();
        code = typeof data?.code === "string" ? data.code : null;
      } catch {
        // Sin cuerpo JSON (p. ej. 204).
      }
      return { status: res.status, code, ms: Math.round(now() - started) };
    } catch (err) {
      return { status: 0, code: err?.name ?? "NetworkError", ms: Math.round(now() - started) };
    }
  }

  return {
    login: (email, password, ip) => post("/api/auth/login", { email, password }, ip),
    register: (email, password) => post("/api/auth/register", { email, password }),
    async health() {
      try {
        const res = await fetchFn(`${baseUrl}/actuator/health`, { redirect: "error", signal: AbortSignal.timeout(5_000) });
        const data = await res.json();
        return data?.status === "UP";
      } catch {
        return false;
      }
    },
  };
}
