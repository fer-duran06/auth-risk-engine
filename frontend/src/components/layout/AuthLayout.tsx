import type { ReactNode } from "react";
import { cx, Icon } from "@/components/ui";

interface AuthLayoutProps {
  children: ReactNode;
  /** md: tarjeta simple (login, 2FA); lg: dos columnas (registro, activar 2FA). */
  width?: "md" | "lg";
}

/**
 * Marco de las pantallas públicas de autenticación: fondo oscuro con halos de color
 * y contenido centrado. Se reutiliza en login, registro y en las vistas 2FA de fases posteriores.
 */
export function AuthLayout({ children, width = "md" }: AuthLayoutProps) {
  return (
    <div className="relative flex min-h-screen flex-col items-center justify-center overflow-hidden px-4 py-10">
      {/* Halos decorativos (azul arriba a la izquierda, verde abajo a la derecha). */}
      <div aria-hidden className="pointer-events-none absolute inset-0">
        <div className="absolute -top-32 -left-32 h-[28rem] w-[28rem] rounded-full bg-acento/10 blur-3xl" />
        <div className="absolute -right-32 -bottom-32 h-[24rem] w-[24rem] rounded-full bg-permitido/5 blur-3xl" />
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_1px_1px,rgba(255,255,255,0.04)_1px,transparent_0)] bg-[size:24px_24px]" />
      </div>

      <main className={cx("relative w-full", width === "md" ? "max-w-md" : "max-w-5xl")}>{children}</main>

      <footer className="relative mt-6 flex items-center gap-2 rounded-full border border-borde bg-tarjeta/70 px-4 py-1.5 text-xs text-texto-suave">
        <Icon name="shieldCheck" size={14} className="text-permitido" />
        Cada inicio de sesión se evalúa según su riesgo
      </footer>
    </div>
  );
}
