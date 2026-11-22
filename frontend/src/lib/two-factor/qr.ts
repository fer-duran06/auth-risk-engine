import qrcode from "qrcode-generator";

/**
 * Matriz de módulos del QR (true = oscuro). Se dibuja como SVG en React, sin insertar HTML
 * generado por la librería (sin dangerouslySetInnerHTML).
 * Nivel de corrección M: suficiente para leerlo de la pantalla y deja el código compacto.
 */
export function qrMatrix(text: string): boolean[][] {
  const qr = qrcode(0, "M");
  qr.addData(text, "Byte");
  qr.make();
  const size = qr.getModuleCount();
  return Array.from({ length: size }, (_, row) => Array.from({ length: size }, (_, col) => qr.isDark(row, col)));
}

/** Path SVG con un cuadrado de 1×1 por módulo oscuro (un solo elemento <path>). */
export function qrPath(matrix: boolean[][]): string {
  const parts: string[] = [];
  matrix.forEach((row, y) => row.forEach((dark, x) => dark && parts.push(`M${x} ${y}h1v1h-1z`)));
  return parts.join("");
}
