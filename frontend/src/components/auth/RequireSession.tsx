"use client";

import { useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { FullScreenStatus } from "@/components/layout/FullScreenStatus";
import { session, useSessionStatus } from "@/lib/session";

/**
 * Protege rutas en el cliente. El token vive solo en memoria y la cookie de refresh pertenece al
 * dominio del backend, así que la comprobación no puede hacerse en el servidor de Next.
 * Al cargar intenta un refresh (deduplicado); si no hay sesión, redirige a /login.
 */
export function RequireSession({ children }: { children: ReactNode }) {
  const status = useSessionStatus();
  const router = useRouter();

  useEffect(() => {
    if (status === "unknown") void session.restore();
    else if (status === "anonymous") router.replace("/login");
  }, [status, router]);

  if (status !== "authenticated") {
    return <FullScreenStatus label={status === "anonymous" ? "Redirigiendo al inicio de sesión…" : "Verificando sesión…"} />;
  }
  return <>{children}</>;
}
