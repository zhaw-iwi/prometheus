import { readFileSync, readdirSync } from "node:fs";
import { resolve } from "node:path";
import { spawn, spawnSync } from "node:child_process";

const ENABLE_ENV = "PROMETHEUS_DESIGNER_DB_SMOKE";
const SCHEMA_ENV = "PROMETHEUS_DESIGNER_DB_SMOKE_SCHEMA";
const ADMIN_TOKEN = "designer-live-admin-token";

export default async function globalSetup() {
  const target = requiredTarget();
  runMaven(["--batch-mode", "--no-transfer-progress", "-DskipTests", "package"]);
  runFixture("prepareDedicatedSchema");
  let application;
  try {
    application = startPackagedApplication(target);
    await waitForApplication(application);
  } catch (failure) {
    stopApplication(application);
    runFixture("removeDedicatedSchema");
    throw failure;
  }

  return async () => {
    stopApplication(application);
    runFixture("removeDedicatedSchema");
  };
}

function requiredTarget() {
  if (process.env[ENABLE_ENV]?.toLowerCase() !== "true") {
    throw new Error(`${ENABLE_ENV} must be exactly true for the dedicated live designer smoke`);
  }
  const schema = process.env[SCHEMA_ENV] ?? "";
  if (!/^prometheus_designer_smoke_[a-z0-9_]+$/.test(schema)) {
    throw new Error(`${SCHEMA_ENV} must name a prometheus_designer_smoke_* schema`);
  }
  const properties = parseProperties(readFileSync(
    resolve("src/main/resources/application.properties"), "utf8"));
  const normalUrl = required(properties, "spring.datasource.url");
  const username = required(properties, "spring.datasource.username");
  const password = required(properties, "spring.datasource.password");
  const lowerUrl = normalUrl.toLowerCase();
  if (!normalUrl.startsWith("jdbc:mysql://") || lowerUrl.includes("password=") || lowerUrl.includes("user=")) {
    throw new Error("Configured datasource must be MySQL with separate credentials");
  }
  const remainder = normalUrl.slice("jdbc:mysql://".length);
  const slash = remainder.indexOf("/");
  if (slash < 1 || slash === remainder.length - 1) throw new Error("Configured MySQL URL is incomplete");
  const authority = remainder.slice(0, slash);
  const databaseAndQuery = remainder.slice(slash + 1);
  const queryIndex = databaseAndQuery.indexOf("?");
  const normalSchema = queryIndex < 0 ? databaseAndQuery : databaseAndQuery.slice(0, queryIndex);
  const query = queryIndex < 0 ? "" : databaseAndQuery.slice(queryIndex);
  if (!normalSchema || normalSchema.toLowerCase() === schema.toLowerCase()) {
    throw new Error("Dedicated smoke schema must differ from the normal database");
  }
  return { url: `jdbc:mysql://${authority}/${schema}${query}`, username, password };
}

function startPackagedApplication(target) {
  const jar = readdirSync(resolve("target"))
    .filter((name) => /^prometheus-.*\.jar$/.test(name) && !name.endsWith(".original"))
    .sort()
    .at(-1);
  if (!jar) throw new Error("Packaged PROMETHEUS JAR was not created");
  const port = process.env.PROMETHEUS_DESIGNER_LIVE_PORT || "18083";
  const child = spawn("java", ["-jar", resolve("target", jar)], {
    detached: process.platform !== "win32",
    windowsHide: true,
    stdio: ["ignore", "pipe", "pipe"],
    env: {
      ...process.env,
      SERVER_PORT: port,
      SPRING_DATASOURCE_URL: target.url,
      SPRING_DATASOURCE_USERNAME: target.username,
      SPRING_DATASOURCE_PASSWORD: target.password,
      SPRING_JPA_HIBERNATE_DDL_AUTO: "validate",
      SPRING_JPA_SHOW_SQL: "false",
      SPRING_JPA_OPEN_IN_VIEW: "false",
      PROMETHEUS_ADMIN_TOKEN: ADMIN_TOKEN,
      LOGGING_LEVEL_ROOT: "WARN",
    },
  });
  child.stdout.resume();
  child.stderr.resume();
  return child;
}

async function waitForApplication(application) {
  const port = process.env.PROMETHEUS_DESIGNER_LIVE_PORT || "18083";
  const deadline = Date.now() + 180_000;
  while (Date.now() < deadline) {
    if (application.exitCode !== null) {
      throw new Error(`Packaged application exited before startup (code ${application.exitCode})`);
    }
    try {
      const response = await fetch(`http://127.0.0.1:${port}/valerian-design/index.html`);
      if (response.ok && (await response.text()).includes("Valerian Designer")) return;
    } catch {
      // Startup polling intentionally hides transient connection details.
    }
    await new Promise((resolveDelay) => setTimeout(resolveDelay, 500));
  }
  throw new Error("Packaged application did not become ready within 180 seconds");
}

function stopApplication(application) {
  if (!application || application.exitCode !== null) return;
  if (process.platform === "win32") {
    spawnSync("taskkill", ["/pid", String(application.pid), "/T", "/F"], { windowsHide: true, stdio: "ignore" });
  } else {
    try { process.kill(-application.pid, "SIGTERM"); } catch { application.kill("SIGTERM"); }
  }
}

function runFixture(method) {
  runMaven(["--batch-mode", "--no-transfer-progress", "-Plocal-db-smoke",
    `-Dtest=DesignerLiveMysqlFixtureTest#${method}`, "test"]);
}

function runMaven(args) {
  const windows = process.platform === "win32";
  const command = windows ? (process.env.ComSpec || "cmd.exe") : resolve("mvnw");
  const commandArgs = windows ? ["/d", "/s", "/c", "mvnw.cmd", ...args] : args;
  const result = spawnSync(command, commandArgs, {
    cwd: resolve("."), env: process.env, windowsHide: true,
    stdio: "inherit",
  });
  if (result.status !== 0) throw new Error(`Maven verification command failed with exit code ${result.status}`);
}

function parseProperties(source) {
  return Object.fromEntries(source.split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith("#") && !line.startsWith("!"))
    .map((line) => {
      const separator = line.search(/[:=]/);
      return separator < 0 ? [line, ""] : [line.slice(0, separator).trim(), line.slice(separator + 1).trim()];
    }));
}

function required(properties, name) {
  const value = properties[name];
  if (!value) throw new Error(`Required local datasource property is unavailable: ${name}`);
  return value;
}
