import { fileURLToPath, URL } from "node:url";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

const root = fileURLToPath(new URL(".", import.meta.url));

export default defineConfig({
  root,
  base: "/valerian-design/",
  plugins: [react()],
  build: {
    outDir: fileURLToPath(new URL("../target/generated-resources/public/valerian-design", import.meta.url)),
    emptyOutDir: true,
    sourcemap: false,
  },
  test: {
    environment: "happy-dom",
    globals: true,
    setupFiles: ["./src/test/setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
    restoreMocks: true,
  },
});
