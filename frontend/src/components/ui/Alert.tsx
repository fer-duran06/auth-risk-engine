import type { ReactNode } from "react";
import { cx } from "./cx";
import { Icon, type IconName } from "./Icon";

/** Tonos alineados con las zonas: permitido (verde), desafío (naranja), bloqueo (rojo). */
export type AlertTone = "info" | "success" | "challenge" | "block" | "error";

const tones: Record<AlertTone, { box: string; icon: IconName }> = {
  info: { box: "border-acento/40 bg-acento/10 text-acento", icon: "info" },
  success: { box: "border-permitido/40 bg-permitido/10 text-permitido", icon: "check" },
  challenge: { box: "border-desafio/40 bg-desafio/10 text-desafio", icon: "shield" },
  block: { box: "border-bloqueo/40 bg-bloqueo/10 text-bloqueo", icon: "lock" },
  error: { box: "border-bloqueo/40 bg-bloqueo/10 text-bloqueo", icon: "alert" },
};

interface AlertProps {
  tone: AlertTone;
  title?: string;
  children?: ReactNode;
  className?: string;
}

/** Mensaje destacado. Los tonos de error/bloqueo se anuncian de inmediato (role="alert"). */
export function Alert({ tone, title, children, className }: AlertProps) {
  const urgent = tone === "error" || tone === "block";
  const { box, icon } = tones[tone];
  return (
    <div role={urgent ? "alert" : "status"} className={cx("flex gap-3 rounded-lg border px-4 py-3 text-sm", box, className)}>
      <Icon name={icon} className="mt-0.5 shrink-0" />
      <div className="space-y-1">
        {title && <p className="font-semibold">{title}</p>}
        {children && <div className="text-texto/85">{children}</div>}
      </div>
    </div>
  );
}
