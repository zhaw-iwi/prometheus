import { describe, expect, it, vi } from "vitest";
import { ADMIN_TOKEN_HEADER, DesignerApiError, fetchDefinitionCatalog } from "./designerApi";

function response(payload: unknown, status = 200): Response {
  return { ok: status >= 200 && status < 300, status, json: async () => payload } as Response;
}

describe("designer API", () => {
  it("loads the catalog through the existing admin-token header and a same-origin path", async () => {
    const request = vi.fn().mockResolvedValue(response([]));

    await expect(fetchDefinitionCatalog("session-token", request)).resolves.toEqual([]);

    expect(request).toHaveBeenCalledWith("/admin/agent-definitions", {
      headers: { [ADMIN_TOKEN_HEADER]: "session-token" },
    });
  });

  it("maps unauthorized responses without exposing response content", async () => {
    const request = vi.fn().mockResolvedValue(response({ detail: "provider-secret" }, 401));

    await expect(fetchDefinitionCatalog("wrong", request)).rejects.toEqual(new DesignerApiError(401));
  });
});
