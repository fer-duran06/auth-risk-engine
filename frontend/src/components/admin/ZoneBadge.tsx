import { Badge } from "@/components/ui";
import type { Zone } from "@/lib/api";
import { ZONE_LABELS } from "@/lib/admin/format";

const TONES = { ALLOW: "success", CHALLENGE: "challenge", BLOCK: "block" } as const;

/** Zona del motor con su color (verde, naranja, rojo). */
export function ZoneBadge({ zone }: { zone: Zone }) {
  return <Badge tone={TONES[zone]}>{ZONE_LABELS[zone]}</Badge>;
}

/** Score en monoespaciada; "—" cuando no hubo score (fail-safe o desafío desconocido). */
export function Score({ value }: { value: number | null }) {
  return <span className="font-mono tabular-nums">{value === null ? "—" : `${value}/100`}</span>;
}
