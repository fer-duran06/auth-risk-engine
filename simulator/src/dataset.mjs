/**
 * Datos sintéticos de la simulación. Todos los emails usan el dominio reservado ejemplo.test
 * y llevan el runId para que cada corrida tenga cuentas nuevas (los contadores por cuenta
 * de corridas anteriores no las afectan).
 */

import { createRng } from "./random.mjs";

/** Bloques de IPs de documentación (RFC 5737), uno por escenario. */
export const IPS = {
  legitimate: (i) => `192.0.2.${1 + i}`, //            a) 192.0.2.1-10
  legitimateUnderAttack: (i) => `192.0.2.${101 + i}`, // a2) 192.0.2.101-110
  stuffingSingle: "198.51.100.10", //                  b)
  stuffingDistributed: (i) => `203.0.113.${1 + i}`, // c) 203.0.113.1-75
  spraying: "198.51.100.20", //                       d)
  bruteForce: "198.51.100.30", //                     e)
};

export const SIZES = {
  legitimateUsers: 10,
  /** Usuarios legítimos que se equivocan una vez antes de acertar (índices). */
  typoEvery: 3,
  leakedEntries: 150,
  leakedValid: 15, // 10 %
  leakedStaleVictims: 15, // víctimas con una contraseña antigua (ya no válida) en la filtración
  distributedIps: 75,
  sprayAccounts: 40,
  sprayVulnerable: 5, // cuentas que usan la contraseña común
  bruteForceGuesses: 40,
  bruteForceCorrectAt: 30, // posición (1-based) de la contraseña correcta en el diccionario
};

/** Contraseña común del password spraying (≥ 12 caracteres para cumplir la política). */
export const COMMON_PASSWORD = "Primavera2026!";

const strongPassword = (rng) => `${rng.string(6)}-${rng.string(6)}-${rng.string(4)}`;

/**
 * Genera cuentas y listas de ataque.
 * @param {{ seed: number, runId: string }} opts
 */
export function buildDataset({ seed, runId }) {
  const rng = createRng(seed);
  const email = (role, i) => `${role}-${runId}-${String(i).padStart(3, "0")}@ejemplo.test`;
  const account = (role, i, password) => ({ email: email(role, i), password });

  // a) y a2) Usuarios legítimos.
  const legitimate = Array.from({ length: SIZES.legitimateUsers }, (_, i) => ({
    ...account("legit", i, strongPassword(rng)),
    makesTypo: i % SIZES.typoEvery === 0,
  }));

  // b) y c) Víctimas del stuffing y lista "filtrada" (email:contraseña).
  const victimCount = SIZES.leakedValid + SIZES.leakedStaleVictims;
  const victims = Array.from({ length: victimCount }, (_, i) => account("victima", i, strongPassword(rng)));
  const leaked = [
    ...victims.slice(0, SIZES.leakedValid).map((v) => ({ email: v.email, password: v.password, valid: true })),
    ...victims.slice(SIZES.leakedValid).map((v) => ({ email: v.email, password: strongPassword(rng), valid: false })),
  ];
  for (let i = leaked.length; i < SIZES.leakedEntries; i++) {
    // Cuentas que no existen en el sistema (filtradas de otro servicio).
    leaked.push({ email: email("filtrado", i), password: strongPassword(rng), valid: false });
  }
  const leakedList = rng.shuffle(leaked);

  // d) Spraying: algunas cuentas usan la contraseña común.
  const sprayAccounts = rng.shuffle(
    Array.from({ length: SIZES.sprayAccounts }, (_, i) =>
      account("spray", i, i < SIZES.sprayVulnerable ? COMMON_PASSWORD : strongPassword(rng)),
    ),
  );

  // e) Fuerza bruta: diccionario con la contraseña real en una posición fija.
  const bruteVictim = account("fuerza", 0, strongPassword(rng));
  const dictionary = Array.from({ length: SIZES.bruteForceGuesses }, (_, i) =>
    i === SIZES.bruteForceCorrectAt - 1 ? bruteVictim.password : `clave${rng.string(3)}${1990 + i}`,
  );

  return {
    legitimate,
    victims,
    leakedList,
    sprayAccounts,
    bruteVictim,
    dictionary,
    /** Todas las cuentas que hay que registrar antes de atacar. */
    accountsToRegister: [...legitimate, ...victims, ...sprayAccounts, bruteVictim],
  };
}
