/**
 * Escenarios. Cada uno devuelve la lista de intentos con el resultado HTTP y si la contraseña
 * enviada era la correcta (dato que el simulador conoce porque generó las cuentas).
 */

import { COMMON_PASSWORD, IPS, SIZES } from "./dataset.mjs";

async function attempt(client, attempts, { email, password, ip, valid }) {
  const r = await client.login(email, password, ip);
  attempts.push({ n: attempts.length + 1, ip, valid, status: r.status, code: r.code, ms: r.ms });
}

/** Usuarios reales: cada uno desde su IP; algunos se equivocan una vez antes de acertar. */
async function legitimate(client, data, ipFor) {
  const attempts = [];
  for (const [i, user] of data.legitimate.entries()) {
    const ip = ipFor(i);
    if (user.makesTypo) await attempt(client, attempts, { email: user.email, password: `${user.password}x`, ip, valid: false });
    await attempt(client, attempts, { email: user.email, password: user.password, ip, valid: true });
  }
  return attempts;
}

export const SCENARIOS = [
  {
    id: "a",
    name: "Tráfico legítimo",
    kind: "legitimate",
    description: `${SIZES.legitimateUsers} usuarios, cada uno desde su IP (192.0.2.x); 1 de cada ${SIZES.typoEvery} se equivoca una vez.`,
    run: (client, data) => legitimate(client, data, IPS.legitimate),
  },
  {
    id: "b",
    name: "Stuffing, 1 IP",
    kind: "attack",
    description: `Lista filtrada de ${SIZES.leakedEntries} credenciales (${SIZES.leakedValid} válidas) desde ${IPS.stuffingSingle}.`,
    async run(client, data) {
      const attempts = [];
      for (const c of data.leakedList) await attempt(client, attempts, { ...c, ip: IPS.stuffingSingle });
      return attempts;
    },
  },
  {
    id: "c",
    name: "Stuffing distribuido",
    kind: "attack",
    description: `La misma lista repartida en rotación entre ${SIZES.distributedIps} IPs (203.0.113.x), ${Math.ceil(SIZES.leakedEntries / SIZES.distributedIps)} intentos por IP.`,
    async run(client, data) {
      const attempts = [];
      for (const [i, c] of data.leakedList.entries()) {
        await attempt(client, attempts, { ...c, ip: IPS.stuffingDistributed(i % SIZES.distributedIps) });
      }
      return attempts;
    },
  },
  {
    id: "d",
    name: "Password spraying",
    kind: "attack",
    description: `"${COMMON_PASSWORD}" contra ${SIZES.sprayAccounts} cuentas (${SIZES.sprayVulnerable} la usan) desde ${IPS.spraying}.`,
    async run(client, data) {
      const attempts = [];
      for (const a of data.sprayAccounts) {
        await attempt(client, attempts, {
          email: a.email,
          password: COMMON_PASSWORD,
          ip: IPS.spraying,
          valid: a.password === COMMON_PASSWORD,
        });
      }
      return attempts;
    },
  },
  {
    id: "e",
    name: "Fuerza bruta, 1 cuenta",
    kind: "attack",
    description: `${SIZES.bruteForceGuesses} contraseñas contra una cuenta desde ${IPS.bruteForce}; la correcta es la n.º ${SIZES.bruteForceCorrectAt}.`,
    async run(client, data) {
      const attempts = [];
      for (const password of data.dictionary) {
        const valid = password === data.bruteVictim.password;
        await attempt(client, attempts, { email: data.bruteVictim.email, password, ip: IPS.bruteForce, valid });
        // Un atacante real se detiene al obtener tokens.
        if (valid && attempts.at(-1).status === 200) break;
      }
      return attempts;
    },
  },
  {
    id: "a2",
    name: "Legítimo durante ataque",
    kind: "legitimate",
    description: "Los mismos usuarios legítimos desde otras IPs (192.0.2.101+), con la tasa global de fallos ya alta.",
    run: (client, data) => legitimate(client, data, IPS.legitimateUnderAttack),
  },
];
