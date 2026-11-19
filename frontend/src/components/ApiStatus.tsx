"use client";

import { useEffect, useState } from "react";

type Estado = "comprobando" | "en-linea" | "fuera-de-linea";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
const INTERVALO_MS = 10_000;

const etiquetas: Record<Estado, { texto: string; color: string }> = {
  comprobando: { texto: "comprobando…", color: "bg-texto-suave" },
  "en-linea": { texto: "en línea", color: "bg-permitido" },
  "fuera-de-linea": { texto: "fuera de línea", color: "bg-bloqueo" },
};

/** Consulta periódicamente /actuator/health del backend y muestra si la API responde. */
export default function ApiStatus() {
  const [estado, setEstado] = useState<Estado>("comprobando");

  useEffect(() => {
    let activo = true;

    const comprobar = async () => {
      try {
        const res = await fetch(`${API_URL}/actuator/health`, { cache: "no-store" });
        const body = res.ok ? await res.json() : null;
        if (activo) setEstado(body?.status === "UP" ? "en-linea" : "fuera-de-linea");
      } catch {
        if (activo) setEstado("fuera-de-linea");
      }
    };

    comprobar();
    const id = setInterval(comprobar, INTERVALO_MS);
    return () => {
      activo = false;
      clearInterval(id);
    };
  }, []);

  const { texto, color } = etiquetas[estado];

  return (
    <div className="flex items-center justify-between rounded-lg border border-borde bg-fondo px-4 py-3">
      <span className="flex items-center gap-2 text-sm">
        <span className={`h-2.5 w-2.5 rounded-full ${color}`} aria-hidden />
        API: <strong className="font-medium">{texto}</strong>
      </span>
      <span className="font-mono text-xs text-texto-suave">{API_URL}</span>
    </div>
  );
}
