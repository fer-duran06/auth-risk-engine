"use client";

import { useRouter } from "next/navigation";
import { useCallback, useEffect, useLayoutEffect, useRef, useState, type FormEvent, type ReactNode, type Ref } from "react";
import { CodeInput } from "@/components/auth/CodeInput";
import { QrCode } from "@/components/auth/QrCode";
import { Alert, Badge, Button, Card, Icon } from "@/components/ui";
import { authApi, notificationsApi, type AppNotification, type TotpSetup, type TotpStatus } from "@/lib/api";
import { session } from "@/lib/session";
import { formatSecret, isCompleteCode } from "@/lib/two-factor/code";

type State = { kind: "loading" } | { kind: "error" } | { kind: "ready"; status: TotpStatus };

const dateFormat = new Intl.DateTimeFormat("es", { dateStyle: "long", timeStyle: "short" });

const MESSAGES = {
  error: "No se pudo completar la solicitud. Revisa tu conexión e inténtalo de nuevo.",
  incomplete: "Ingresa los 6 dígitos del código.",
  invalid: "El código no es válido. Comprueba la hora de tu teléfono y usa un código nuevo.",
} as const;

/** Seguridad de la cuenta: estado del 2FA y su activación (setup → confirmar con un código). */
export function SecurityView() {
  const router = useRouter();
  const [state, setState] = useState<State>({ kind: "loading" });
  const [setup, setSetup] = useState<TotpSetup | null>(null);
  const [starting, setStarting] = useState(false);
  const [startError, setStartError] = useState<string | null>(null);
  const [justEnabled, setJustEnabled] = useState(false);
  const [breachCount, setBreachCount] = useState<number | null>(null);
  const [notifications, setNotifications] = useState<AppNotification[]>([]);

  // Con cacheComponents, Next conserva esta página oculta (<Activity>) al navegar. Si el usuario
  // sale a mitad de la activación, el secreto en claro no debe quedar en memoria ni en el DOM oculto.
  useLayoutEffect(
    () => () => {
      setSetup(null);
      setStartError(null);
    },
    [],
  );

  const load = useCallback(async () => {
    setState({ kind: "loading" });
    const result = await session.withAuth((token) => authApi.totpStatus(token));
    if (result.kind === "ok") setState({ kind: "ready", status: result.status });
    else if (result.kind === "unauthorized") router.replace("/login");
    else setState({ kind: "error" });
  }, [router]);

  useEffect(() => {
    // Carga inicial de datos externos (patrón de sincronización con un sistema externo).
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load();
  }, [load]);

  useEffect(() => {
    // Independiente del estado del 2FA: se carga aunque esa consulta falle.
    void session.withAuth((token) => authApi.breachStatus(token)).then((r) => {
      if (r.kind === "ok") setBreachCount(r.status.breachCount);
    });
    void session.withAuth((token) => notificationsApi.list(token)).then((r) => {
      if (r.kind === "ok") setNotifications(r.items);
    });
  }, []);

  async function dismissNotification(id: string) {
    setNotifications((prev) => prev.filter((n) => n.id !== id));
    await session.withAuth((token) => notificationsApi.markRead(token, id));
  }

  async function startSetup() {
    setStarting(true);
    setStartError(null);
    const result = await session.withAuth((token) => authApi.totpSetup(token));
    setStarting(false);
    if (result.kind === "ok") setSetup(result.setup);
    else if (result.kind === "unauthorized") router.replace("/login");
    else if (result.kind === "already-enabled") void load();
    else setStartError(MESSAGES.error);
  }

  function onEnabled() {
    // El secreto en claro deja de estar en memoria en cuanto se confirma.
    setSetup(null);
    setJustEnabled(true);
    void load();
  }

  return (
    <div className="space-y-6">
      <header>
        <p className="font-mono text-xs tracking-wide text-acento uppercase">Área de usuario</p>
        <h1 className="mt-1 text-3xl font-semibold tracking-tight">Seguridad de la cuenta</h1>
        <p className="mt-1 text-sm text-texto-suave">Protege tu cuenta con un segundo factor de verificación.</p>
      </header>

      <AlertsCard breachCount={breachCount} notifications={notifications} onDismiss={(id) => void dismissNotification(id)} />

      {state.kind === "loading" && <SecuritySkeleton />}

      {state.kind === "error" && (
        <Alert tone="error" title="No se pudo cargar la configuración de seguridad">
          <p>Revisa tu conexión e inténtalo de nuevo.</p>
          <Button variant="secondary" className="mt-3" onClick={() => void load()}>
            Reintentar
          </Button>
        </Alert>
      )}

      {state.kind === "ready" && (
        <div className="grid gap-6 lg:grid-cols-[1fr_18rem]">
          <div className="space-y-6">
            <StatusCard
              status={state.status}
              justEnabled={justEnabled}
              setupInProgress={setup !== null}
              starting={starting}
              startError={startError}
              onStart={() => void startSetup()}
            />
            {setup && !state.status.enabled && (
              <ActivationSteps setup={setup} onEnabled={onEnabled} onCancel={() => setSetup(null)} />
            )}
          </div>
          <GoodPractices />
        </div>
      )}
    </div>
  );
}

