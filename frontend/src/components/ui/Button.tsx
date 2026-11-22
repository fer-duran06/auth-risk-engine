import type { ButtonHTMLAttributes, ReactNode } from "react";
import { cx } from "./cx";
import { Icon, type IconName } from "./Icon";

type Variant = "primary" | "secondary" | "danger" | "ghost";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  loading?: boolean;
  icon?: IconName;
  iconEnd?: IconName;
  fullWidth?: boolean;
  children: ReactNode;
}

const variants: Record<Variant, string> = {
  primary: "bg-acento text-fondo hover:bg-acento/90 shadow-[0_0_24px_-6px] shadow-acento/60",
  secondary: "border border-borde bg-tarjeta text-texto hover:border-acento/60 hover:text-acento",
  danger: "border border-bloqueo/40 bg-bloqueo/10 text-bloqueo hover:bg-bloqueo/20",
  ghost: "text-texto-suave hover:text-texto hover:bg-white/5",
};

export function Button({
  variant = "primary",
  loading = false,
  icon,
  iconEnd,
  fullWidth,
  disabled,
  className,
  children,
  type = "button",
  ...rest
}: ButtonProps) {
  return (
    <button
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={cx(
        "inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2.5 text-sm font-semibold transition-colors",
        "disabled:cursor-not-allowed disabled:opacity-50",
        variants[variant],
        fullWidth && "w-full",
        className,
      )}
      {...rest}
    >
      {loading ? (
        <span className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent" aria-hidden />
      ) : (
        icon && <Icon name={icon} />
      )}
      {children}
      {!loading && iconEnd && <Icon name={iconEnd} />}
    </button>
  );
}
