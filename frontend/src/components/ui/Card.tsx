import type { HTMLAttributes } from "react";
import { cx } from "./cx";

/** Superficie base (#121A30) con borde sutil. */
export function Card({ className, ...rest }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cx("rounded-2xl border border-borde bg-tarjeta shadow-xl shadow-black/30", className)}
      {...rest}
    />
  );
}
