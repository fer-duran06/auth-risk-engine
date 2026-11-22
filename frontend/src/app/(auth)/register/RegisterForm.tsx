"use client";

import Link from "next/link";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { DemoIpSelector } from "@/components/auth/DemoIpSelector";
import { Alert, Button, Card, cx, Icon, PasswordField, TextField } from "@/components/ui";
import { authApi } from "@/lib/api";
import { EMAIL_MAX_LENGTH, PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH } from "@/lib/api/config";

type FieldErrors = { email?: string; password?: string; confirm?: string };

/** Mismo texto que devuelve el backend con 202: no revela si el email ya estaba registrado. */
const NEUTRAL_MESSAGE = "Si los datos son válidos, la cuenta quedó registrada. Ya puedes iniciar sesión.";

const EMAIL_FORMAT = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** Longitud en unidades UTF-16, igual que String.length en el backend (Kotlin). */
const lengthOk = (p: string) => p.length >= PASSWORD_MIN_LENGTH && p.length <= PASSWORD_MAX_LENGTH;

export function RegisterForm() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  function validate(): FieldErrors {
    const errors: FieldErrors = {};
    const e = email.trim();
    if (!e) errors.email = "Ingresa tu correo electrónico.";
    else if (e.length > EMAIL_MAX_LENGTH || !EMAIL_FORMAT.test(e)) errors.email = "Escribe un correo electrónico válido.";
    if (!password) errors.password = "Ingresa una contraseña.";
    else if (!lengthOk(password))
      errors.password = `La contraseña debe tener entre ${PASSWORD_MIN_LENGTH} y ${PASSWORD_MAX_LENGTH} caracteres.`;
    if (!confirm) errors.confirm = "Repite la contraseña.";
    else if (confirm !== password) errors.confirm = "Las contraseñas no coinciden.";
    return errors;
  }

  async function onSubmit(ev: FormEvent<HTMLFormElement>) {
    ev.preventDefault();
    setFormError(null);
    const errors = validate();
    setFieldErrors(errors);
    const firstInvalid = (["email", "password", "confirm"] as const).find((k) => errors[k]);
    if (firstInvalid) {
      document.getElementById(`register-${firstInvalid}`)?.focus();
      return;
    }

    setSubmitting(true);
    const result = await authApi.register(email.trim(), password);
    setSubmitting(false);

    switch (result.kind) {
      case "accepted":
        // No se conserva la contraseña en memoria más de lo necesario.
        setPassword("");
        setConfirm("");
        setDone(true);
        return;
      case "invalid":
        setFormError("Revisa el correo y la contraseña e inténtalo de nuevo.");
        return;
      case "rate-limited":
        setFormError("Demasiados intentos. Intenta más tarde.");
        return;
      default:
        setFormError("No se pudo completar la solicitud. Revisa tu conexión e inténtalo de nuevo.");
    }
  }

  if (done) return <RegisterDone />;

  return (
    <Card className="overflow-hidden">
      <div className="h-px bg-gradient-to-r from-transparent via-acento to-transparent" aria-hidden />
      <div className="grid gap-8 p-6 sm:p-8 lg:grid-cols-[1fr_20rem]">
        <section aria-labelledby="register-title">
          <h1 id="register-title" className="text-3xl font-semibold tracking-tight">
            Crear cuenta
          </h1>
          <p className="mt-2 text-sm text-texto-suave">
            Protege tus accesos con autenticación adaptativa: cada inicio de sesión se evalúa según su riesgo.
          </p>

          <form onSubmit={onSubmit} noValidate className="mt-6 space-y-4">
            <DemoIpSelector />

            <TextField
              id="register-email"
              label="Correo electrónico"
              icon="mail"
              type="email"
              name="email"
              autoComplete="email"
              inputMode="email"
              placeholder="usuario@ejemplo.com"
              maxLength={EMAIL_MAX_LENGTH}
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              error={fieldErrors.email}
            />

            <PasswordField
              id="register-password"
              label="Contraseña"
              icon="lock"
              name="new-password"
              autoComplete="new-password"
              maxLength={PASSWORD_MAX_LENGTH}
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              error={fieldErrors.password}
              hint={`Entre ${PASSWORD_MIN_LENGTH} y ${PASSWORD_MAX_LENGTH} caracteres. Una frase larga es más segura y fácil de recordar.`}
            />

            <PasswordField
              id="register-confirm"
              label="Confirmar contraseña"
              icon="lock"
              name="confirm-password"
              autoComplete="new-password"
              maxLength={PASSWORD_MAX_LENGTH}
              required
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              error={fieldErrors.confirm}
            />

            {formError && <Alert tone="error">{formError}</Alert>}

            <Button type="submit" fullWidth loading={submitting} icon="user">
              {submitting ? "Creando cuenta…" : "Crear cuenta"}
            </Button>

            <p className="text-center text-sm text-texto-suave">
              ¿Ya tienes una cuenta?{" "}
              <Link href="/login" className="font-semibold text-acento hover:underline">
                Iniciar sesión
              </Link>
            </p>
          </form>
        </section>

        <PasswordPolicy password={password} confirm={confirm} />
      </div>
    </Card>
  );
}

