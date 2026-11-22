"use client";

import { useState, type Ref } from "react";
import { cx } from "@/components/ui";
import { normalizeCode, TOTP_DIGITS } from "@/lib/two-factor/code";

interface CodeInputProps {
  id: string;
  label: string;
  value: string;
  onChange: (code: string) => void;
  error?: string;
  hint?: string;
  disabled?: boolean;
  inputRef?: Ref<HTMLInputElement>;
}

/**
 * Código de 6 dígitos. Visualmente son 6 casillas, pero hay un único input real: así funcionan
 * el pegado, el autocompletado del sistema (one-time-code) y los lectores de pantalla.
 */
export function CodeInput({ id, label, value, onChange, error, hint, disabled, inputRef }: CodeInputProps) {
  const [focused, setFocused] = useState(false);
  const errorId = error ? `${id}-error` : undefined;
  const hintId = hint ? `${id}-ayuda` : undefined;
  const active = Math.min(value.length, TOTP_DIGITS - 1);

  return (
    <div>
      <label htmlFor={id} className="mb-2 block text-xs font-semibold tracking-wide text-texto uppercase">
        {label}
      </label>
      <div className="relative mx-auto w-fit">
        <div className="flex gap-2 sm:gap-3" aria-hidden>
          {Array.from({ length: TOTP_DIGITS }, (_, i) => (
            <span
              key={i}
              className={cx(
                "grid h-12 w-10 place-items-center rounded-lg border bg-fondo font-mono text-xl font-semibold sm:h-14 sm:w-12",
                error ? "border-bloqueo/70" : "border-borde",
                focused && i === active && "border-acento ring-2 ring-acento/30",
              )}
            >
              {value[i] ?? ""}
            </span>
          ))}
        </div>
        <input
          ref={inputRef}
          id={id}
          name="code"
          type="text"
          inputMode="numeric"
          autoComplete="one-time-code"
          pattern="[0-9]*"
          maxLength={TOTP_DIGITS + 2}
          value={value}
          disabled={disabled}
          onChange={(e) => onChange(normalizeCode(e.target.value))}
          onFocus={() => setFocused(true)}
          onBlur={() => setFocused(false)}
          aria-invalid={error ? true : undefined}
          aria-describedby={[errorId, hintId].filter(Boolean).join(" ") || undefined}
          className="absolute inset-0 h-full w-full cursor-text bg-transparent text-transparent caret-transparent outline-none selection:bg-transparent"
        />
      </div>
      {error && (
        <p id={errorId} className="mt-2 text-center text-xs text-bloqueo">
          {error}
        </p>
      )}
      {hint && (
        <p id={hintId} className="mt-2 text-center text-xs text-texto-suave">
          {hint}
        </p>
      )}
    </div>
  );
}
