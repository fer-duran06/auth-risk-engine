"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { DemoIpSelector } from "@/components/auth/DemoIpSelector";
import { Alert, Button, Card, Icon, PasswordField, TextField } from "@/components/ui";
import { authApi } from "@/lib/api";
import { EMAIL_MAX_LENGTH, PASSWORD_MAX_LENGTH } from "@/lib/api/config";
import { session, useSessionStatus } from "@/lib/session";
import { challengeStore } from "@/lib/two-factor/challenge-store";

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
        // El challengeId queda solo en memoria; la pantalla de verificación lo toma de ahí.
        setPassword("");
        challengeStore.set(result.challengeId);
        router.push("/login/verify");
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
