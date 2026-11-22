"use client";

import { useMemo } from "react";
import { qrMatrix, qrPath } from "@/lib/two-factor/qr";

/** QR dibujado como SVG propio a partir de la matriz (sin HTML generado por la librería). */
export function QrCode({ value, label, size = 184 }: { value: string; label: string; size?: number }) {
  const matrix = useMemo(() => qrMatrix(value), [value]);
  const quiet = 4; // zona de silencio estándar de 4 módulos
  const dim = matrix.length + quiet * 2;

  return (
    <svg
      role="img"
      aria-label={label}
      width={size}
      height={size}
      viewBox={`${-quiet} ${-quiet} ${dim} ${dim}`}
      shapeRendering="crispEdges"
      className="rounded-lg"
    >
      {/* Fondo blanco: las apps leen mejor módulos oscuros sobre claro. */}
      <rect x={-quiet} y={-quiet} width={dim} height={dim} fill="#ffffff" />
      <path d={qrPath(matrix)} fill="#0b1020" />
    </svg>
  );
}
