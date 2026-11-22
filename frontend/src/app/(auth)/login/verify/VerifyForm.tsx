"use client";

import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { CodeInput } from "@/components/auth/CodeInput";
import { Alert, Badge, Button, Card, Icon } from "@/components/ui";
import { authApi } from "@/lib/api";
import { session } from "@/lib/session";
import { challengeStore, type PendingChallenge } from "@/lib/two-factor/challenge-store";
import { isCompleteCode } from "@/lib/two-factor/code";

/** Intentos que admite el backend por desafío (app.two-factor.max-attempts). */
const MAX_ATTEMPTS = 5;

/**
 * Mensajes genéricos: el backend responde igual ante código incorrecto, desafío vencido o
 * contraseña incorrecta, y la UI no intenta distinguirlos.
 */
const MESSAGES = {
  incomplete: "Ingresa los 6 dígitos del código.",
  invalid: "No se pudo completar la verificación. Revisa el código e inténtalo de nuevo, o vuelve a iniciar sesión.",
  error: "No se pudo completar la solicitud. Revisa tu conexión e inténtalo de nuevo.",
} as const;

function remainingSeconds(challenge: PendingChallenge | null): number {
  return challenge ? Math.max(0, Math.ceil((challenge.expiresAt - Date.now()) / 1000)) : 0;
}

function formatClock(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
}

export function VerifyForm() {
  const router = useRouter();
  // Solo existe tras un login en esta pestaña (navegación en el cliente). Al recargar no hay desafío.
  const [challenge] = useState(() => challengeStore.get());
  const [code, setCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [failures, setFailures] = useState(0);
  const [seconds, setSeconds] = useState(() => remainingSeconds(challenge));
  const titleRef = useRef<HTMLHeadingElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const expired = seconds === 0;
  const exhausted = failures >= MAX_ATTEMPTS;
  const finished = expired || exhausted;

  useEffect(() => {
    if (!challenge) {
      router.replace("/login");
      return;
    }
    titleRef.current?.focus();
    const timer = setInterval(() => setSeconds(remainingSeconds(challenge)), 1000);
    return () => clearInterval(timer);
  }, [challenge, router]);

  useEffect(() => {
    if (finished) challengeStore.clear();
  }, [finished]);

  function backToLogin() {
    challengeStore.clear();
    router.replace("/login");
  }

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!challenge || finished) return;
    if (!isCompleteCode(code)) {
      setError(MESSAGES.incomplete);
      inputRef.current?.focus();
      return;
    }

    setSubmitting(true);
    setError(null);
    const result = await authApi.verifyChallenge(challenge.id, code);
    setSubmitting(false);

    if (result.kind === "ok") {
      challengeStore.clear();
      session.start(result.token);
      router.replace("/me");
      return;
    }
    setCode("");
    if (result.kind === "invalid") {
      setFailures((n) => n + 1);
      setError(MESSAGES.invalid);
    } else {
      setError(MESSAGES.error);
    }
    inputRef.current?.focus();
  }

  if (!challenge) return null;

  return (
    <Card className="overflow-hidden">
      <div className="h-px bg-gradient-to-r from-transparent via-desafio to-transparent" aria-hidden />
      <div className="p-6 text-center sm:p-8">
        <span className="mx-auto mb-4 grid h-12 w-12 place-items-center rounded-xl border border-desafio/40 bg-desafio/10 text-desafio">
          <Icon name="shield" size={22} />
        </span>
        <Badge tone="challenge">
          <span className="h-1.5 w-1.5 rounded-full bg-desafio" aria-hidden />
          Desafío adaptativo por riesgo
        </Badge>
        <h1 ref={titleRef} tabIndex={-1} className="mt-3 text-2xl font-semibold tracking-tight focus:outline-none">
          Verificación de seguridad requerida
        </h1>
        <p className="mt-3 text-sm text-texto-suave">
          Por seguridad, este inicio de sesión necesita un segundo paso. Introduce el código temporal de 6 dígitos de tu
          aplicación autenticadora.
        </p>

        {finished ? (
          <div className="mt-6 space-y-4 text-left">
            <Alert tone="challenge" title="La verificación ya no está disponible">
              <p>Vuelve a iniciar sesión para obtener una verificación nueva.</p>
            </Alert>
            <Button fullWidth icon="arrowRight" onClick={backToLogin}>
              Volver al inicio de sesión
            </Button>
          </div>
        ) : (
          <form onSubmit={onSubmit} noValidate className="mt-6 space-y-5 text-left">
            <CodeInput
              id="verify-code"
              label="Código de verificación"
              value={code}
              onChange={(v) => {
                setCode(v);
                if (error === MESSAGES.incomplete) setError(null);
              }}
              disabled={submitting}
              inputRef={inputRef}
            />

            <div className="flex items-center justify-between gap-2 text-xs text-texto-suave">
              <span className="flex items-center gap-1.5">
                <Icon name="clock" size={14} />
                La verificación expira en:
              </span>
              <span className="font-mono text-acento" aria-live="off">
                {formatClock(seconds)}
              </span>
            </div>

            {error && <Alert tone="error">{error}</Alert>}

            <Button type="submit" fullWidth loading={submitting} icon="shieldCheck">
              {submitting ? "Verificando…" : "Verificar código"}
            </Button>
            <Button variant="secondary" fullWidth onClick={backToLogin}>
              Volver al inicio de sesión
            </Button>
          </form>
        )}

        <p className="mt-6 text-xs text-texto-suave">
          La verificación en dos pasos se activa desde <span className="text-texto">Seguridad</span> en tu cuenta. Sin
          ella, este paso no se puede completar: vuelve a intentarlo más tarde.
        </p>
      </div>
    </Card>
  );
}
