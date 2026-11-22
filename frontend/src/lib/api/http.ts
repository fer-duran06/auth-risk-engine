import { SIMULATED_IP_HEADER } from "./simulated-ip";

export interface HttpConfig {
  baseUrl: string;
  demoMode: boolean;
  /** Devuelve la IP simulada actual; solo se usa con demoMode. */
  getSimulatedIp?: () => string | null;
  /** Inyectable para pruebas. */
  fetchFn?: typeof fetch;
}

export interface RequestOptions {
  method?: "GET" | "POST";
  json?: unknown;
  /** Access token para el header Authorization. */
  token?: string;
}

export type HttpRequest = (path: string, options?: RequestOptions) => Promise<Response>;

/**
 * Crea la función base de peticiones. Siempre usa credentials: "include" para que el navegador
 * envíe y reciba la cookie HttpOnly de refresh. Lanza solo ante errores de red.
 */
export function createHttp(config: HttpConfig): HttpRequest {
  const fetchFn = config.fetchFn ?? ((...args: Parameters<typeof fetch>) => fetch(...args));

  return (path, options = {}) => {
    const headers: Record<string, string> = { Accept: "application/json" };
    if (options.json !== undefined) headers["Content-Type"] = "application/json";
    if (options.token) headers.Authorization = `Bearer ${options.token}`;
    if (config.demoMode) {
      const ip = config.getSimulatedIp?.();
      if (ip) headers[SIMULATED_IP_HEADER] = ip;
    }

    return fetchFn(`${config.baseUrl}${path}`, {
      method: options.method ?? "GET",
      headers,
      body: options.json !== undefined ? JSON.stringify(options.json) : undefined,
      credentials: "include",
      cache: "no-store",
    });
  };
}
