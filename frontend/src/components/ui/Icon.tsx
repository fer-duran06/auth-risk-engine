import type { SVGProps } from "react";

/** Iconos SVG en línea (trazo de 24px, estilo outline). Sin dependencias externas. */
const paths = {
  shield: "M12 3 4.5 6v5.5c0 4.6 3.2 8.6 7.5 9.5 4.3-.9 7.5-4.9 7.5-9.5V6L12 3Z",
  shieldCheck: "M12 3 4.5 6v5.5c0 4.6 3.2 8.6 7.5 9.5 4.3-.9 7.5-4.9 7.5-9.5V6L12 3Zm-3 9 2 2 4-4",
  lock: "M7 11V8a5 5 0 0 1 10 0v3M5.5 11h13v9h-13z",
  mail: "M3.5 6.5h17v11h-17zM3.5 7l8.5 6 8.5-6",
  key: "M14.5 9.5a4 4 0 1 0-3.9 3.9M10.6 13.4 20 4m-3 3 2 2m-4.5.5 1.5 1.5",
  eye: "M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12ZM12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z",
  eyeOff: "M3 3l18 18M10.6 10.6a2 2 0 0 0 2.8 2.8M7.4 7.4C4.4 9 2.5 12 2.5 12S6 18.5 12 18.5c1.8 0 3.3-.6 4.6-1.4M10 5.7c.6-.1 1.3-.2 2-.2 6 0 9.5 6.5 9.5 6.5s-.8 1.5-2.3 3.1",
  arrowRight: "M5 12h14m-5-5 5 5-5 5",
  alert: "M12 8v5m0 3.5v.01M10.3 4l-7.6 13.2A2 2 0 0 0 4.4 20h15.2a2 2 0 0 0 1.7-2.8L13.7 4a2 2 0 0 0-3.4 0Z",
  info: "M12 11v5m0-8.5v.01M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z",
  check: "m5 12.5 4.5 4.5L19 7.5",
  x: "M6 6l12 12M18 6 6 18",
  user: "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm-7.5 8.5c.8-3.4 3.9-5.5 7.5-5.5s6.7 2.1 7.5 5.5",
  logout: "M15 4.5h3.5v15H15M10 8l-4 4 4 4m-4-4h11",
  clock: "M12 7.5V12l3 2m6-2a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z",
  globe: "M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0ZM3 12h18M12 3c2.5 2.5 3.8 5.5 3.8 9s-1.3 6.5-3.8 9c-2.5-2.5-3.8-5.5-3.8-9S9.5 5.5 12 3Z",
  fingerprint: "M12 11v3.5c0 2.5-.7 4.5-2 6M8.5 9.5a3.5 3.5 0 0 1 7 0v4c0 2-.3 3.7-1 5.3M5.5 15V10a6.5 6.5 0 0 1 11-4.7M18.5 10v3.5",
} as const;

export type IconName = keyof typeof paths;

interface IconProps extends SVGProps<SVGSVGElement> {
  name: IconName;
  size?: number;
}

export function Icon({ name, size = 18, className, ...rest }: IconProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.75}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      className={className}
      {...rest}
    >
      <path d={paths[name]} />
    </svg>
  );
}
