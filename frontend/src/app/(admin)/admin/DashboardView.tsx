"use client";

import Link from "next/link";
import { AdminHeader, LoadError, LoadingCard } from "@/components/admin/AdminPage";
import { Button, Card, cx, Icon, type IconName } from "@/components/ui";
import { adminApi, type AdminStats } from "@/lib/api";
import { barWidth, formatCount, formatDateTime, ZONE_LABELS, zoneShares } from "@/lib/admin/format";
import { useAdminData } from "@/lib/admin/useAdminData";

const ZONE_BAR = { ALLOW: "bg-permitido", CHALLENGE: "bg-desafio", BLOCK: "bg-bloqueo" } as const;
const ZONE_TEXT = { ALLOW: "text-permitido", CHALLENGE: "text-desafio", BLOCK: "text-bloqueo" } as const;

/** Resumen de las últimas 24 h con datos de GET /api/admin/stats. */
export function DashboardView() {
  const { state, reload } = useAdminData((token) => adminApi.stats(token));

  return (
    <div className="space-y-6">
      <AdminHeader
        kicker="Últimas 24 horas"
        title="Dashboard de riesgo"
        subtitle="Intentos de inicio de sesión y decisiones del motor de riesgo."
        actions={
          <Button variant="secondary" icon="refresh" onClick={() => void reload()}>
            Actualizar
          </Button>
        }
      />
      {state.kind === "loading" && <LoadingCard label="Cargando resumen…" />}
      {(state.kind === "error" || state.kind === "invalid" || state.kind === "forbidden") && <LoadError onRetry={() => void reload()} />}
      {state.kind === "ok" && <Dashboard stats={state.data} />}
    </div>
  );
}

function Dashboard({ stats }: { stats: AdminStats }) {
  const shares = zoneShares(stats.byZone);
  const pct = (n: number) => (stats.loginAttempts > 0 ? `${Math.round((n / stats.loginAttempts) * 100)} %` : "—");
  const maxIp = stats.topIps[0]?.attempts ?? 0;

  return (
    <>
      <p className="text-xs text-texto-suave">
        Del <time dateTime={stats.since}>{formatDateTime(stats.since)}</time> al{" "}
        <time dateTime={stats.until}>{formatDateTime(stats.until)}</time>
      </p>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Kpi icon="list" label="Intentos de login" value={stats.loginAttempts} />
        <Kpi icon="check" label="Permitidos" value={stats.byZone.allow} detail={pct(stats.byZone.allow)} tone="text-permitido" />
        <Kpi icon="shield" label="Desafíos 2FA" value={stats.byZone.challenge} detail={pct(stats.byZone.challenge)} tone="text-desafio" />
        <Kpi icon="lock" label="Bloqueados" value={stats.byZone.block} detail={pct(stats.byZone.block)} tone="text-bloqueo" />
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card className="p-6">
          <h2 className="text-lg font-semibold">Distribución de decisiones</h2>
          <p className="text-sm text-texto-suave">Zona asignada a cada intento de login.</p>
          {stats.loginAttempts === 0 ? (
            <p className="mt-6 text-sm text-texto-suave">Sin intentos en este periodo.</p>
          ) : (
            <>
              <div className="mt-6 flex h-4 overflow-hidden rounded-full bg-fondo" role="img" aria-label={shares.map((s) => `${ZONE_LABELS[s.zone]} ${s.percent} %`).join(", ")}>
                {shares.map((s) => (
                  <div key={s.zone} className={ZONE_BAR[s.zone]} style={{ width: `${s.percent}%` }} />
                ))}
              </div>
              <ul className="mt-4 space-y-2 text-sm">
                {shares.map((s) => (
                  <li key={s.zone} className="flex items-center justify-between gap-4">
                    <span className="flex items-center gap-2">
                      <span className={cx("h-2.5 w-2.5 rounded-full", ZONE_BAR[s.zone])} aria-hidden />
                      {ZONE_LABELS[s.zone]}
                    </span>
                    <span className="font-mono tabular-nums">
                      {formatCount(s.count)} <span className={ZONE_TEXT[s.zone]}>{s.percent} %</span>
                    </span>
                  </li>
                ))}
              </ul>
            </>
          )}
        </Card>

        <Card className="p-6">
          <h2 className="text-lg font-semibold">Sesiones y verificación en dos pasos</h2>
          <dl className="mt-4 grid grid-cols-2 gap-3">
            <Metric label="Sesiones emitidas" value={stats.sessionsIssued} />
            <Metric label="Logins sin tokens" value={stats.loginWithoutTokens} />
            <Metric label="Verificaciones 2FA correctas" value={stats.totpSuccesses} />
            <Metric label="Verificaciones 2FA fallidas" value={stats.totpFailures} />
          </dl>
          <p className="mt-4 text-xs text-texto-suave">
            Las sesiones incluyen los logins en zona permitida y los desafíos completados con un código TOTP.
          </p>
        </Card>
      </div>

      <Card className="p-6">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-lg font-semibold">IPs con más intentos</h2>
          <Link href="/admin/ips" className="text-sm text-acento hover:underline">
            Ver todas
          </Link>
        </div>
        {stats.topIps.length === 0 ? (
          <p className="mt-4 text-sm text-texto-suave">Sin intentos en este periodo.</p>
        ) : (
          <ol className="mt-4 space-y-3">
            {stats.topIps.map((t) => (
              <li key={t.ip} className="grid grid-cols-[minmax(0,9rem)_1fr_auto] items-center gap-3 text-sm sm:grid-cols-[12rem_1fr_auto]">
                <Link href={`/admin/events?ip=${encodeURIComponent(t.ip)}`} className="truncate font-mono text-acento hover:underline">
                  {t.ip}
                </Link>
                <span className="h-2 rounded-full bg-fondo" aria-hidden>
                  <span className="block h-2 rounded-full bg-acento/70" style={{ width: `${barWidth(t.attempts, maxIp)}%` }} />
                </span>
                <span className="font-mono tabular-nums">{formatCount(t.attempts)}</span>
              </li>
            ))}
          </ol>
        )}
      </Card>
    </>
  );
}

function Kpi({ icon, label, value, detail, tone }: { icon: IconName; label: string; value: number; detail?: string; tone?: string }) {
  return (
    <Card className="p-5">
      <div className="flex items-center justify-between text-xs font-semibold tracking-wide text-texto-suave uppercase">
        {label}
        <Icon name={icon} size={16} className={tone ?? "text-acento"} />
      </div>
      <p className="mt-2 font-mono text-3xl font-semibold tabular-nums">{formatCount(value)}</p>
      {detail && <p className={cx("mt-1 font-mono text-xs", tone)}>{detail} del total</p>}
    </Card>
  );
}

function Metric({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-lg border border-borde bg-fondo/50 px-4 py-3">
      <dt className="text-xs text-texto-suave">{label}</dt>
      <dd className="mt-1 font-mono text-xl tabular-nums">{formatCount(value)}</dd>
    </div>
  );
}
