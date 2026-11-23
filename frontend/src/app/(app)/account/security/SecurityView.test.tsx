// @vitest-environment happy-dom
import { Activity, act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const SECRET = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";
const URI = `otpauth://totp/AuthShield:ana%40example.com?secret=${SECRET}&issuer=AuthShield`;

// Router estable, como el de Next: un objeto nuevo por render dispararía la carga en bucle.
const router = { replace: vi.fn(), push: vi.fn() };
vi.mock("next/navigation", () => ({ useRouter: () => router }));
vi.mock("@/lib/api", () => ({
  authApi: {
    totpStatus: vi.fn(async () => ({ kind: "ok", status: { enabled: false, confirmedAt: null } })),
    totpSetup: vi.fn(async () => ({ kind: "ok", setup: { secret: SECRET, otpauthUri: URI } })),
    totpEnable: vi.fn(),
  },
}));
vi.mock("@/lib/session", () => ({
  session: { withAuth: (call: (token: string) => Promise<unknown>) => call("tok") },
}));

const { SecurityView } = await import("./SecurityView");

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;
let root: Root;

async function render(mode: "visible" | "hidden") {
  await act(async () =>
    root.render(
      <Activity mode={mode}>
        <SecurityView />
      </Activity>,
    ),
  );
}

const button = (text: string) =>
  [...container.querySelectorAll("button")].find((b) => b.textContent?.includes(text)) ?? null;

describe("SecurityView con la página preservada por Activity", () => {
  beforeEach(() => {
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
  });

  it("al salir a mitad de la activación, el secreto no queda en el estado ni en el DOM", async () => {
    await render("visible");
    await act(async () => button("Activar verificación en dos pasos")!.click());
    // Clave agrupada de 4 en 4 y URI visibles durante la activación.
    expect(container.textContent).toContain("JBSW Y3DP");
    expect(container.innerHTML).toContain(SECRET);

    await render("hidden");
    expect(container.innerHTML).not.toContain(SECRET);

    await render("visible");
    expect(container.innerHTML).not.toContain(SECRET);
    expect(button("Activar verificación en dos pasos")).not.toBeNull();
  });
});
