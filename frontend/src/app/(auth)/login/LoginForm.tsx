"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { DemoIpSelector } from "@/components/auth/DemoIpSelector";
import { Alert, Badge, Button, Card, Icon, PasswordField, TextField } from "@/components/ui";
import { authApi } from "@/lib/api";
import { EMAIL_MAX_LENGTH, PASSWORD_MAX_LENGTH } from "@/lib/api/config";
import { session, useSessionStatus } from "@/lib/session";

type FieldErrors = { email?: string; password?: string };
type FormError = { tone: "error" | "block"; text: string };

/** Mensajes genéricos: nunca revelan si el email existe, el score ni las reglas. */
const MESSAGES = {
  invalid: "Credenciales inválidas",
  rateLimited: "Demasiados intentos. Intenta más tarde.",
  error: "No se pudo completar la solicitud. Revisa tu conexión e inténtalo de nuevo.",
} as const;

export function LoginForm() {
  const router = useRouter();
  const status = useSessionStatus();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<FormError | null>(null);
  const [challenge, setChallenge] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  // Si ya hay sesión en memoria (p. ej. al volver atrás), ir directo al área de usuario.
  useEffect(() => {
    if (status === "authenticated") router.replace("/me");
  }, [status, router]);

  function validate(): FieldErrors {
    const errors: FieldErrors = {};
    if (!email.trim()) errors.email = "Ingresa tu correo electrónico.";
    if (!password) errors.password = "Ingresa tu contraseña.";
    return errors;
  }

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setFormError(null);
    const errors = validate();
    setFieldErrors(errors);
    if (errors.email || errors.password) {
      document.getElementById(errors.email ? "login-email" : "login-password")?.focus();
      return;
    }

    setSubmitting(true);
    const result = await authApi.login(email.trim(), password);
    setSubmitting(false);

    switch (result.kind) {
      case "ok":
        session.start(result.token);
        router.replace("/me");
        return;
      case "challenge":
        setPassword("");
        setChallenge(true);
        return;
      case "invalid":
        setPassword("");
        setFormError({ tone: "error", text: MESSAGES.invalid });
        return;
      case "rate-limited":
        setPassword("");
        setFormError({ tone: "block", text: MESSAGES.rateLimited });
        return;
      default:
        setFormError({ tone: "error", text: MESSAGES.error });
    }
  }

  if (challenge) {
    return (
      <ChallengePending
        onBack={() => {
          setChallenge(false);
          requestAnimationFrame(() => document.getElementById("login-email")?.focus());
        }}
      />
    );
  }

  return (
    <Card className="overflow-hidden">
      <div className="h-px bg-gradient-to-r from-transparent via-acento to-transparent" aria-hidden />
      <div className="p-6 sm:p-8">
        <header className="text-center">
          <span className="mx-auto mb-4 grid h-12 w-12 place-items-center rounded-xl border border-acento/30 bg-acento/10 text-acento">
            <Icon name="lock" size={22} />
          </span>
          <h1 className="text-2xl font-semibold tracking-tight">
            Iniciar sesión en Auth<span className="text-acento">Shield</span>
          </h1>
          <p className="mt-2 text-sm text-texto-suave">Acceso protegido por evaluación de riesgo adaptativa.</p>
        </header>

        <form onSubmit={onSubmit} noValidate className="mt-6 space-y-4" aria-describedby={formError ? "login-error" : undefined}>
          <DemoIpSelector />

          <TextField
            id="login-email"
            label="Correo electrónico"
            icon="mail"
            type="email"
            name="email"
            autoComplete="username"
            inputMode="email"
            placeholder="usuario@ejemplo.com"
            maxLength={EMAIL_MAX_LENGTH}
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            error={fieldErrors.email}
          />

          <PasswordField
            id="login-password"
            label="Contraseña"
            name="password"
            autoComplete="current-password"
            maxLength={PASSWORD_MAX_LENGTH}
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            error={fieldErrors.password}
          />

          {formError && (
            <div id="login-error">
              <Alert tone={formError.tone}>{formError.text}</Alert>
            </div>
          )}

          <Button type="submit" fullWidth loading={submitting} iconEnd="arrowRight">
            {submitting ? "Verificando…" : "Iniciar sesión"}
          </Button>
        </form>
      </div>
      <p className="border-t border-borde bg-fondo/40 px-6 py-4 text-center text-sm text-texto-suave">
        ¿No tienes una cuenta?{" "}
        <Link href="/register" className="font-semibold text-acento hover:underline">
          Crear cuenta
        </Link>
      </p>
    </Card>
  );
}

/** Estado CHALLENGE (403 CHALLENGE_REQUIRED). El segundo factor (TOTP) llega en una fase posterior. */
function ChallengePending({ onBack }: { onBack: () => void }) {
  const titleRef = useRef<HTMLHeadingElement>(null);
  useEffect(() => titleRef.current?.focus(), []);

  return (
    <Card className="overflow-hidden">
      <div className="h-px bg-gradient-to-r from-transparent via-desafio to-transparent" aria-hidden />
      <div className="p-6 text-center sm:p-8">
        <span className="mx-auto mb-4 grid h-12 w-12 place-items-center rounded-xl border border-desafio/40 bg-desafio/10 text-desafio">
          <Icon name="shield" size={22} />
        </span>
        <h1 ref={titleRef} tabIndex={-1} className="text-2xl font-semibold tracking-tight focus:outline-none">
          Verificación adicional requerida
        </h1>
        <p className="mt-3 text-sm text-texto-suave">
          Por seguridad, este inicio de sesión necesita un segundo factor de verificación.
        </p>
        <div className="mt-6 text-left">
          <Alert tone="challenge" title="Verificación en dos pasos pendiente">
            <p>
              La verificación con código de una app autenticadora (TOTP) aún no está disponible. Inténtalo de nuevo más
              tarde.
            </p>
          </Alert>
        </div>
        <div className="mt-4 flex justify-center">
          <Badge tone="challenge">Pendiente</Badge>
        </div>
        <Button variant="secondary" fullWidth className="mt-6" onClick={onBack}>
          Volver al inicio de sesión
        </Button>
      </div>
    </Card>
  );
}
