"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState, type ReactNode } from "react";
import { Button, cx, Logo } from "@/components/ui";
import { session } from "@/lib/session";

/** Enlaces del área de usuario. Las vistas de 2FA y dispositivos se añadirán en sus fases. */
const NAV = [{ href: "/me", label: "Mi cuenta" }] as const;

/**
 * Marco del área autenticada de usuario: barra superior con marca, navegación y cierre de sesión.
 * El área de administración tendrá su propio layout con barra lateral.
 */
export function AppLayout({ children }: { children: ReactNode }) {
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
    <div className="min-h-screen">
      <header className="border-b border-borde bg-tarjeta/80 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-3 sm:px-6">
          <Link href="/me" aria-label="AuthShield, ir a mi cuenta" className="rounded-lg">
            <Logo />
          </Link>
          <nav aria-label="Principal" className="hidden sm:block">
            <ul className="flex items-center gap-1">
              {NAV.map(({ href, label }) => {
                const active = pathname === href;
                return (
                  <li key={href}>
                    <Link
                      href={href}
                      aria-current={active ? "page" : undefined}
                      className={cx(
                        "rounded-md px-3 py-2 text-sm transition-colors",
                        active ? "text-acento" : "text-texto-suave hover:text-texto",
                      )}
                    >
                      {label}
                    </Link>
                  </li>
                );
              })}
            </ul>
          </nav>
          <Button variant="danger" icon="logout" onClick={handleLogout} loading={leaving}>
            Cerrar sesión
          </Button>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-8 sm:px-6">{children}</main>
    </div>
  );
}
