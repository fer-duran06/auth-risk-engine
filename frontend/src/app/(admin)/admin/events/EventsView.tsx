"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState, type FormEvent } from "react";
import { AdminHeader, LoadError, LoadingCard } from "@/components/admin/AdminPage";
import { Score, ZoneBadge } from "@/components/admin/ZoneBadge";
import { Alert, Badge, Button, Card, cx, TextField } from "@/components/ui";
import { adminApi, ZONES, type AdminEvent, type EventPage, type Zone } from "@/lib/api";
import { eventsQuery } from "@/lib/api/admin";
import { parseEventFilter, PAGE_SIZES } from "@/lib/admin/filters";
import { formatCount, formatDateTime, ruleLabel, ZONE_LABELS } from "@/lib/admin/format";
import { useAdminData } from "@/lib/admin/useAdminData";

type Filter = ReturnType<typeof parseEventFilter>;

const href = (f: Filter) => `/admin/events${eventsQuery({ ...f, page: f.page || undefined, size: f.size === 25 ? undefined : f.size })}`;

/** Historial de login_attempts con filtros y paginación. Los filtros viven en la URL. */
export function EventsView() {
  const params = useSearchParams();
  const filter = parseEventFilter(params);
  const key = params.toString();
  const { state, reload } = useAdminData((token) => adminApi.events(token, filter), key);

  return (
    <div className="space-y-6">
      <AdminHeader
        kicker="Registro de intentos"
        title="Eventos de login"
        subtitle="Cada intento con su zona, score y reglas disparadas. Más recientes primero."
        actions={
          <Button variant="secondary" icon="refresh" onClick={() => void reload()}>
            Actualizar
          </Button>
        }
      />
      {/* key: al cambiar la URL (filtros, enlaces), el formulario vuelve a partir de ella. */}
      <Filters key={key} filter={filter} />
      {state.kind === "loading" && <LoadingCard label="Cargando eventos…" />}
      {state.kind === "invalid" && <Alert tone="error">Los filtros no son válidos.</Alert>}
      {(state.kind === "error" || state.kind === "forbidden") && <LoadError onRetry={() => void reload()} />}
      {state.kind === "ok" && <EventsTable page={state.data} filter={filter} />}
    </div>
  );
}