/**
 * Requisitos de la contraseña, evaluados en vivo. La comprobación contra filtraciones (HIBP)
 * se añadirá aquí en su fase; entonces el botón de registro se deshabilitará si está filtrada.
 */
function PasswordPolicy({ password, confirm }: { password: string; confirm: string }) {
  const items = [
    { label: `Entre ${PASSWORD_MIN_LENGTH} y ${PASSWORD_MAX_LENGTH} caracteres`, ok: lengthOk(password) },
    { label: "Ambas contraseñas coinciden", ok: confirm !== "" && confirm === password },
  ];

  return (
    <aside aria-labelledby="policy-title" className="h-fit rounded-xl border border-borde bg-fondo/50 p-5">
      <h2 id="policy-title" className="flex items-center gap-2 text-sm font-semibold">
        <Icon name="shieldCheck" className="text-acento" />
        Requisitos de la contraseña
      </h2>
      <ul className="mt-4 space-y-2">
        {items.map(({ label, ok }) => (
          <li
            key={label}
            className={cx(
              "flex items-center gap-2.5 rounded-lg border px-3 py-2 text-sm",
              ok ? "border-permitido/30 text-texto" : "border-borde text-texto-suave",
            )}
          >
            <span
              className={cx(
                "grid h-5 w-5 shrink-0 place-items-center rounded",
                ok ? "bg-permitido/15 text-permitido" : "bg-white/5 text-texto-suave",
              )}
              aria-hidden
            >
              <Icon name={ok ? "check" : "x"} size={14} />
            </span>
            {label}
            <span className="sr-only">{ok ? ": cumplido" : ": pendiente"}</span>
          </li>
        ))}
      </ul>
      <p className="mt-4 text-xs leading-relaxed text-texto-suave">
        La contraseña se hashea con Argon2id antes de guardarse; nunca se almacena en texto plano.
      </p>
    </aside>
  );
}

/** Confirmación neutra: idéntica exista o no la cuenta. */
function RegisterDone() {
  const ref = useRef<HTMLHeadingElement>(null);
  useEffect(() => ref.current?.focus(), []);

  return (
    <Card className="mx-auto max-w-md overflow-hidden">
      <div className="h-px bg-gradient-to-r from-transparent via-permitido to-transparent" aria-hidden />
      <div className="p-6 text-center sm:p-8">
        <span className="mx-auto mb-4 grid h-12 w-12 place-items-center rounded-xl border border-permitido/40 bg-permitido/10 text-permitido">
          <Icon name="check" size={22} />
        </span>
        <h1 ref={ref} tabIndex={-1} className="text-2xl font-semibold tracking-tight focus:outline-none">
          Solicitud recibida
        </h1>
        <p role="status" className="mt-3 text-sm text-texto-suave">
          {NEUTRAL_MESSAGE}
        </p>
        <Link
          href="/login"
          className="mt-6 inline-flex w-full items-center justify-center gap-2 rounded-lg bg-acento px-4 py-2.5 text-sm font-semibold text-fondo hover:bg-acento/90"
        >
          Ir a iniciar sesión
          <Icon name="arrowRight" />
        </Link>
      </div>
    </Card>
  );
}
