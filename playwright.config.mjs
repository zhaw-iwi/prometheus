import { defineConfig } from "@playwright/test";

const baseURL = process.env.PROMETHEUS_BASE_URL || "http://127.0.0.1:8080";
const startCommand = process.platform === "win32"
  ? ".\\mvnw.cmd spring-boot:run"
  : "./mvnw spring-boot:run";

export default defineConfig({
  testDir: "./tests/playwright",
  timeout: 90_000,
  expect: {
    timeout: 10_000,
  },
  fullyParallel: false,
  workers: 1,
  reporter: [["list"]],
  use: {
    baseURL,
    viewport: { width: 1440, height: 1000 },
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
  },
  webServer: process.env.PROMETHEUS_SKIP_WEBSERVER === "true"
    ? undefined
    : {
        command: startCommand,
        url: baseURL,
        timeout: 180_000,
        reuseExistingServer: true,
      },
});