interface StatusCardProps {
  status: TotpStatus;
  justEnabled: boolean;
  setupInProgress: boolean;
  starting: boolean;
  startError: string | null;
  onStart: () => void;
}

function StatusCard({ status, justEnabled, setupInProgress, starting, startError, onStart }: StatusCardProps) {
  const confirmed = status.confirmedAt ? new Date(status.confirmedAt) : null;
  const confirmedLabel = confirmed && !Number.isNaN(confirmed.getTime()) ? dateFormat.format(confirmed) : null;

  return (
    <Card className="p-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex items-center gap-4">
          <span className="grid h-12 w-12 shrink-0 place-items-center rounded-xl border border-acento/30 bg-acento/10 text-acento">
            <Icon name="smartphone" size={22} />
          </span>
          <div>
            <h2 className="text-lg font-semibold">Autenticación de dos factores</h2>
            <p className="text-sm text-texto-suave">Códigos TOTP (RFC 6238) de 6 dígitos que cambian cada 30 segundos.</p>
          </div>
        </div>
        {status.enabled ? (
          <Badge tone="success">
            <span className="h-1.5 w-1.5 rounded-full bg-permitido" aria-hidden />
            Activo
          </Badge>
        ) : (
          <Badge>Inactivo</Badge>
        )}
      </div>

      {status.enabled ? (
        <div className="mt-6 space-y-4">
          {justEnabled && (
            <Alert tone="success" title="Verificación en dos pasos activada">
              <p>Cuando un inicio de sesión resulte sospechoso, te pediremos un código de tu aplicación.</p>
            </Alert>
          )}
          <dl className="grid gap-3 sm:grid-cols-2">
            <Field icon="smartphone" label="Método" value="Aplicación autenticadora" />
            <Field icon="clock" label="Activado" value={confirmedLabel ?? "—"} />
          </dl>
        </div>
      ) : (
        !setupInProgress && (
          <div className="mt-6 space-y-4">
            <p className="text-sm text-texto-suave">
              Sin la verificación en dos pasos, los inicios de sesión que el sistema considere sospechosos no podrán
              completarse.
            </p>
            {startError && <Alert tone="error">{startError}</Alert>}
            <Button icon="shieldCheck" loading={starting} onClick={onStart}>
              Activar verificación en dos pasos
            </Button>
          </div>
        )
      )}
    </Card>
  );
}

