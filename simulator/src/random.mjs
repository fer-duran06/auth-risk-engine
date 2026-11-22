/** Generador pseudoaleatorio con semilla (mulberry32): la misma semilla produce los mismos datos. */
export function createRng(seed) {
  let a = seed >>> 0;
  const next = () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };

  const ALPHABET = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";

  return {
    next,
    int: (max) => Math.floor(next() * max),
    /** Cadena aleatoria del alfabeto (sin caracteres ambiguos). */
    string(length) {
      let s = "";
      for (let i = 0; i < length; i++) s += ALPHABET[Math.floor(next() * ALPHABET.length)];
      return s;
    },
    /** Copia barajada (Fisher-Yates). */
    shuffle(items) {
      const copy = [...items];
      for (let i = copy.length - 1; i > 0; i--) {
        const j = Math.floor(next() * (i + 1));
        [copy[i], copy[j]] = [copy[j], copy[i]];
      }
      return copy;
    },
  };
}
