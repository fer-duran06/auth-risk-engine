/** Métricas por escenario a partir de la lista de intentos. */

const CHALLENGE_REQUIRED = "CHALLENGE_REQUIRED";

const firstIndex = (attempts, predicate) => {
  const found = attempts.find(predicate);
  return found ? found.n : null;
};

/**
 * @param {{ id: string, name: string, kind: "legitimate" | "attack" }} scenario
 * @param {{ n: number, valid: boolean, status: number, code: string | null }[]} attempts
 * @param {number} durationMs
 */
export function summarize(scenario, attempts, durationMs) {
  const count = (status) => attempts.filter((a) => a.status === status).length;
  const status = { 200: count(200), 401: count(401), 403: count(403), 429: count(429) };
  status.otros = attempts.length - status[200] - status[401] - status[403] - status[429];

  const stopped = status[403] + status[429];
  const validTried = attempts.filter((a) => a.valid).length;
  const isAttack = scenario.kind === "attack";

  return {
    id: scenario.id,
    name: scenario.name,
    kind: scenario.kind,
    attempts: attempts.length,
    status,
    firstChallenge: firstIndex(attempts, (a) => a.status === 403 && a.code === CHALLENGE_REQUIRED),
    firstBlock: firstIndex(attempts, (a) => a.status === 429),
    /** Intentos que recibieron 403 o 429 (frenados por el motor). */
    stopped,
    stoppedPct: attempts.length ? round((stopped / attempts.length) * 100) : 0,
    validTried,
    /** Ataque: credenciales válidas que obtuvieron tokens (200). */
    compromised: isAttack ? status[200] : null,
    /**
     * Ataque: válidas que recibieron 403 (sin tokens). Desde la Fase 4 el 403 de CHALLENGE es idéntico
     * con contraseña correcta o incorrecta, así que el atacante no puede distinguirlas: solo el
     * simulador lo sabe porque generó los datos. Ya no cuentan como "confirmadas".
     */
    validChallenged: isAttack ? attempts.filter((a) => a.status === 403 && a.valid).length : null,
    /** Ataque: válidas que recibieron 429 (el atacante no supo que eran correctas). */
    validBlocked: isAttack ? attempts.filter((a) => a.valid && a.status === 429).length : null,
    /** Legítimo: intentos frenados (403/429). Con contraseña correcta o con un error de tecleo. */
    falsePositives: isAttack ? null : stopped,
    /** Legítimo: usuarios que no pudieron entrar con su contraseña correcta. */
    legitDenied: isAttack ? null : attempts.filter((a) => a.valid && a.status !== 200).length,
    durationMs,
    effectiveRate: durationMs > 0 ? round(attempts.length / (durationMs / 1000)) : 0,
    /** Secuencia compacta de respuestas (p. ej. "401×8, 403, 429×140"), para ver dónde cambia de zona. */
    timeline: compactTimeline(attempts.map((a) => a.status)),
  };
}

/**
 * Comprueba que el comportamiento observado coincide con el modo declarado.
 * Devuelve una lista de avisos (vacía si todo cuadra).
 */
export function checkMode(mode, results) {
  const warnings = [];
  const anyStopped = results.some((r) => r.stopped > 0);
  if (mode === "sin-proteccion" && anyStopped) {
    warnings.push("Modo sin-proteccion pero hubo respuestas 403/429: ¿el backend arrancó con RISK_ENABLED=false?");
  }
  if (mode === "protegido" && !anyStopped) {
    warnings.push("Modo protegido pero no hubo ningún 403/429: ¿el backend tiene el motor activo y app.demo-mode=true?");
  }
  const network = results.reduce((acc, r) => acc + r.status.otros, 0);
  if (network > 0) warnings.push(`${network} respuestas inesperadas (errores de red u otros códigos).`);
  return warnings;
}

/** Codifica rachas iguales: [401, 401, 403] → "401×2, 403". */
export function compactTimeline(statuses) {
  const runs = [];
  for (const s of statuses) {
    const last = runs.at(-1);
    if (last && last.s === s) last.n++;
    else runs.push({ s, n: 1 });
  }
  return runs.map(({ s, n }) => (n > 1 ? `${s}×${n}` : `${s}`)).join(", ");
}

function round(x) {
  return Math.round(x * 10) / 10;
}
