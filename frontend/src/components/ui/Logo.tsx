import { cx } from "./cx";
import { Icon } from "./Icon";

/** Marca de AuthShield: escudo en recuadro + nombre. */
export function Logo({ withText = true, className }: { withText?: boolean; className?: string }) {
  return (
    <span className={cx("inline-flex items-center gap-2.5", className)}>
      <span className="grid h-9 w-9 place-items-center rounded-lg border border-acento/30 bg-acento/10 text-acento">
        <Icon name="shieldCheck" size={20} />
      </span>
      {withText && (
        <span className="text-base font-semibold tracking-tight">
          Auth<span className="text-acento">Shield</span>
        </span>
      )}
    </span>
  );
}