function ActivationSteps({ setup, onEnabled, onCancel }: { setup: TotpSetup; onEnabled: () => void; onCancel: () => void }) {
  const router = useRouter();
  const [code, setCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [copied, setCopied] = useState(false);
  const stepRef = useRef<HTMLHeadingElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => stepRef.current?.focus(), []);

  async function copySecret() {
    try {
      await navigator.clipboard.writeText(setup.secret);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // Sin permiso de portapapeles: la clave sigue visible para copiarla a mano.
    }
  }

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!isCompleteCode(code)) {
      setError(MESSAGES.incomplete);
      inputRef.current?.focus();
      return;
    }
    setSubmitting(true);
    setError(null);
    const result = await session.withAuth((token) => authApi.totpEnable(token, code));
    setSubmitting(false);

    if (result.kind === "ok" || result.kind === "already-enabled") return onEnabled();
    if (result.kind === "unauthorized") return router.replace("/login");
    setCode("");
    setError(result.kind === "invalid" ? MESSAGES.invalid : MESSAGES.error);
    inputRef.current?.focus();
  }

  return (
    <>
      <StepCard number="01" kicker="Vincular dispositivo" title="Escanea el código QR" titleRef={stepRef}>
        <p className="text-sm text-texto-suave">
          Abre tu aplicación autenticadora (por ejemplo Google Authenticator, Microsoft Authenticator o 1Password) y
          escanea este código.
        </p>
        <div className="mt-5 grid items-center gap-6 sm:grid-cols-[auto_1fr]">
          <div className="mx-auto rounded-xl border border-borde bg-fondo p-3">
            <QrCode value={setup.otpauthUri} label="Código QR para registrar AuthShield en tu aplicación autenticadora" />
          </div>
          <div className="space-y-3">
            <p className="text-sm font-semibold">¿No puedes escanear la imagen?</p>
            <p className="text-sm text-texto-suave">Introduce manualmente esta clave en tu aplicación:</p>
            <div className="flex flex-wrap items-center gap-2">
              <code className="rounded-md border border-borde bg-fondo px-3 py-2 font-mono text-sm tracking-wider break-all text-acento">
                {formatSecret(setup.secret)}
              </code>
              <Button variant="secondary" icon={copied ? "check" : "copy"} onClick={() => void copySecret()}>
                {copied ? "Copiada" : "Copiar clave"}
              </Button>
            </div>
            <span className="sr-only" role="status">
              {copied ? "Clave copiada al portapapeles" : ""}
            </span>
            <details className="text-xs text-texto-suave">
              <summary className="cursor-pointer hover:text-texto">Ver URI otpauth</summary>
              <code className="mt-2 block rounded-md border border-borde bg-fondo px-3 py-2 font-mono break-all">
                {setup.otpauthUri}
              </code>
            </details>
            <p className="text-xs text-texto-suave">Tipo: basada en tiempo · SHA-1 · 6 dígitos · 30 segundos.</p>
          </div>
        </div>
      </StepCard>

      <StepCard number="02" kicker="Verificación temporal" title="Confirma con un primer código">
        <p className="text-sm text-texto-suave">
          Escribe el código de 6 dígitos que muestra tu aplicación para comprobar que quedó bien configurada.
        </p>
        <form onSubmit={onSubmit} noValidate className="mt-5 space-y-4">
          <CodeInput
            id="enable-code"
            label="Código de 6 dígitos"
            value={code}
            onChange={(v) => {
              setCode(v);
              if (error === MESSAGES.incomplete) setError(null);
            }}
            disabled={submitting}
            inputRef={inputRef}
          />
          {error && <Alert tone="error">{error}</Alert>}
          <div className="flex flex-wrap gap-3">
            <Button type="submit" icon="shieldCheck" loading={submitting}>
              {submitting ? "Validando…" : "Validar y activar"}
            </Button>
            <Button variant="ghost" onClick={onCancel} disabled={submitting}>
              Cancelar
            </Button>
          </div>
        </form>
      </StepCard>
    </>
  );
}

