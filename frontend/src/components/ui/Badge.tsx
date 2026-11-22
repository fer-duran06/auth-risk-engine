import type { ReactNode } from "react";
import { cx } from "./cx";

type BadgeTone = "neutral" | "accent" | "success" | "challenge" | "block";

const tones: Record<BadgeTone, string> = {
  neutral: "border-borde text-texto-suave",
  accent: "border-acento/40 text-acento bg-acento/10",
  success: "border-permitido/40 text-permitido bg-permitido/10",
  challenge: "border-desafio/40 text-desafio bg-desafio/10",
  block: "border-bloqueo/40 text-bloqueo bg-bloqueo/10",
};

/** Etiqueta corta en monoespaciada (estados, roles, modo demo). */
export function Badge({ tone = "neutral", children, className }: { tone?: BadgeTone; children: ReactNode; className?: string }) {
  return (
    <span
      className={cx(
        "inline-flex items-center gap-1.5 rounded-md border px-2 py-0.5 font-mono text-[11px] font-medium tracking-wide uppercase",
        tones[tone],
        className,
      )}
    >
      {children}
    </span>
  );
}
