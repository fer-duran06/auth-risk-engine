"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState, type ReactNode } from "react";
import { Alert, Badge, Button, Card, Icon, type IconName } from "@/components/ui";
import { authApi, type CurrentUser } from "@/lib/api";
import { session } from "@/lib/session";

type State = { kind: "loading" } | { kind: "ok"; user: CurrentUser } | { kind: "error" };

const ROLE_LABELS: Record<string, string> = { USER: "Usuario", ADMIN: "Administrador" };

const dateFormat = new Intl.DateTimeFormat("es", { dateStyle: "long", timeStyle: "short" });

/** Datos de GET /api/auth/me. Si la sesión expira, withAuth refresca una vez antes de rendirse. */
export function MeView() {
  const router = useRouter();
  const [state, setState] = useState<State>({ kind: "loading" });

  const load = useCallback(async () => {
    setState({ kind: "loading" });
    const result = await session.withAuth((token) => authApi.me(token));
    if (result.kind === "ok") setState({ kind: "ok", user: result.user });
    else if (result.kind === "unauthorized") router.replace("/login");
    else setState({ kind: "error" });
  }, [router]);

  useEffect(() => {
    // Carga inicial de datos externos (patrón de sincronización con un sistema externo).
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load();
  }, [load]);

  return (
    <div className="space-y-6">
      <header>
        <p className="font-mono text-xs tracking-wide text-acento uppercase">Área de usuario</p>
        <h1 className="mt-1 text-3xl font-semibold tracking-tight">Mi cuenta</h1>
        <p className="mt-1 text-sm text-texto-suave">Datos de tu cuenta y de la sesión actual.</p>
      </header>

      {state.kind === "loading" && <AccountSkeleton />}

      {state.kind === "error" && (
        <Alert tone="error" title="No se pudieron cargar tus datos">
          <p>Revisa tu conexión e inténtalo de nuevo.</p>
          <Button variant="secondary" className="mt-3" onClick={() => void load()}>
            Reintentar
          </Button>
        </Alert>
      )}

      {state.kind === "ok" && <AccountCard user={state.user} />}
    </div>
  );
}

function AccountCard({ user }: { user: CurrentUser }) {
  const created = new Date(user.createdAt);
  const createdLabel = Number.isNaN(created.getTime()) ? user.createdAt : dateFormat.format(created);

  return (
    <Card className="p-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-4">
          <span className="grid h-12 w-12 place-items-center rounded-xl border border-acento/30 bg-acento/10 text-acento">
            <Icon name="user" size={22} />
          </span>
          <div className="min-w-0">
            <h2 className="truncate text-lg font-semibold">{user.email}</h2>
            <p className="text-sm text-texto-suave">Sesión iniciada</p>
          </div>
        </div>
        <Badge tone="success">
          <span className="h-1.5 w-1.5 rounded-full bg-permitido" aria-hidden />
          Sesión activa
        </Badge>
      </div>

      <dl className="mt-6 grid gap-3 sm:grid-cols-2">
        <Field icon="mail" label="Correo electrónico" value={user.email} />
        <Field icon="shieldCheck" label="Rol" value={ROLE_LABELS[user.role] ?? user.role} />
        <Field icon="clock" label="Miembro desde" value={<time dateTime={user.createdAt}>{createdLabel}</time>} />
        <Field icon="fingerprint" label="Identificador" value={user.id} mono />
      </dl>

      {/* Solo un atajo: el backend exige ROLE_ADMIN en cada endpoint del panel. */}
      {user.role === "ADMIN" && (
        <Link
          href="/admin"
          className="mt-6 inline-flex items-center gap-2 rounded-lg border border-acento/40 bg-acento/10 px-4 py-2.5 text-sm font-semibold text-acento hover:bg-acento/20"
        >
          <Icon name="chart" size={16} />
          Ir al panel de administración
        </Link>
      )}
    </Card>
  );
}

function Field({ icon, label, value, mono }: { icon: IconName; label: string; value: ReactNode; mono?: boolean }) {
  return (
    <div className="rounded-lg border border-borde bg-fondo/50 px-4 py-3">
      <dt className="flex items-center gap-2 text-xs font-semibold tracking-wide text-texto-suave uppercase">
        <Icon name={icon} size={14} />
        {label}
      </dt>
      <dd className={mono ? "mt-1 font-mono text-sm break-all" : "mt-1 text-sm break-words"}>{value}</dd>
    </div>
  );
}

function AccountSkeleton() {
  return (
    <Card className="p-6" aria-busy="true">
      <span className="sr-only" role="status">
        Cargando datos de la cuenta…
      </span>
      <div className="flex animate-pulse items-center gap-4" aria-hidden>
        <div className="h-12 w-12 rounded-xl bg-white/5" />
        <div className="space-y-2">
          <div className="h-4 w-48 rounded bg-white/5" />
          <div className="h-3 w-24 rounded bg-white/5" />
        </div>
      </div>
      <div className="mt-6 grid animate-pulse gap-3 sm:grid-cols-2" aria-hidden>
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="h-16 rounded-lg bg-white/5" />
        ))}
      </div>
    </Card>
  );
}
