"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useCallback, useEffect, useState, type ReactNode } from "react";
import { FullScreenStatus } from "@/components/layout/FullScreenStatus";
import { Alert, Button, Card, cx, Icon, Logo, type IconName } from "@/components/ui";
import { authApi } from "@/lib/api";
import { adminAccess, type AdminAccess } from "@/lib/admin/access";
import { session } from "@/lib/session";

const NAV: { href: string; label: string; icon: IconName }[] = [
  { href: "/admin", label: "Dashboard", icon: "chart" },
  { href: "/admin/events", label: "Eventos de login", icon: "list" },
  { href: "/admin/ips", label: "IPs recientes", icon: "globe" },
  { href: "/admin/rules", label: "Reglas y umbrales", icon: "sliders" },
];

/**
 * Marco del panel de administración: barra lateral y guardia por rol. La guardia solo decide
 * qué se muestra; la protección real es la del backend (403 en /api/admin sin ROLE_ADMIN).
 */
export function AdminLayout({ children }: { children: ReactNode }) {
  const router = useRouter();
  const [access, setAccess] = useState<AdminAccess | "loading">("loading");

  const check = useCallback(async () => {
    setAccess("loading");
    const result = adminAccess(await session.withAuth((token) => authApi.me(token)));
    if (result === "unauthorized") router.replace("/login");
    else setAccess(result);
  }, [router]);

  useEffect(() => {
    // Comprobación del rol al entrar (sincronización con un sistema externo).
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void check();
  }, [check]);

  if (access === "loading" || access === "unauthorized") return <FullScreenStatus label="Comprobando permisos…" />;
  if (access === "denied") return <AccessDenied />;
  if (access === "error") {
    return (
      <div className="mx-auto max-w-md px-4 py-16">
        <Alert tone="error" title="No se pudieron comprobar tus permisos">
          <p>Revisa tu conexión e inténtalo de nuevo.</p>
          <Button variant="secondary" className="mt-3" onClick={() => void check()}>
            Reintentar
          </Button>
        </Alert>
      </div>
    );
  }

  return (
    <div className="min-h-screen md:flex">
      <Sidebar />
      <main className="min-w-0 flex-1 px-4 py-8 sm:px-6 lg:px-8">{children}</main>
    </div>
  );
}

function Sidebar() {
  const pathname = usePathname();
  const router = useRouter();
  const [leaving, setLeaving] = useState(false);

  async function handleLogout() {
    setLeaving(true);
    try {
      await session.logout();
    } finally {
      router.replace("/login");
    }
  }

  return (
    <aside className="border-b border-borde bg-tarjeta/80 md:sticky md:top-0 md:flex md:h-screen md:w-64 md:shrink-0 md:flex-col md:border-r md:border-b-0">
      <div className="flex items-center justify-between gap-3 px-4 py-4">
        <Link href="/admin" aria-label="AuthShield, panel de administración" className="rounded-lg">
          <Logo />
        </Link>
        <span className="font-mono text-[10px] tracking-wide text-texto-suave uppercase md:hidden">Admin</span>
      </div>
      <p className="hidden px-4 pb-2 font-mono text-[11px] tracking-wide text-texto-suave uppercase md:block">Administración</p>
      <nav aria-label="Administración" className="px-2 pb-3 md:flex-1">
        <ul className="flex gap-1 overflow-x-auto md:flex-col">
          {NAV.map(({ href, label, icon }) => {
            const active = pathname === href;
            return (
              <li key={href} className="shrink-0">
                <Link
                  href={href}
                  aria-current={active ? "page" : undefined}
                  className={cx(
                    "flex items-center gap-2 rounded-lg px-3 py-2 text-sm transition-colors",
                    active ? "bg-acento/10 text-acento" : "text-texto-suave hover:bg-white/5 hover:text-texto",
                  )}
                >
                  <Icon name={icon} size={16} />
                  {label}
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>
      <div className="hidden space-y-2 border-t border-borde p-4 md:block">
        <Link href="/me" className="flex items-center gap-2 rounded-lg px-1 py-1 text-sm text-texto-suave hover:text-texto">
          <Icon name="user" size={16} />
          Volver a mi cuenta
        </Link>
        <Button variant="danger" icon="logout" fullWidth onClick={handleLogout} loading={leaving}>
          Cerrar sesión
        </Button>
      </div>
    </aside>
  );
}

function AccessDenied() {
  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <Card className="w-full max-w-md p-8 text-center">
        <span className="mx-auto mb-4 grid h-12 w-12 place-items-center rounded-xl border border-bloqueo/40 bg-bloqueo/10 text-bloqueo">
          <Icon name="lock" size={22} />
        </span>
        <h1 className="text-2xl font-semibold tracking-tight">Acceso denegado</h1>
        <p className="mt-3 text-sm text-texto-suave">Tu cuenta no tiene permisos de administración.</p>
        <Link
          href="/me"
          className="mt-6 inline-flex items-center justify-center gap-2 rounded-lg border border-borde bg-tarjeta px-4 py-2.5 text-sm font-semibold hover:border-acento/60 hover:text-acento"
        >
          Volver a mi cuenta
        </Link>
      </Card>
    </div>
  );
}
