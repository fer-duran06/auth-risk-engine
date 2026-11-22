/**
 * Validación del objetivo: el simulador solo ataca al backend local.
 * Se compara el hostname ya parseado (no el texto), así que trucos como
 * "http://localhost.evil.com" o "http://127.0.0.1@evil.com" se rechazan.
 */

const ALLOWED_HOSTS = new Set(["localhost", "127.0.0.1"]);

/** Devuelve la URL base normalizada (sin barra final) o lanza un Error con el motivo. */
export function validateBaseUrl(raw) {
  let url;
  try {
    url = new URL(raw);
  } catch {
    throw new Error(`--base-url no es una URL válida: ${raw}`);
  }
  if (url.protocol !== "http:" && url.protocol !== "https:") {
    throw new Error("--base-url debe usar http o https");
  }
  if (url.username || url.password) {
    throw new Error("--base-url no puede incluir credenciales");
  }
  if (!ALLOWED_HOSTS.has(url.hostname)) {
    throw new Error(`Objetivo rechazado (${url.hostname}): solo se permite localhost o 127.0.0.1`);
  }
  if (url.search || url.hash) {
    throw new Error("--base-url no puede incluir query ni fragmento");
  }
  return `${url.origin}${url.pathname}`.replace(/\/+$/, "");
}
