import { defineConfig } from "@playwright/test";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const baseURL = process.env.PARTICIPATE_BASE_URL || "http://127.0.0.1:8091";
const rootDir = dirname(fileURLToPath(import.meta.url));
const envFile = resolve(rootDir, ".web/participate/.env.test");
const startCommand = "php .web/participate/tests/setup_test_db.php && php -S 127.0.0.1:8091 -t .web/participate";

export default defineConfig({
  testDir: "./tests/playwright",
  testMatch: "participate.spec.mjs",
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
  webServer: process.env.PARTICIPATE_SKIP_WEBSERVER === "true"
    ? undefined
    : {
        command: startCommand,
        url: baseURL,
        env: {
          ...process.env,
          PARTICIPATE_ENV_FILE: envFile,
        },
        timeout: 30_000,
        reuseExistingServer: false,
      },
});
