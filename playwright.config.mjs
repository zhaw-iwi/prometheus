import { defineConfig } from "@playwright/test";

const baseURL = process.env.PROMETHEUS_BASE_URL || "http://127.0.0.1:8080";
const startCommand = process.platform === "win32"
  ? ".\\mvnw.cmd spring-boot:run \"-Dspring-boot.run.useTestClasspath=true\""
  : "./mvnw spring-boot:run -Dspring-boot.run.useTestClasspath=true";

const isolatedTestEnvironment = {
  ...process.env,
  SPRING_DATASOURCE_URL: "jdbc:h2:mem:prometheus_playwright;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
  SPRING_DATASOURCE_USERNAME: "sa",
  SPRING_DATASOURCE_PASSWORD: "",
  SPRING_DATASOURCE_DRIVER_CLASS_NAME: "org.h2.Driver",
  SPRING_JPA_DATABASE_PLATFORM: "org.hibernate.dialect.H2Dialect",
  SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT: "org.hibernate.dialect.H2Dialect",
  SPRING_JPA_HIBERNATE_DDL_AUTO: "validate",
  PROMETHEUS_ADMIN_TOKEN: process.env.PROMETHEUS_ADMIN_TOKEN || "laure",
};

export default defineConfig({
  testDir: "./tests/playwright",
  testIgnore: ["**/participate.spec.mjs", "**/valerian-designer-*.spec.mjs"],
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
        reuseExistingServer: false,
        env: isolatedTestEnvironment,
      },
});
