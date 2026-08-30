import { describe, expect, it, vi } from "vitest";
import type { AgentDefinitionV1 } from "../model/agentDefinition";
import { createDefaultDefinition } from "../authoring/editorModel";
import {
  ADMIN_TOKEN_HEADER,
  activateDefinitionRevision,
  archiveDefinitionRevision,
  cloneDefinitionRevision,
  closeDefinitionPreview,
  createDefinitionDraft,
  createDefinitionPreview,
  DesignerApiError,
  fetchDefinitionCatalog,
  fetchPromptPreviews,
  fetchDesignerWorkspace,
  generatePreviewBehaviour,
  importDefinitionDraft,
  publishDefinitionRevision,
  resetDefinitionPreview,
  submitPreviewEvent,
  updateDefinitionDraft,
  validateDefinition,
  validateDefinitionForPublication,
} from "./designerApi";

function response(payload: unknown, status = 200): Response {
  return { ok: status >= 200 && status < 300, status, json: async () => payload } as Response;
}

describe("designer API", () => {
  it("loads the catalog through the existing admin-token header and a same-origin path", async () => {
    const request = vi.fn().mockResolvedValue(response([]));

    await expect(fetchDefinitionCatalog("session-token", request)).resolves.toEqual([]);

    expect(request).toHaveBeenCalledTimes(1);
    expect(request.mock.calls[0][0]).toBe("/admin/agent-definitions");
    expect(new Headers(request.mock.calls[0][1]?.headers).get(ADMIN_TOKEN_HEADER)).toBe("session-token");
  });

  it("maps unauthorized responses without exposing response content", async () => {
    const request = vi.fn().mockResolvedValue(response({ detail: "provider-secret" }, 401));

    await expect(fetchDefinitionCatalog("wrong", request)).rejects.toMatchObject({
      status: 401,
      code: "UNAUTHORIZED",
      message: new DesignerApiError(401).message,
      diagnostics: [],
    });
  });

  it("loads definitions and registered components as one workspace", async () => {
    const request = vi.fn((input: RequestInfo | URL) => Promise.resolve(response(
      String(input).endsWith("component-catalog") ? [{ kind: "prometheus.policy.prompt" }] : [{ key: "designer.test" }],
    )));

    const workspace = await fetchDesignerWorkspace("token", request);

    expect(workspace.definitions[0].key).toBe("designer.test");
    expect(workspace.components[0].kind).toBe("prometheus.policy.prompt");
  });

  it("maps create, update, and validation requests to the lifecycle API", async () => {
    const definition: AgentDefinitionV1 = createDefaultDefinition();
    definition.key = "designer.test";
    const revision = { key: definition.key, revision: 1, definition, optimisticVersion: 4 };
    const request = vi.fn()
      .mockResolvedValueOnce(response(revision, 201))
      .mockResolvedValueOnce(response(revision))
      .mockResolvedValueOnce(response({ valid: true, diagnostics: [] }));

    await createDefinitionDraft(definition, "token", request);
    await updateDefinitionDraft(definition, 4, "token", request);
    await validateDefinition(definition, "token", request);

    expect(request.mock.calls.map((call) => [call[0], call[1]?.method])).toEqual([
      ["/admin/agent-definitions", "POST"],
      ["/admin/agent-definitions/designer.test/revisions/1", "PUT"],
      ["/admin/agent-definitions/validation", "POST"],
    ]);
    expect(JSON.parse(String(request.mock.calls[1][1]?.body)).optimisticVersion).toBe(4);
    expect(new Headers(request.mock.calls[2][1]?.headers).get("Content-Type")).toBe("application/json");
  });

  it("maps prompt, lifecycle, import, and disposable preview requests without inventing auth", async () => {
    const definition = createDefaultDefinition();
    definition.key = "designer.review";
    const preview = { id: "preview-1", transcript: [] };
    const request = vi.fn()
      .mockResolvedValueOnce(response([]))
      .mockResolvedValueOnce(response({ valid: true, diagnostics: [] }))
      .mockResolvedValueOnce(response({ definition }, 201))
      .mockResolvedValueOnce(response({ status: "PUBLISHED" }))
      .mockResolvedValueOnce(response({ activeRevision: 1 }))
      .mockResolvedValueOnce(response({ status: "ARCHIVED" }))
      .mockResolvedValueOnce(response({ key: "designer.clone", revision: 2 }, 201))
      .mockResolvedValueOnce(response(preview, 201))
      .mockResolvedValueOnce(response(preview))
      .mockResolvedValueOnce(response(preview))
      .mockResolvedValueOnce(response(preview))
      .mockResolvedValueOnce({ ok: true, status: 204 });

    await fetchPromptPreviews(definition, "token", request);
    await validateDefinitionForPublication(definition, "token", request);
    await importDefinitionDraft(definition, "token", request);
    await publishDefinitionRevision(definition.key, 1, 3, "token", request);
    await activateDefinitionRevision(definition.key, 1, 7, "token", request);
    await archiveDefinitionRevision(definition.key, 1, 4, "token", request);
    await cloneDefinitionRevision(definition.key, 1, "designer.clone", 2, "token", request);
    await createDefinitionPreview(definition, "token", request);
    await submitPreviewEvent("preview-1", { type: "obs.text", actor: "user", kind: "observation", payload: "hello" }, "token", request);
    await generatePreviewBehaviour("preview-1", "token", request);
    await resetDefinitionPreview("preview-1", "token", request);
    await closeDefinitionPreview("preview-1", "token", request);

    expect(request.mock.calls.map((call) => [call[0], call[1]?.method])).toEqual([
      ["/admin/agent-definitions/prompt-previews", "POST"],
      ["/admin/agent-definitions/publication-readiness", "POST"],
      ["/admin/agent-definitions/imports", "POST"],
      ["/admin/agent-definitions/designer.review/revisions/1/publish", "POST"],
      ["/admin/agent-definitions/designer.review/revisions/1/activate", "POST"],
      ["/admin/agent-definitions/designer.review/revisions/1/archive", "POST"],
      ["/admin/agent-definitions/designer.review/revisions/1/clone", "POST"],
      ["/admin/agent-definitions/previews", "POST"],
      ["/admin/agent-definitions/previews/preview-1/events", "POST"],
      ["/admin/agent-definitions/previews/preview-1/generate", "POST"],
      ["/admin/agent-definitions/previews/preview-1/reset", "POST"],
      ["/admin/agent-definitions/previews/preview-1", "DELETE"],
    ]);
    expect(JSON.parse(String(request.mock.calls[4][1]?.body))).toEqual({ optimisticVersion: 7 });
    for (const call of request.mock.calls) {
      expect(new Headers(call[1]?.headers).get(ADMIN_TOKEN_HEADER)).toBe("token");
    }
  });
});