interface StepCardProps {
  number: string;
  kicker: string;
  title: string;
  children: ReactNode;
  titleRef?: Ref<HTMLHeadingElement>;
}

function StepCard({ number, kicker, title, children, titleRef }: StepCardProps) {
  return (
    <Card className="p-6">
      <div className="mb-4 flex items-center gap-3">
        <span className="grid h-9 w-9 place-items-center rounded-lg border border-acento/30 bg-acento/10 font-mono text-sm text-acento">
          {number}
        </span>
        <div>
          <p className="font-mono text-[11px] tracking-wide text-texto-suave uppercase">{kicker}</p>
          <h2 ref={titleRef} tabIndex={-1} className="text-lg font-semibold focus:outline-none">
            {title}
          </h2>
        </div>
      </div>
      {children}
    </Card>
  );
}

interface AlertsCardProps {
  breachCount: number | null;
  notifications: AppNotification[];
  onDismiss: (id: string) => void;
}

/** Avisos de seguridad: brechas conocidas del correo y notificaciones de actividad sospechosa. */
function AlertsCard({ breachCount, notifications, onDismiss }: AlertsCardProps) {
  const unread = notifications.filter((n) => !n.read);
  if (!breachCount && unread.length === 0) return null;

  return (
    <div className="space-y-3">
      {breachCount != null && breachCount > 0 && (
        <Alert tone="block" title="Tu correo aparece en filtraciones conocidas">
          <p>
            Lo encontramos en {breachCount} {breachCount === 1 ? "filtración pública" : "filtraciones públicas"} de
            datos. Si usas esta misma contraseña en otro sitio, cámbiala ahí también.
          </p>
        </Alert>
      )}
      {unread.map((n) => (
        <Alert key={n.id} tone="challenge" title="Actividad sospechosa en tu cuenta">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <p>{n.message}</p>
            <Button variant="ghost" onClick={() => onDismiss(n.id)}>
              Marcar como leída
            </Button>
          </div>
        </Alert>
      ))}
    </div>
  );
}

function GoodPractices() {
  const items = [
    "No compartas capturas de pantalla del código QR ni de la clave.",
    "Mantén la hora de tu teléfono en modo automático: los códigos dependen de ella.",
    "Cada código sirve una sola vez y caduca en segundos.",
    "Nunca te pediremos el código por correo ni por teléfono.",
  ];
  return (
    <Card className="h-fit p-6">
      <h2 className="flex items-center gap-2 text-sm font-semibold">
        <Icon name="shieldCheck" className="text-acento" />
        Buenas prácticas
      </h2>
      <ul className="mt-4 space-y-3 text-sm text-texto-suave">
        {items.map((item) => (
          <li key={item} className="flex gap-2">
            <Icon name="check" size={16} className="mt-0.5 shrink-0 text-permitido" />
            {item}
          </li>
        ))}
      </ul>
    </Card>
  );
}

function Field({ icon, label, value }: { icon: "smartphone" | "clock"; label: string; value: ReactNode }) {
  return (
    <div className="rounded-lg border border-borde bg-fondo/50 px-4 py-3">
      <dt className="flex items-center gap-2 text-xs font-semibold tracking-wide text-texto-suave uppercase">
        <Icon name={icon} size={14} />
        {label}
      </dt>
      <dd className="mt-1 text-sm">{value}</dd>
    </div>
  );
}

function SecuritySkeleton() {
  return (
    <Card className="p-6" aria-busy="true">
      <span className="sr-only" role="status">
        Cargando configuración de seguridad…
      </span>
      <div className="flex animate-pulse items-center gap-4" aria-hidden>
        <div className="h-12 w-12 rounded-xl bg-white/5" />
        <div className="space-y-2">
          <div className="h-4 w-56 rounded bg-white/5" />
          <div className="h-3 w-72 rounded bg-white/5" />
        </div>
      </div>
    </Card>
  );
}
