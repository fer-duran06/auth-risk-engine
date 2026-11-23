// @vitest-environment happy-dom
import { Activity, act, type ReactNode } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("next/link", () => ({ default: ({ href, children }: { href: string; children: ReactNode }) => <a href={href}>{children}</a> }));
const register = vi.fn(async () => ({ kind: "accepted" as const }));
vi.mock("@/lib/api", () => ({ authApi: { register }, DEMO_MODE: false }));

const { RegisterForm } = await import("./RegisterForm");

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;
let root: Root;

/** Igual que en Next con cacheComponents: la página se oculta con <Activity>, no se desmonta. */
async function render(mode: "visible" | "hidden") {
  await act(async () =>
    root.render(
      <Activity mode={mode}>
        <RegisterForm />
      </Activity>,
    ),
  );
}

async function type(id: string, value: string) {
  const input = container.querySelector<HTMLInputElement>(`#${id}`)!;
  const setValue = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!;
  await act(async () => {
    setValue.call(input, value);
    input.dispatchEvent(new Event("input", { bubbles: true }));
  });
}

describe("RegisterForm con la página preservada por Activity", () => {
  beforeEach(() => {
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
    vi.clearAllMocks();
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
  });

  it("tras registrarse y salir, al volver se ve el formulario vacío y no la confirmación anterior", async () => {
    await render("visible");
    await type("register-email", "ana@example.com");
    await type("register-password", "contrasena-larga-1");
    await type("register-confirm", "contrasena-larga-1");
    await act(async () => {
      container.querySelector("form")!.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
    });
    expect(register).toHaveBeenCalledWith("ana@example.com", "contrasena-larga-1");
    expect(container.textContent).toContain("Solicitud recibida");

    await render("hidden");
    await render("visible");

    expect(container.textContent).not.toContain("Solicitud recibida");
    expect(container.querySelector<HTMLInputElement>("#register-email")!.value).toBe("");
    expect(container.querySelector<HTMLInputElement>("#register-password")!.value).toBe("");
    expect(container.innerHTML).not.toContain("ana@example.com");
  });
});
