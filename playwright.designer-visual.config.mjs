import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests/playwright",
  testMatch: "valerian-designer-visual.spec.mjs",
  outputDir: "test-results/designer-visual",
  timeout: 90_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  reporter: [["list"]],
  use: {
    baseURL: "http://127.0.0.1:4175",
    viewport: { width: 1440, height: 1000 },
    colorScheme: "light",
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
  },
  webServer: {
    command: "npm run designer:dev -- --host 127.0.0.1 --port 4175 --strictPort",
    url: "http://127.0.0.1:4175/valerian-design/",
    timeout: 120_000,
    reuseExistingServer: false,
  },
});
