/** Indicador de carga a pantalla completa (p. ej. mientras se restaura la sesión). */
export function FullScreenStatus({ label }: { label: string }) {
  return (
    <div role="status" aria-live="polite" className="flex min-h-screen items-center justify-center gap-3 text-sm text-texto-suave">
      <span className="h-5 w-5 animate-spin rounded-full border-2 border-acento border-t-transparent" aria-hidden />
      {label}
    </div>
  );
}
