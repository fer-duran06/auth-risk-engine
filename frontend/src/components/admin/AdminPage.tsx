"use client";

import type { ReactNode } from "react";
import { Alert, Button, Card } from "@/components/ui";

/** Encabezado común de las páginas del panel. */
export function AdminHeader({ kicker, title, subtitle, actions }: { kicker: string; title: string; subtitle: string; actions?: ReactNode }) {
  return (
    <header className="flex flex-wrap items-end justify-between gap-4">
      <div>
        <p className="font-mono text-xs tracking-wide text-acento uppercase">{kicker}</p>
        <h1 className="mt-1 text-3xl font-semibold tracking-tight">{title}</h1>
        <p className="mt-1 text-sm text-texto-suave">{subtitle}</p>
      </div>
      {actions}
    </header>
  );
}

export function LoadError({ onRetry }: { onRetry: () => void }) {
  return (
    <Alert tone="error" title="No se pudieron cargar los datos">
      <p>Revisa tu conexión e inténtalo de nuevo.</p>
      <Button variant="secondary" className="mt-3" onClick={onRetry}>
        Reintentar
      </Button>
    </Alert>
  );
}

export function LoadingCard({ label }: { label: string }) {
  return (
    <Card className="p-6" aria-busy="true">
      <span className="sr-only" role="status">
        {label}
      </span>
      <div className="animate-pulse space-y-3" aria-hidden>
        <div className="h-4 w-48 rounded bg-white/5" />
        <div className="h-3 w-full rounded bg-white/5" />
        <div className="h-3 w-2/3 rounded bg-white/5" />
      </div>
    </Card>
  );
}
