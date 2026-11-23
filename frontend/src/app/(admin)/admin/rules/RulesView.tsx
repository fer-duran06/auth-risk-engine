"use client";

import { AdminHeader, LoadError, LoadingCard } from "@/components/admin/AdminPage";
import { Alert, Badge, Card } from "@/components/ui";
import { adminApi, type RiskConfig } from "@/lib/api";
import { formatWindow, ruleCondition, ruleLabel, ZONE_LABELS } from "@/lib/admin/format";
import { useAdminData } from "@/lib/admin/useAdminData";

/** Configuración efectiva del motor (GET /api/admin/risk-config). Solo lectura. */
export function RulesView() {
  const { state, reload } = useAdminData((token) => adminApi.riskConfig(token));

  return (
    <div className="space-y-6">
      <AdminHeader
        kicker="Motor de riesgo"
        title="Reglas y umbrales"
        subtitle="Pesos y umbrales con los que se evalúa cada intento de login."
      />
      {state.kind === "loading" && <LoadingCard label="Cargando configuración…" />}
      {(state.kind === "error" || state.kind === "invalid" || state.kind === "forbidden") && <LoadError onRetry={() => void reload()} />}
      {state.kind === "ok" && <Rules config={state.data} />}
    </div>
  );
}

function Rules({ config }: { config: RiskConfig }) {
  const { challengeThreshold: c, blockThreshold: b, maxScore: max } = config;
  const pct = (n: number) => `${(n / max) * 100}%`;
  const totalWeight = config.rules.reduce((acc, r) => acc + r.weight, 0);

  return (
    <>
      <Alert tone="info">
        <p>
          Solo lectura. Los valores se configuran en <code className="font-mono">app.risk</code> (application.yml) y se
          aplican al reiniciar el backend.
        </p>
      </Alert>

      {!config.enabled && (
        <Alert tone="block" title="Motor de riesgo desactivado">
          <p>Todos los intentos se permiten sin evaluar. Solo debe usarse para comparar en el simulador.</p>
        </Alert>
      )}

      <Card className="p-6">
        <h2 className="text-lg font-semibold">Umbrales de decisión</h2>
        <p className="text-sm text-texto-suave">El score es la suma de los pesos de las reglas disparadas, acotada a {max}.</p>
        <div className="mt-6">
          <div className="flex h-4 overflow-hidden rounded-full" role="img" aria-label={`Permitido de 0 a ${c - 1}, desafío de ${c} a ${b - 1}, bloqueo de ${b} a ${max}`}>
            <div className="bg-permitido/70" style={{ width: pct(c) }} />
            <div className="bg-desafio/70" style={{ width: pct(b - c) }} />
            <div className="bg-bloqueo/70" style={{ width: pct(max - b) }} />
          </div>
          <div className="relative mt-1 h-4 font-mono text-[11px] text-texto-suave" aria-hidden>
            <span className="absolute left-0">0</span>
            <span className="absolute -translate-x-1/2" style={{ left: pct(c) }}>{c}</span>
            <span className="absolute -translate-x-1/2" style={{ left: pct(b) }}>{b}</span>
            <span className="absolute right-0">{max}</span>
          </div>
        </div>
        <dl className="mt-4 grid gap-3 sm:grid-cols-3">
          <Zone label={ZONE_LABELS.ALLOW} range={`< ${c}`} detail="Se emiten tokens con la contraseña." tone="success" />
          <Zone label={ZONE_LABELS.CHALLENGE} range={`${c} – ${b - 1}`} detail="Se exige un código TOTP (2FA)." tone="challenge" />
          <Zone label={ZONE_LABELS.BLOCK} range={`≥ ${b}`} detail="Se rechaza sin verificar la contraseña." tone="block" />
        </dl>
      </Card>

      <Card className="p-6">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h2 className="text-lg font-semibold">Reglas de ponderación</h2>
          <span className="text-xs text-texto-suave">
            Suma de pesos: <span className="font-mono text-texto">{totalWeight}</span>
          </span>
        </div>
        <ul className="mt-4 divide-y divide-borde">
          {config.rules.map((r) => (
            <li key={r.id} className="grid gap-2 py-4 sm:grid-cols-[1fr_auto] sm:items-center">
              <div>
                <p className="font-semibold">{ruleLabel(r.id)}</p>
                <p className="text-sm text-texto-suave">{ruleCondition(r)}</p>
                <p className="mt-1 font-mono text-[11px] text-texto-suave">
                  {r.id} · ventana {formatWindow(r.windowSeconds)}
                </p>
              </div>
              <span className="w-fit rounded-md border border-acento/40 bg-acento/10 px-2.5 py-1 font-mono text-sm text-acento">
                +{r.weight}
              </span>
            </li>
          ))}
        </ul>
      </Card>
    </>
  );
}

function Zone({ label, range, detail, tone }: { label: string; range: string; detail: string; tone: "success" | "challenge" | "block" }) {
  return (
    <div className="rounded-lg border border-borde bg-fondo/50 px-4 py-3">
      <dt className="flex items-center justify-between gap-2">
        <Badge tone={tone}>{label}</Badge>
        <span className="font-mono text-sm">{range}</span>
      </dt>
      <dd className="mt-2 text-xs text-texto-suave">{detail}</dd>
    </div>
  );
}
