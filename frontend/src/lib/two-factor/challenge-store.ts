/**
 * Desafío pendiente entre /login y /login/verify. El challengeId vive solo en memoria (closure
 * del módulo): nunca en localStorage, sessionStorage, cookies ni en la URL. Al recargar se pierde
 * y el usuario vuelve a iniciar sesión.
 */

/** Vigencia en el backend (app.two-factor.challenge-ttl). Pasado este tiempo no tiene sentido intentarlo. */
export const CHALLENGE_TTL_MS = 5 * 60 * 1000;

export interface PendingChallenge {
  id: string;
  expiresAt: number;
}

export function createChallengeStore(now: () => number = Date.now) {
  let current: PendingChallenge | null = null;
  const listeners = new Set<() => void>();
  const notify = () => listeners.forEach((l) => l());

  return {
    set(id: string): void {
      current = { id, expiresAt: now() + CHALLENGE_TTL_MS };
      notify();
    },

    /** Desafío vigente, o null si no hay o ya venció (en ese caso se descarta). */
    get(): PendingChallenge | null {
      if (current && now() >= current.expiresAt) current = null;
      return current;
    },

    clear(): void {
      current = null;
      notify();
    },

    /** Para useSyncExternalStore: la vista de verificación reacciona a un desafío nuevo. */
    subscribe(listener: () => void): () => void {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
  };
}

export type ChallengeStore = ReturnType<typeof createChallengeStore>;

/** Instancia única de la app. */
export const challengeStore = createChallengeStore();
