"use client";

import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import type { AdminResult } from "../api/admin";
import { session } from "../session";

export type AdminData<T> =
  | { kind: "loading" }
  | { kind: "ok"; data: T }
  | { kind: "forbidden" }
  | { kind: "invalid" }
  | { kind: "error" };

/**
 * Carga datos del panel con el token en memoria (withAuth refresca una vez ante 401).
 * Sin sesión redirige al login. Vuelve a cargar cuando cambia [key] (p. ej. la query de filtros).
 */
export function useAdminData<T>(call: (token: string) => Promise<AdminResult<T>>, key = "") {
  const router = useRouter();
  const [state, setState] = useState<AdminData<T>>({ kind: "loading" });
  // Siempre la última versión de la llamada, sin que cada render dispare una carga.
  const callRef = useRef(call);
  useEffect(() => {
    callRef.current = call;
  });

  const load = useCallback(async () => {
    setState({ kind: "loading" });
    const result = await session.withAuth((token) => callRef.current(token));
    if (result.kind === "unauthorized") router.replace("/login");
    else setState(result);
  }, [router]);

  useEffect(() => {
    // Carga de datos externos al montar o al cambiar la clave.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load();
  }, [load, key]);

  return { state, reload: load };
}
