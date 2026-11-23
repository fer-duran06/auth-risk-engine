// @vitest-environment happy-dom
import { Activity, act, useSyncExternalStore } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// Next y la API se sustituyen: solo interesa el ciclo de vida del componente.
const router = { replace: vi.fn(), push: vi.fn() };
vi.mock("next/navigation", () => ({ useRouter: () => router }));
const verifyChallenge = vi.fn(async () => ({ kind: "invalid" as const }));
vi.mock("@/lib/api", () => ({ authApi: { verifyChallenge } }));
// Sesión falsa con estado observable: start() la abre, como la real.
const fakeSession = vi.hoisted(() => {
  const listeners = new Set<() => void>();
  const state = { status: "anonymous" as "anonymous" | "authenticated" };
  return {
    state,
    subscribe: (l: () => void) => (listeners.add(l), () => listeners.delete(l)),
    start: () => {
      state.status = "authenticated";
      listeners.forEach((l) => l());
    },
  };
});
vi.mock("@/lib/session", () => ({
  session: { start: vi.fn(fakeSession.start) },
  useSessionStatus: () => useSyncExternalStore(fakeSession.subscribe, () => fakeSession.state.status),
}));

const { challengeStore } = await import("@/lib/two-factor/challenge-store");
const { VerifyForm } = await import("./VerifyForm");

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;
let root: Root;

/**
 * Con cacheComponents, Next 16 no desmonta la página al navegar: la oculta con <Activity> y
 * conserva su estado. Aquí se reproduce igual: visible en /login/verify, oculta en /login.
 */
async function render(mode: "visible" | "hidden") {
  await act(async () =>
    root.render(
      <Activity mode={mode}>
        <VerifyForm />
      </Activity>,
    ),
  );
}

async function submitCode(code: string) {
  const input = container.querySelector<HTMLInputElement>("#verify-code")!;
  const setValue = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!;
  await act(async () => {
    setValue.call(input, code);
    input.dispatchEvent(new Event("input", { bubbles: true }));
  });
  await act(async () => {
    container.querySelector("form")!.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
  });
}

const unavailable = () => container.textContent?.includes("La verificación ya no está disponible") ?? false;

describe("VerifyForm con la página preservada por Activity", () => {
  beforeEach(() => {
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
    challengeStore.clear();
    fakeSession.state.status = "anonymous";
    vi.clearAllMocks();
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
  });

  it("un desafío nuevo muestra el formulario aunque el anterior haya terminado", async () => {
    // Primer desafío: 5 códigos rechazados → "ya no está disponible".
    challengeStore.set("ch-1");
    await render("visible");
    for (let i = 0; i < 5; i++) await submitCode("000000");
    expect(unavailable()).toBe(true);

    // "Volver al inicio de sesión": la página queda oculta, no desmontada.
    await render("hidden");

    // Nuevo login en CHALLENGE y regreso a /login/verify.
    challengeStore.set("ch-2");
    await render("visible");

    expect(unavailable()).toBe(false);
    expect(container.querySelector("#verify-code")).not.toBeNull();
    expect(challengeStore.get()?.id).toBe("ch-2");

    // Y el código se verifica contra el desafío nuevo.
    await submitCode("123456");
    expect(verifyChallenge).toHaveBeenLastCalledWith("ch-2", "123456");
  });

  it("tras verificar con éxito navega una sola vez, a /me, sin pasar por /login", async () => {
    verifyChallenge.mockResolvedValueOnce({ kind: "ok", token: { accessToken: "tok", expiresIn: 900 } } as never);
    challengeStore.set("ch-ok");
    await render("visible");

    await submitCode("123456");

    expect(verifyChallenge).toHaveBeenCalledWith("ch-ok", "123456");
    expect(router.replace.mock.calls).toEqual([["/me"]]);
    expect(challengeStore.get()).toBeNull();
  });

  it("sin desafío en memoria redirige al login", async () => {
    await render("visible");
    expect(router.replace).toHaveBeenCalledWith("/login");
    expect(container.querySelector("#verify-code")).toBeNull();
  });
});
