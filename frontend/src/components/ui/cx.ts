/** Une clases de Tailwind ignorando valores vacíos. */
export function cx(...classes: (string | false | null | undefined)[]): string {
  return classes.filter(Boolean).join(" ");
}
