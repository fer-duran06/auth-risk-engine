"use client";

import { useId, type InputHTMLAttributes, type ReactNode } from "react";
import { cx } from "./cx";
import { Icon, type IconName } from "./Icon";

export interface TextFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, "id"> {
  label: string;
  icon?: IconName;
  /** Mensaje de error del campo; se enlaza con aria-describedby. */
  error?: string;
  /** Texto de ayuda bajo el campo. */
  hint?: ReactNode;
  /** Contenido a la derecha de la etiqueta (p. ej. un enlace). */
  labelAside?: ReactNode;
  /** Contenido dentro del campo, a la derecha (p. ej. botón mostrar contraseña). */
  trailing?: ReactNode;
  id?: string;
}

/** Campo de texto con etiqueta visible, icono, ayuda y error accesibles. */
export function TextField({ label, icon, error, hint, labelAside, trailing, id, className, ...input }: TextFieldProps) {
  const autoId = useId();
  const inputId = id ?? autoId;
  const hintId = hint ? `${inputId}-ayuda` : undefined;
  const errorId = error ? `${inputId}-error` : undefined;
  const describedBy = [errorId, hintId].filter(Boolean).join(" ") || undefined;

  return (
    <div className={className}>
      <div className="mb-1.5 flex items-center justify-between gap-2">
        <label htmlFor={inputId} className="text-xs font-semibold tracking-wide text-texto uppercase">
          {label}
        </label>
        {labelAside}
      </div>
      <div
        className={cx(
          "flex items-center gap-2 rounded-lg border bg-fondo px-3 transition-colors",
          "focus-within:border-acento focus-within:ring-2 focus-within:ring-acento/30",
          error ? "border-bloqueo/70" : "border-borde",
        )}
      >
        {icon && <Icon name={icon} className="shrink-0 text-texto-suave" />}
        <input
          id={inputId}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className="w-full min-w-0 bg-transparent py-2.5 text-sm text-texto placeholder:text-texto-suave/60 focus:outline-none"
          {...input}
        />
        {trailing}
      </div>
      {error && (
        <p id={errorId} className="mt-1.5 flex items-center gap-1.5 text-xs text-bloqueo">
          <Icon name="alert" size={14} />
          {error}
        </p>
      )}
      {hint && (
        <p id={hintId} className="mt-1.5 text-xs text-texto-suave">
          {hint}
        </p>
      )}
    </div>
  );
}
