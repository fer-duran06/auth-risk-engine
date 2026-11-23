// @vitest-environment happy-dom
import { Activity, act, type ReactNode } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const router = { replace: vi.fn(), push: vi.fn() };
vi.mock("next/navigation", () => ({ useRouter: () => router }));
vi.mock("next/link", () => ({ default: ({ href, children }: { href: string; children: ReactNode }) => <a href={href}>{children}</a> }));
const login = vi.fn(async () => ({ kind: "ok" as const, token: { accessToken: "tok", expiresIn: 900 } }));
vi.mock("@/lib/api", () => ({ authApi: { login }, DEMO_MODE: false }));
vi.mock("@/lib/session", () => ({ session: { start: vi.fn() }, useSessionStatus: () => "anonymous" }));

const { LoginForm } = await import("./LoginForm");

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;
let root: Root;

/** Igual que en Next con cacheComponents: la página se oculta con <Activity>, no se desmonta. */
async function render(mode: "visible" | "hidden") {
  await act(async () =>
    root.render(
      <Activity mode={mode}>
        <LoginForm />
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

const value = (id: string) => container.querySelector<HTMLInputElement>(`#${id}`)!.value;

describe("LoginForm con la página preservada por Activity", () => {
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

  it("tras un login correcto no quedan el email ni la contraseña para el siguiente usuario", async () => {
    await render("visible");
    await type("login-email", "ana@example.com");
    await type("login-password", "contrasena-de-ana-1");
    await act(async () => {
      container.querySelector("form")!.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
    });
    expect(login).toHaveBeenCalledWith("ana@example.com", "contrasena-de-ana-1");
    expect(router.replace).toHaveBeenCalledWith("/me");

    // Ana navega a /me y luego cierra sesión: /login vuelve a mostrarse.
    await render("hidden");
    await render("visible");

    expect(value("login-email")).toBe("");
    expect(value("login-password")).toBe("");
    expect(container.innerHTML).not.toContain("contrasena-de-ana-1");
  });

  it("al ocultarse se borra lo escrito aunque no se haya enviado", async () => {
    await render("visible");
    await type("login-email", "ana@example.com");
    await type("login-password", "a-medio-escribir");

    await render("hidden");
    await render("visible");

    expect(value("login-email")).toBe("");
    expect(value("login-password")).toBe("");
  });
});
