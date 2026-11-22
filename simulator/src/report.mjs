/** Salida: tabla en consola, reporte Markdown por corrida y comparación entre modos. */

const MODE_LABEL = { protegido: "Protegido (motor activo)", "sin-proteccion": "Sin protección (RISK_ENABLED=false)" };
const dash = (v) => (v === null || v === undefined ? "—" : String(v));

/** Columnas comunes de la tabla de resultados. */
const COLUMNS = [
  ["Esc.", (r) => r.id],
  ["Escenario", (r) => r.name],
  ["Intentos", (r) => r.attempts],
  ["200", (r) => r.status[200]],
  ["401", (r) => r.status[401]],
  ["403", (r) => r.status[403]],
  ["429", (r) => r.status[429]],
  ["Otros", (r) => r.status.otros],
  ["1.er CHALLENGE", (r) => dash(r.firstChallenge)],
  ["1.er BLOCK", (r) => dash(r.firstBlock)],
  ["Válidas", (r) => r.validTried],
  ["Comprometidas", (r) => dash(r.compromised)],
  ["Confirmadas 403", (r) => dash(r.confirmedBy403)],
  ["Falsos +", (r) => dash(r.falsePositives)],
];

export function consoleTable(results) {
  const rows = results.map((r) => COLUMNS.map(([, f]) => String(f(r))));
  const headers = COLUMNS.map(([h]) => h);
  const widths = headers.map((h, i) => Math.max(h.length, ...rows.map((row) => row[i].length)));
  const line = (cells) => cells.map((c, i) => (i < 2 ? c.padEnd(widths[i]) : c.padStart(widths[i]))).join("  ");
  return [line(headers), widths.map((w) => "─".repeat(w)).join("  "), ...rows.map(line)].join("\n");
}

function markdownTable(results) {
  const headers = COLUMNS.map(([h]) => h);
  const align = COLUMNS.map((_, i) => (i < 2 ? "---" : "---:"));
  const rows = results.map((r) => `| ${COLUMNS.map(([, f]) => f(r)).join(" | ")} |`);
  return [`| ${headers.join(" | ")} |`, `| ${align.join(" | ")} |`, ...rows].join("\n");
}

/** Reporte Markdown de una corrida. */
export function runMarkdown(run) {
  const lines = [
    `# Simulación AuthShield — ${MODE_LABEL[run.mode] ?? run.mode}`,
    "",
    `- Fecha: ${run.startedAt}`,
    `- Objetivo: \`${run.baseUrl}\``,
    `- runId: \`${run.runId}\` · semilla: \`${run.seed}\``,
    `- Tasa pedida: ${run.rate} req/s · cuentas registradas: ${run.registered}`,
    "",
    "## Resultados",
    "",
    markdownTable(run.results),
    "",
    "Columnas: *Comprometidas* = credenciales válidas que obtuvieron tokens (200). *Confirmadas 403* = credenciales válidas",
    "que recibieron 403 CHALLENGE (sin tokens, pero el atacante sabe que son correctas). *Falsos +* = intentos legítimos",
    "frenados con 403/429. *1.er CHALLENGE / BLOCK* = número de intento dentro del escenario.",
    "",
    "## Escenarios",
    "",
    ...run.results.flatMap((r) => [
      `### ${r.id}) ${r.name}`,
      "",
      r.description,
      "",
      `- Frenados (403/429): ${r.stopped} de ${r.attempts} (${r.stoppedPct} %) · duración ${(r.durationMs / 1000).toFixed(1)} s · ${r.effectiveRate} req/s reales`,
      ...(r.kind === "attack"
        ? [`- Válidas en la lista: ${r.validTried} → comprometidas ${r.compromised}, confirmadas por 403 ${r.confirmedBy403}, bloqueadas con 429 ${r.validBlocked}`]
        : [`- Falsos positivos: ${r.falsePositives} · usuarios con contraseña correcta rechazados: ${r.legitDenied}`]),
      `- Secuencia de respuestas: \`${r.timeline}\``,
      "",
    ]),
  ];
  if (run.warnings.length) {
    lines.push("## Avisos", "", ...run.warnings.map((w) => `- ⚠️ ${w}`), "");
  }
  return lines.join("\n");
}

/** Comparación de una corrida sin protección y una protegida. */
export function compareMarkdown(unprotected, protectedRun) {
  const byId = (run) => new Map(run.results.map((r) => [r.id, r]));
  const u = byId(unprotected);
  const p = byId(protectedRun);
  const ids = protectedRun.results.map((r) => r.id);

  const metric = (label, f) => `| ${label} | ${ids.map((id) => `${dash(f(u.get(id)))} → **${dash(f(p.get(id)))}**`).join(" | ")} |`;
  const header = `| Métrica (sin protección → protegido) | ${ids.map((id) => `${id}) ${p.get(id).name}`).join(" | ")} |`;

  const sum = (run, key) => run.results.filter((r) => r.kind === "attack").reduce((acc, r) => acc + (r[key] ?? 0), 0);
  const fp = (run) => run.results.filter((r) => r.kind === "legitimate").reduce((acc, r) => acc + r.falsePositives, 0);
  const validTotal = sum(protectedRun, "validTried");

  return [
    "# Comparación: sin protección vs. protegido",
    "",
    `- Sin protección: ${unprotected.startedAt} (runId \`${unprotected.runId}\`)`,
    `- Protegido: ${protectedRun.startedAt} (runId \`${protectedRun.runId}\`)`,
    `- Misma semilla: ${unprotected.seed === protectedRun.seed ? "sí" : "no"} (\`${unprotected.seed}\` / \`${protectedRun.seed}\`)`,
    "",
    "## Resumen",
    "",
    "| Total | Sin protección | Protegido |",
    "|---|---:|---:|",
    `| Credenciales válidas probadas por los atacantes | ${sum(unprotected, "validTried")} | ${validTotal} |`,
    `| Comprometidas (200 con tokens) | ${sum(unprotected, "compromised")} | ${sum(protectedRun, "compromised")} |`,
    `| Confirmadas al atacante por 403 (sin tokens) | ${sum(unprotected, "confirmedBy403")} | ${sum(protectedRun, "confirmedBy403")} |`,
    `| Intentos de ataque frenados (403/429) | ${sum(unprotected, "stopped")} | ${sum(protectedRun, "stopped")} |`,
    `| Falsos positivos en tráfico legítimo | ${fp(unprotected)} | ${fp(protectedRun)} |`,
    "",
    "## Por escenario",
    "",
    header,
    `|---|${ids.map(() => "---:").join("|")}|`,
    metric("Intentos", (r) => r.attempts),
    metric("200", (r) => r.status[200]),
    metric("401", (r) => r.status[401]),
    metric("403", (r) => r.status[403]),
    metric("429", (r) => r.status[429]),
    metric("1.er CHALLENGE", (r) => r.firstChallenge),
    metric("1.er BLOCK", (r) => r.firstBlock),
    metric("Comprometidas", (r) => r.compromised),
    metric("Confirmadas 403", (r) => r.confirmedBy403),
    metric("Frenados %", (r) => r.stoppedPct),
    metric("Falsos positivos", (r) => r.falsePositives),
    "",
  ].join("\n");
}
