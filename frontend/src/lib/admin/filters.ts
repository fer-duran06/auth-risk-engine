import { ZONES, type EventFilter, type Zone } from "../api/admin";

export const PAGE_SIZES = [25, 50, 100] as const;
export const DEFAULT_PAGE_SIZE = 25;

/** Lo mínimo de URLSearchParams que se necesita (también sirve el de Next). */
interface ParamsLike {
  get(name: string): string | null;
}

/**
 * Filtros de /admin/events leídos de la URL, que es la fuente de verdad (la página se conserva
 * oculta entre navegaciones, así que no se guardan en estado). Valores inválidos se ignoran.
 */
export function parseEventFilter(params: ParamsLike): Required<Pick<EventFilter, "page" | "size">> & { zone: Zone | null; ip: string } {
  const zoneRaw = params.get("zone")?.toUpperCase() ?? "";
  const zone = (ZONES as readonly string[]).includes(zoneRaw) ? (zoneRaw as Zone) : null;
  const ip = (params.get("ip") ?? "").trim().slice(0, 45);
  const pageNum = Number(params.get("page"));
  const page = Number.isInteger(pageNum) && pageNum >= 0 && pageNum <= 10_000 ? pageNum : 0;
  const sizeNum = Number(params.get("size"));
  const size = (PAGE_SIZES as readonly number[]).includes(sizeNum) ? sizeNum : DEFAULT_PAGE_SIZE;
  return { zone, ip, page, size };
}
