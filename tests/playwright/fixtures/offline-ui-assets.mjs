import { test as base, expect } from "@playwright/test";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
// Exact versions already referenced by the cockpit. Keep real Bootstrap behaviour
// and integrity checks while making offline acceptance independent of the CDN.
const assets = new Map([
  ["/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css", ["bootstrap/dist/css/bootstrap.min.css", "text/css"]],
  ["/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js", ["bootstrap/dist/js/bootstrap.bundle.min.js", "application/javascript"]],
  ["/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css", ["bootstrap-icons/font/bootstrap-icons.min.css", "text/css"]],
  ["/npm/bootstrap-icons@1.11.3/font/fonts/bootstrap-icons.woff2", ["bootstrap-icons/font/fonts/bootstrap-icons.woff2", "font/woff2"]],
  ["/npm/bootstrap-icons@1.11.3/font/fonts/bootstrap-icons.woff", ["bootstrap-icons/font/fonts/bootstrap-icons.woff", "font/woff"]],
]);

export async function installOfflineUiAssets(context) {
  await context.route("https://cdn.jsdelivr.net/npm/bootstrap*/**", route => {
    const asset = assets.get(new URL(route.request().url()).pathname);
    return asset
      ? route.fulfill({ path: require.resolve(asset[0]), contentType: asset[1], headers: { "Access-Control-Allow-Origin": "*" } })
      : route.abort();
  });
}

export const test = base.extend({
  context: async ({ context }, use) => {
    await installOfflineUiAssets(context);
    await use(context);
  },
});
export { expect };
