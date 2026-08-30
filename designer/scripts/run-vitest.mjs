import { spawnSync } from "node:child_process";
import { realpathSync } from "node:fs";
import { resolve } from "node:path";

// Maven preserves the spelling supplied through -f. On Windows, Vitest 4.1.x
// can split its runner context when that spelling uses a lower-case drive
// letter while imported modules resolve through the canonical upper-case path.
// See vitest-dev/vitest#10692.
const canonicalWorkingDirectory = realpathSync.native(process.cwd());
const vitestEntry = resolve(canonicalWorkingDirectory, "node_modules", "vitest", "vitest.mjs");
const result = spawnSync(process.execPath, [
  vitestEntry,
  "run",
  "--config",
  "designer/vite.config.ts",
  ...process.argv.slice(2),
], {
  cwd: canonicalWorkingDirectory,
  env: process.env,
  stdio: "inherit",
});

if (result.error) throw result.error;
process.exitCode = result.status ?? 1;
