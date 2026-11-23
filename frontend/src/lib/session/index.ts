"use client";

import { useSyncExternalStore } from "react";
import { authApi } from "../api";
import { challengeStore } from "../two-factor/challenge-store";
import { createSession, type SessionStatus } from "./session";

/** Sesión única de la app (en memoria del navegador). */
export const session = createSession(authApi, () => challengeStore.clear());

/** Estado de la sesión para componentes React. En el servidor siempre es "unknown". */
export function useSessionStatus(): SessionStatus {
  return useSyncExternalStore(session.subscribe, session.getStatus, () => "unknown");
}

export type { SessionStatus } from "./session";
