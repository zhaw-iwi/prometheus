import { defineConfig } from "@playwright/test";

const port = Number(process.env.PROMETHEUS_DESIGNER_H2_PORT || 18084);
const baseURL = `http://127.0.0.1:${port}`;
const startCommand = process.platform === "win32"
  ? ".\\mvnw.cmd spring-boot:run \"-Dspring-boot.run.useTestClasspath=true\""
  : "./mvnw spring-boot:run -Dspring-boot.run.useTestClasspath=true";

export default defineConfig({
  testDir: "./tests/playwright",
  testMatch: "valerian-designer-h2.spec.mjs",
  outputDir: "test-results/designer-h2",
  timeout: 120_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  reporter: [["list"]],
  use: {
    baseURL,
    viewport: { width: 1440, height: 1000 },
    actionTimeout: 20_000,
    navigationTimeout: 30_000,
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
  },
  webServer: {
    command: startCommand,
    url: `${baseURL}/valerian-design/`,
    timeout: 180_000,
    reuseExistingServer: false,
    env: {
      ...process.env,
      SERVER_PORT: String(port),
      SPRING_DATASOURCE_URL: "jdbc:h2:mem:prometheus_designer_v27;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      SPRING_DATASOURCE_USERNAME: "sa",
      SPRING_DATASOURCE_PASSWORD: "",
      SPRING_DATASOURCE_DRIVER_CLASS_NAME: "org.h2.Driver",
      SPRING_JPA_DATABASE_PLATFORM: "org.hibernate.dialect.H2Dialect",
      SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT: "org.hibernate.dialect.H2Dialect",
      SPRING_JPA_HIBERNATE_DDL_AUTO: "validate",
      PROMETHEUS_ADMIN_TOKEN: "designer-h2-admin-token",
      LOGGING_LEVEL_ROOT: "WARN",
    },
  },
});