function Filters({ filter }: { filter: Filter }) {
  const router = useRouter();
  const [zone, setZone] = useState<Zone | "">(filter.zone ?? "");
  const [ip, setIp] = useState(filter.ip);
  const [size, setSize] = useState(filter.size);

  function apply(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    router.push(href({ zone: zone || null, ip, page: 0, size }));
  }

  const selectClass =
    "w-full rounded-lg border border-borde bg-fondo px-3 py-2.5 text-sm text-texto focus:border-acento focus:ring-2 focus:ring-acento/30 focus:outline-none";

  return (
    <Card className="p-4">
      <form onSubmit={apply} className="grid gap-4 sm:grid-cols-[10rem_1fr_8rem_auto] sm:items-end">
        <div>
          <label htmlFor="filter-zone" className="mb-1.5 block text-xs font-semibold tracking-wide uppercase">
            Zona
          </label>
          <select id="filter-zone" value={zone} onChange={(e) => setZone(e.target.value as Zone | "")} className={selectClass}>
            <option value="">Todas</option>
            {ZONES.map((z) => (
              <option key={z} value={z}>
                {ZONE_LABELS[z]}
              </option>
            ))}
          </select>
        </div>
        <TextField
          id="filter-ip"
          label="Dirección IP"
          icon="globe"
          placeholder="Exacta, p. ej. 198.51.100.10"
          maxLength={45}
          value={ip}
          onChange={(e) => setIp(e.target.value)}
          className="[&_input]:font-mono"
        />
        <div>
          <label htmlFor="filter-size" className="mb-1.5 block text-xs font-semibold tracking-wide uppercase">
            Por página
          </label>
          <select id="filter-size" value={size} onChange={(e) => setSize(Number(e.target.value))} className={selectClass}>
            {PAGE_SIZES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </div>
        <div className="flex gap-2">
          <Button type="submit">Filtrar</Button>
          {(filter.zone || filter.ip) && (
            <Button variant="ghost" onClick={() => router.push("/admin/events")}>
              Limpiar
            </Button>
          )}
        </div>
      </form>
    </Card>
  );
}

function EventsTable({ page, filter }: { page: EventPage; filter: Filter }) {
  if (page.total === 0) {
    return <Card className="p-6 text-sm text-texto-suave">No hay eventos con estos filtros.</Card>;
  }
  const from = page.page * page.size + 1;
  const to = page.page * page.size + page.items.length;

  return (
    <Card className="overflow-hidden">
      <div className="overflow-x-auto">
        <table className="w-full min-w-[56rem] text-left text-sm">
          <caption className="sr-only">Eventos de login</caption>
          <thead className="border-b border-borde bg-fondo/40 text-xs tracking-wide text-texto-suave uppercase">
            <tr>
              <th scope="col" className="px-4 py-3 font-semibold">Fecha</th>
              <th scope="col" className="px-4 py-3 font-semibold">Email</th>
              <th scope="col" className="px-4 py-3 font-semibold">IP</th>
              <th scope="col" className="px-4 py-3 font-semibold">Paso</th>
              <th scope="col" className="px-4 py-3 font-semibold">Zona</th>
              <th scope="col" className="px-4 py-3 font-semibold">Score</th>
              <th scope="col" className="px-4 py-3 font-semibold">Reglas</th>
              <th scope="col" className="px-4 py-3 font-semibold">Resultado</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-borde">
            {page.items.map((e) => (
              <EventRow key={e.id} event={e} />
            ))}
          </tbody>
        </table>
      </div>
      {page.items.length === 0 ? (
        <p className="border-t border-borde px-4 py-3 text-sm text-texto-suave">Esta página no tiene eventos.</p>
      ) : null}
      <nav aria-label="Paginación" className="flex flex-wrap items-center justify-between gap-3 border-t border-borde px-4 py-3 text-sm">
        <span className="text-texto-suave">
          {page.items.length > 0 ? `${formatCount(from)}–${formatCount(to)} de ${formatCount(page.total)}` : `${formatCount(page.total)} eventos`}
          {" · "}página {page.page + 1} de {formatCount(Math.max(page.totalPages, 1))}
        </span>
        <div className="flex gap-2">
          <PageLink disabled={page.page === 0} to={href({ ...filter, page: page.page - 1 })}>
            Anterior
          </PageLink>
          <PageLink disabled={page.page + 1 >= page.totalPages} to={href({ ...filter, page: page.page + 1 })}>
            Siguiente
          </PageLink>
        </div>
      </nav>
    </Card>
  );
}

function EventRow({ event: e }: { event: AdminEvent }) {
  return (
    <tr className="align-top hover:bg-white/[0.02]">
      <td className="px-4 py-3 font-mono text-xs whitespace-nowrap text-texto-suave">
        <time dateTime={e.createdAt}>{formatDateTime(e.createdAt)}</time>
      </td>
      <td className="max-w-[14rem] truncate px-4 py-3" title={e.email}>
        {e.email || <span className="text-texto-suave">—</span>}
      </td>
      <td className="px-4 py-3 font-mono text-xs">
        <Link href={`/admin/events?ip=${encodeURIComponent(e.ip)}`} className="text-acento hover:underline">
          {e.ip}
        </Link>
      </td>
      <td className="px-4 py-3">
        <Badge>{e.stage === "TOTP" ? "2FA" : "Contraseña"}</Badge>
      </td>
      <td className="px-4 py-3">
        <ZoneBadge zone={e.zone} />
      </td>
      <td className="px-4 py-3 text-xs">
        <Score value={e.score} />
      </td>
      <td className="px-4 py-3">
        {e.triggeredRules.length === 0 ? (
          <span className="text-texto-suave">—</span>
        ) : (
          <ul className="flex flex-wrap gap-1">
            {e.triggeredRules.map((r) => (
              <li key={r} className="rounded border border-borde bg-fondo px-1.5 py-0.5 text-[11px] text-texto-suave" title={r}>
                {ruleLabel(r)}
              </li>
            ))}
          </ul>
        )}
      </td>
      <td className={cx("px-4 py-3 text-xs font-semibold", e.success ? "text-permitido" : "text-texto-suave")}>
        {e.success ? "Sesión emitida" : "Sin tokens"}
      </td>
    </tr>
  );
}

function PageLink({ disabled, to, children }: { disabled: boolean; to: string; children: string }) {
  const cls = "rounded-lg border border-borde px-3 py-1.5 font-semibold";
  if (disabled) {
    return (
      <span aria-disabled="true" className={cx(cls, "cursor-not-allowed opacity-40")}>
        {children}
      </span>
    );
  }
  return (
    <Link href={to} className={cx(cls, "hover:border-acento/60 hover:text-acento")}>
      {children}
    </Link>
  );
}
