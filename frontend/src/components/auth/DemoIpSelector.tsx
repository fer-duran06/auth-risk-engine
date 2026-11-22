"use client";

import { useId, useState, useSyncExternalStore } from "react";
import { Badge, Icon } from "@/components/ui";
import { DEMO_MODE } from "@/lib/api";
import { DEMO_IPS, isIpLiteral, simulatedIp } from "@/lib/api/simulated-ip";

const REAL = "";
const CUSTOM = "__otra__";

/**
 * Selector de IP simulada (header X-Simulated-IP). Solo existe con NEXT_PUBLIC_DEMO_MODE=true;
 * en otro caso no se renderiza y el cliente tampoco envía el header.
 */
export function DemoIpSelector() {
  if (!DEMO_MODE) return null;
  return <Selector />;
}

function Selector() {
  const id = useId();
  const current = useSyncExternalStore(simulatedIp.subscribe, simulatedIp.get, () => null);
  const [mode, setMode] = useState<string>(() =>
    current === null ? REAL : (DEMO_IPS as readonly string[]).includes(current) ? current : CUSTOM,
  );
  const [custom, setCustom] = useState(current ?? "");
  const customInvalid = mode === CUSTOM && custom.trim() !== "" && !isIpLiteral(custom);

  function onSelect(value: string) {
    setMode(value);
    if (value === CUSTOM) simulatedIp.set(custom);
    else simulatedIp.set(value === REAL ? null : value);
  }

  function onCustom(value: string) {
    setCustom(value);
    simulatedIp.set(value);
  }

  return (
    <fieldset className="rounded-lg border border-dashed border-desafio/40 bg-fondo/60 p-3">
      <legend className="sr-only">Simulador de IP de origen (modo demo)</legend>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <label htmlFor={`${id}-ip`} className="flex items-center gap-2 text-xs font-semibold tracking-wide text-texto-suave uppercase">
          <Icon name="globe" size={14} />
          Simulador: IP de origen
        </label>
        <Badge tone="challenge">Modo demo</Badge>
      </div>
      <select
        id={`${id}-ip`}
        value={mode}
        onChange={(e) => onSelect(e.target.value)}
        className="mt-2 w-full rounded-md border border-borde bg-fondo px-2 py-2 font-mono text-sm text-texto focus:border-acento"
      >
        <option value={REAL}>IP real de la conexión</option>
        {DEMO_IPS.map((ip) => (
          <option key={ip} value={ip}>
            {ip}
          </option>
        ))}
        <option value={CUSTOM}>Otra IP…</option>
      </select>
      {mode === CUSTOM && (
        <div className="mt-2">
          <label htmlFor={`${id}-custom`} className="sr-only">
            IP personalizada
          </label>
          <input
            id={`${id}-custom`}
            value={custom}
            onChange={(e) => onCustom(e.target.value)}
            placeholder="p. ej. 203.0.113.99 o 2001:db8::1"
            inputMode="text"
            autoComplete="off"
            spellCheck={false}
            aria-invalid={customInvalid || undefined}
            aria-describedby={customInvalid ? `${id}-custom-error` : undefined}
            className="w-full rounded-md border border-borde bg-fondo px-2 py-2 font-mono text-sm text-texto placeholder:text-texto-suave/60 focus:border-acento"
          />
          {customInvalid && (
            <p id={`${id}-custom-error`} className="mt-1 text-xs text-bloqueo">
              Escribe una IPv4 o IPv6 válida. Mientras tanto se usa la IP real.
            </p>
          )}
        </div>
      )}
    </fieldset>
  );
}
