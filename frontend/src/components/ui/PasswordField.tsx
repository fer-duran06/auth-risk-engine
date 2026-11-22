"use client";

import { useState } from "react";
import { Icon } from "./Icon";
import { TextField, type TextFieldProps } from "./TextField";

type PasswordFieldProps = Omit<TextFieldProps, "type" | "trailing">;

/** Campo de contraseña con botón accesible para mostrarla u ocultarla. */
export function PasswordField({ icon = "key", ...props }: PasswordFieldProps) {
  const [visible, setVisible] = useState(false);

  return (
    <TextField
      {...props}
      icon={icon}
      type={visible ? "text" : "password"}
      // Evita que el corrector o el autocompletado de texto procesen la contraseña.
      spellCheck={false}
      autoCapitalize="none"
      trailing={
        <button
          type="button"
          onClick={() => setVisible((v) => !v)}
          aria-label={visible ? "Ocultar contraseña" : "Mostrar contraseña"}
          aria-pressed={visible}
          className="-mr-1 rounded p-1 text-texto-suave hover:text-texto"
        >
          <Icon name={visible ? "eyeOff" : "eye"} />
        </button>
      }
    />
  );
}
