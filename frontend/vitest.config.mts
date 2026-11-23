import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

// Pruebas unitarias en Node; las de componentes (*.test.tsx) declaran happy-dom en el propio archivo.
export default defineConfig({
  resolve: {
    alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
  },
  test: {
    environment: "node",
    include: ["src/**/*.test.{ts,tsx}"],
  },
});
