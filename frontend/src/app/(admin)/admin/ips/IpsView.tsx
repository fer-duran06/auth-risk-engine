"use client";

import Link from "next/link";
import { AdminHeader, LoadError, LoadingCard } from "@/components/admin/AdminPage";
import { Score, ZoneBadge } from "@/components/admin/ZoneBadge";
import { Button, Card } from "@/components/ui";
import { adminApi, type IpActivity } from "@/lib/api";
import { formatCount, formatDateTime } from "@/lib/admin/format";
import { useAdminData } from "@/lib/admin/useAdminData";

/** IPs con intentos de login en las últimas 24 h (GET /api/admin/ips). */
export function IpsView() {
  const { state, reload } = useAdminData((token) => adminApi.ips(token, 100));

  return (
    <div className="space-y-6">
      <AdminHeader
        kicker="Últimas 24 horas"
        title="IPs recientes"
        subtitle="Actividad por dirección IP, ordenada por su último intento."
        actions={
          <Button variant="secondary" icon="refresh" onClick={() => void reload()}>
            Actualizar
          </Button>
        }
      />
      <p className="text-xs text-texto-suave">
        La zona es la que recibió el último intento de cada IP. No hay bloqueos permanentes: cada intento se evalúa de nuevo
        con los contadores de su ventana.
      </p>
      {state.kind === "loading" && <LoadingCard label="Cargando IPs…" />}
      {(state.kind === "error" || state.kind === "invalid" || state.kind === "forbidden") && <LoadError onRetry={() => void reload()} />}
      {state.kind === "ok" && <IpsTable ips={state.data} />}
    </div>
  );
}

function IpsTable({ ips }: { ips: IpActivity[] }) {
  if (ips.length === 0) return <Card className="p-6 text-sm text-texto-suave">Sin intentos en las últimas 24 horas.</Card>;

  return (
    <Card className="overflow-hidden">
      <div className="overflow-x-auto">
        <table className="w-full min-w-[44rem] text-left text-sm">
          <caption className="sr-only">IPs con actividad reciente</caption>
          <thead className="border-b border-borde bg-fondo/40 text-xs tracking-wide text-texto-suave uppercase">
            <tr>
              <th scope="col" className="px-4 py-3 font-semibold">Dirección IP</th>
              <th scope="col" className="px-4 py-3 text-right font-semibold">Intentos</th>
              <th scope="col" className="px-4 py-3 text-right font-semibold">Sin tokens</th>
              <th scope="col" className="px-4 py-3 font-semibold">Último intento</th>
              <th scope="col" className="px-4 py-3 font-semibold">Última zona</th>
              <th scope="col" className="px-4 py-3 font-semibold">Último score</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-borde">
            {ips.map((i) => (
              <tr key={i.ip} className="hover:bg-white/[0.02]">
                <td className="px-4 py-3 font-mono text-xs">
                  <Link href={`/admin/events?ip=${encodeURIComponent(i.ip)}`} className="text-acento hover:underline">
                    {i.ip}
                  </Link>
                </td>
                <td className="px-4 py-3 text-right font-mono tabular-nums">{formatCount(i.attempts)}</td>
                <td className="px-4 py-3 text-right font-mono tabular-nums">{formatCount(i.failures)}</td>
                <td className="px-4 py-3 font-mono text-xs whitespace-nowrap text-texto-suave">
                  <time dateTime={i.lastSeen}>{formatDateTime(i.lastSeen)}</time>
                </td>
                <td className="px-4 py-3">
                  <ZoneBadge zone={i.lastZone} />
                </td>
                <td className="px-4 py-3 text-xs">
                  <Score value={i.lastScore} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="border-t border-borde px-4 py-3 text-xs text-texto-suave">
        {formatCount(ips.length)} {ips.length === 1 ? "IP" : "IPs"} (máximo 100).
      </p>
    </Card>
  );
}
