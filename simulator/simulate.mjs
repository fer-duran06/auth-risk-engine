#!/usr/bin/env node
/**
 * Simulador académico de credential stuffing contra el backend LOCAL de AuthShield.
 * Solo ataca localhost/127.0.0.1, con cuentas y credenciales sintéticas que él mismo registra.
 *
 *   node simulate.mjs run --mode protegido|sin-proteccion [--base-url URL] [--rate N] [--seed N]
 *   node simulate.mjs compare
 */

import { mkdir, readdir, readFile, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { parseArgs } from "node:util";
import { createClient } from "./src/client.mjs";
import { buildDataset } from "./src/dataset.mjs";
import { checkMode, summarize } from "./src/metrics.mjs";
import { compareMarkdown, consoleTable, runMarkdown } from "./src/report.mjs";
import { SCENARIOS } from "./src/scenarios.mjs";
import { validateBaseUrl } from "./src/target.mjs";

const HERE = dirname(fileURLToPath(import.meta.url));
const RESULTS_DIR = join(HERE, "results");
const MODES = ["protegido", "sin-proteccion"];
const DEFAULT_SEED = 20261122;

const USAGE = `Uso:
  node simulate.mjs run --mode protegido|sin-proteccion [--base-url http://localhost:8080] [--rate 5] [--seed N]
  node simulate.mjs compare`;

async function main() {
  const { positionals, values } = parseArgs({
    allowPositionals: true,
    options: {
      mode: { type: "string" },
      "base-url": { type: "string", default: "http://localhost:8080" },
      rate: { type: "string", default: "5" },
      seed: { type: "string", default: String(DEFAULT_SEED) },
      help: { type: "boolean", short: "h" },
    },
  });
  const command = positionals[0];
  if (values.help || !command) return console.log(USAGE);
  if (command === "compare") return compare();
  if (command !== "run") throw new Error(`Comando desconocido: ${command}\n${USAGE}`);

  if (!MODES.includes(values.mode)) throw new Error(`--mode debe ser uno de: ${MODES.join(", ")}`);
  const baseUrl = validateBaseUrl(values["base-url"]);
  const rate = Number(values.rate);
  if (!Number.isFinite(rate) || rate <= 0 || rate > 50) throw new Error("--rate debe ser un número entre 0 y 50");
  const seed = Number.parseInt(values.seed, 10);
  if (!Number.isSafeInteger(seed)) throw new Error("--seed debe ser un entero");

  await run({ mode: values.mode, baseUrl, rate, seed });
}

async function run({ mode, baseUrl, rate, seed }) {
  const client = createClient({ baseUrl, rate });
  if (!(await client.health())) throw new Error(`El backend no responde en ${baseUrl}/actuator/health`);

  const startedAt = new Date().toISOString();
  const runId = Date.now().toString(36).slice(-6);
  const data = buildDataset({ seed, runId });
  console.log(`AuthShield · simulación ${mode} · ${baseUrl} · ${rate} req/s · runId ${runId} · semilla ${seed}`);

  // Preparación: registro de las cuentas víctima (contraseñas conocidas por el simulador).
  process.stdout.write(`Registrando ${data.accountsToRegister.length} cuentas sintéticas… `);
  let registered = 0;
  for (const acc of data.accountsToRegister) {
    const r = await client.register(acc.email, acc.password);
    if (r.status === 202) registered++;
  }
  console.log(`${registered} aceptadas`);
  if (registered !== data.accountsToRegister.length) throw new Error("No se pudieron registrar todas las cuentas");

  const results = [];
  for (const scenario of SCENARIOS) {
    process.stdout.write(`  ${scenario.id}) ${scenario.name}… `);
    const t0 = performance.now();
    const attempts = await scenario.run(client, data);
    const summary = { ...summarize(scenario, attempts, Math.round(performance.now() - t0)), description: scenario.description };
    results.push(summary);
    console.log(`${summary.attempts} intentos, ${summary.stopped} frenados`);
  }

  const warnings = checkMode(mode, results);
  const report = { tool: "authshield-simulator", mode, baseUrl, rate, seed, runId, startedAt, registered, results, warnings };

  console.log(`\n${consoleTable(results)}\n`);
  warnings.forEach((w) => console.warn(`⚠️  ${w}`));

  await mkdir(RESULTS_DIR, { recursive: true });
  const stamp = startedAt.replace(/[:.]/g, "-");
  const base = join(RESULTS_DIR, `${stamp}-${mode}`);
  await writeFile(`${base}.json`, `${JSON.stringify(report, null, 2)}\n`);
  await writeFile(`${base}.md`, runMarkdown(report));
  console.log(`Reportes: ${base}.json y .md`);
}

/** Toma la corrida más reciente de cada modo y genera results/comparacion.md. */
async function compare() {
  const files = (await readdir(RESULTS_DIR).catch(() => [])).filter((f) => f.endsWith(".json")).sort();
  const latest = {};
  for (const f of files) {
    const run = JSON.parse(await readFile(join(RESULTS_DIR, f), "utf8"));
    if (run.tool === "authshield-simulator" && MODES.includes(run.mode)) latest[run.mode] = run;
  }
  if (!latest.protegido || !latest["sin-proteccion"]) {
    throw new Error("Hace falta al menos una corrida de cada modo en simulator/results/");
  }
  const md = compareMarkdown(latest["sin-proteccion"], latest.protegido);
  const out = join(RESULTS_DIR, "comparacion.md");
  await writeFile(out, md);
  console.log(md);
  console.log(`\nComparación guardada en ${out}`);
}

main().catch((err) => {
  console.error(`Error: ${err.message}`);
  process.exitCode = 1;
});
