import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type {
  DefinitionRevisionView,
  DefinitionSummary,
  PreviewSnapshot,
  RequestFunction,
} from "../api/designerApi";
import { createDefaultDefinition } from "../v2/projection";
import type { AgentDefinitionV1 } from "../model/agentDefinition";
import { ReviewPanel } from "./ReviewPanel";

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe("ReviewPanel", () => {
  it("preserves the projected document on parse failure and applies a valid JSON edit through backend validation", async () => {
    const user = userEvent.setup();
    const definition = validDefinition();
    const apply = vi.fn()
      .mockResolvedValueOnce({ applied: true, message: "Applied safely." })
      .mockResolvedValueOnce({ applied: false, message: "Backend rejected the edit; the current document was preserved." });
    renderPanel({ definition, request: promptRequest(), onApplyDefinition: apply });

    fireEvent.change(screen.getByTestId("canonical-json-editor"), { target: { value: '{\n  "key":' } });
    await user.click(screen.getByTestId("apply-canonical-json"));
    expect((await screen.findByTestId("json-message")).textContent).toContain("projected document was not changed");
    expect(apply).not.toHaveBeenCalled();

    const changed = structuredClone(definition);
    changed.metadata.displayName = "Edited in JSON";
    fireEvent.change(screen.getByTestId("canonical-json-editor"), { target: { value: JSON.stringify(changed) } });
    await user.click(screen.getByTestId("apply-canonical-json"));
    await waitFor(() => expect(apply).toHaveBeenCalledWith(changed));
    expect(screen.getByTestId("json-message").textContent).toContain("Applied safely");

    const rejected = structuredClone(definition);
    rejected.metadata.displayName = "Rejected name";
    fireEvent.change(screen.getByTestId("canonical-json-editor"), { target: { value: JSON.stringify(rejected) } });
    await user.click(screen.getByTestId("apply-canonical-json"));
    await waitFor(() => expect(screen.getByTestId("json-message").textContent).toContain("current document was preserved"));
    expect(screen.getByTestId("review-narrative").textContent).toContain("Exercises review and publication");
    expect(screen.getByTestId("review-narrative").textContent).not.toContain("Rejected name");
  });

  it("runs a disposable preview, renders state and trace changes, resets, and cleans up on unmount", async () => {
    const user = userEvent.setup();
    const definition = validDefinition();
    definition.interaction.supportedObservations = ["obs.user_utterance"];
    const request = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path.endsWith("prompt-previews")) return response([]);
      if (path === "/admin/agent-definitions/previews" && init?.method === "POST") return response(snapshot("CREATE"), 201);
      if (path.endsWith("/events")) return response(snapshot("EVENT"));
      if (path.endsWith("/reset")) return response(snapshot("RESET"));
      if (path.endsWith("preview-1") && init?.method === "DELETE") return response(undefined, 204);
      throw new Error(`Unexpected ${path}`);
    }) as RequestFunction;
    const rendered = renderPanel({ definition, request });

    await user.click(screen.getByTestId("start-preview"));
    expect((await screen.findByTestId("preview-workspace")).textContent).toContain("main");
    await user.click(screen.getByText("What the person says"));
    await user.type(screen.getByTestId("preview-event-payload"), "hello");
    await user.click(screen.getByTestId("send-preview-event"));
    await waitFor(() => expect(screen.getByTestId("preview-transcript").textContent).toContain("EVENT"));
    expect(screen.getByTestId("preview-transcript").textContent).toContain("stay");
    expect(screen.getByTestId("preview-transcript").textContent).toContain("counter");

    await user.click(screen.getByTestId("reset-preview"));
    await waitFor(() => expect(screen.getByTestId("preview-transcript").textContent).toContain("RESET"));
    rendered.unmount();
    await waitFor(() => expect(request).toHaveBeenCalledWith("/admin/agent-definitions/previews/preview-1",
      expect.objectContaining({ method: "DELETE" })));
  });

  it("gates publication on current validation and explains activation, export, clone, and archive consequences", async () => {
    const user = userEvent.setup();
    const definition = validDefinition();
    const published = revision(definition, "PUBLISHED", 4);
    const onRevisionChange = vi.fn();
    const onWorkspaceChanged = vi.fn();
    const confirm = vi.fn().mockReturnValue(true);
    vi.stubGlobal("confirm", confirm);
    vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:export");
    vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => undefined);
    vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => undefined);
    const request = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path.endsWith("prompt-previews")) return response([]);
      if (path.endsWith("/publish")) return response(published);
      if (path.endsWith("/activate")) return response({ ...summary(definition), activeRevision: 1 });
      if (path.endsWith("/export")) return response(definition);
      if (path.endsWith("/clone")) return response(revision({ ...definition, revision: 2 }, "DRAFT", 0), 201);
      if (path.endsWith("/archive")) return response({ ...published, status: "ARCHIVED" });
      throw new Error(`Unexpected ${path} ${init?.method}`);
    }) as RequestFunction;
    const props = panelProps({ definition, persisted: revision(definition, "DRAFT", 3),
      definitionSummary: summary(definition), request, dirty: false, validationCurrent: false,
      onRevisionChange, onWorkspaceChanged });
    const rendered = render(<ReviewPanel {...props} />);
    expect((screen.getByTestId("publish-revision") as HTMLButtonElement).disabled).toBe(true);

    rendered.rerender(<ReviewPanel {...props} validationCurrent />);
    expect((screen.getByTestId("publish-revision") as HTMLButtonElement).disabled).toBe(false);
    await user.click(screen.getByTestId("publish-revision"));
    await waitFor(() => expect(onRevisionChange).toHaveBeenCalledWith(published));
    expect(confirm.mock.calls[0][0]).toContain("does not activate");

    rendered.rerender(<ReviewPanel {...props} persisted={published} validationCurrent />);
    await user.click(screen.getByTestId("activate-revision"));
    await waitFor(() => expect(screen.getByTestId("lifecycle-message").textContent).toContain("new instances only"));
    expect(confirm.mock.calls[1][0]).toContain("Existing instances remain pinned");

    await user.click(screen.getByTestId("export-revision"));
    await waitFor(() => expect(screen.getByTestId("lifecycle-message").textContent).toContain("Exported canonical"));
    await user.click(screen.getByTestId("clone-revision"));
    await waitFor(() => expect(onWorkspaceChanged).toHaveBeenCalledWith({ key: definition.key, revision: 2 }));
    await user.click(screen.getByTestId("archive-revision"));
    await waitFor(() => expect(screen.getByTestId("lifecycle-message").textContent).toContain("archived"));
  });

  it("refreshes backend-composed prompt pointers after scoped guidance changes", async () => {
    const definition = validDefinition();
    const changed = structuredClone(definition);
    const context = changed.states[0];
    if (context.kind === "final" || !context.policy) throw new Error("Expected context policy");
    context.policy.config.responsePrompt = {
      sections: [{ id: "objective", kind: "objective", content: "Help with the changed goal." }],
    };
    const request = vi.fn()
      .mockResolvedValueOnce(response([{ pointer: "/states/0/policy/config/responsePrompt", label: "Agent-wide response", composed: "Original" }]))
      .mockResolvedValueOnce(response([{ pointer: "/states/0/policy/config/responsePrompt", label: "Agent-wide response", composed: "Help with the changed goal." }])) as RequestFunction;
    const rendered = renderPanel({ definition, request });
    await waitFor(() => expect(screen.getByText("Original")).not.toBeNull());

    rendered.rerender(<ReviewPanel {...panelProps({ definition: changed, request })} />);
    await waitFor(() => expect(screen.getByText("Help with the changed goal.")).not.toBeNull());
    expect(screen.getByText("/states/0/policy/config/responsePrompt")).not.toBeNull();
    expect(request).toHaveBeenCalledTimes(2);
  });
});

function renderPanel(overrides: Partial<ReturnType<typeof panelProps>> = {}) {
  return render(<ReviewPanel {...panelProps(overrides)} />);
}

function panelProps(overrides: Record<string, unknown> = {}) {
  const definition = (overrides.definition as AgentDefinitionV1 | undefined) ?? validDefinition();
  return {
    definition,
    persisted: null,
    definitionSummary: undefined,
    diagnostics: [],
    active: true,
    dirty: true,
    validationCurrent: false,
    adminToken: "token",
    request: promptRequest(),
    onValidate: vi.fn().mockResolvedValue(true),
    onApplyDefinition: vi.fn().mockResolvedValue({ applied: true, message: "Applied." }),
    onDiagnosticSelect: vi.fn(),
    onRevisionChange: vi.fn(),
    onWorkspaceChanged: vi.fn(),
    ...overrides,
  };
}

function validDefinition(): AgentDefinitionV1 {
  const definition = createDefaultDefinition();
  definition.key = "designer.review";
  definition.metadata.displayName = "Review agent";
  definition.metadata.description = "Exercises review and publication.";
  return definition;
}

function revision(definition: AgentDefinitionV1, status: DefinitionRevisionView["status"], optimisticVersion: number): DefinitionRevisionView {
  return {
    id: 11, key: definition.key, revision: definition.revision, schemaVersion: 1, status,
    contentHash: "a".repeat(64), provenance: "DESIGNER", sourceDetail: "test", optimisticVersion,
    createdAt: "2026-08-30T10:00:00Z", updatedAt: "2026-08-30T10:00:00Z",
    publishedAt: status === "DRAFT" ? null : "2026-08-30T10:01:00Z", archivedAt: null, definition,
  };
}

function summary(definition: AgentDefinitionV1): DefinitionSummary {
  return {
    key: definition.key, activeRevisionId: null, activeRevision: null, optimisticVersion: 8,
    displayName: definition.metadata.displayName, description: definition.metadata.description,
    categoryPath: ["designer"], languageCode: "en", revisions: [{
      id: 11, revision: 1, status: "DRAFT", provenance: "DESIGNER", optimisticVersion: 3,
      updatedAt: "2026-08-30T10:00:00Z",
    }],
  };
}

function snapshot(kind: "CREATE" | "EVENT" | "RESET"): PreviewSnapshot {
  return {
    id: "preview-1", source: "UNSAVED", storedRevisionId: null, definitionKey: "designer.review", definitionRevision: 1,
    createdAt: "2026-08-30T10:00:00Z", lastAccessedAt: "2026-08-30T10:00:01Z", expiresAt: "2026-08-30T10:15:00Z",
    activeStatePath: ["main"], storage: kind === "EVENT" ? { counter: 1 } : {}, history: [], started: true, active: true,
    transcript: [{
      sequence: 1, kind, at: "2026-08-30T10:00:01Z",
      input: kind === "EVENT" ? { type: "obs.user_utterance", actor: "user", kind: "observation", payload: "hello" } : null,
      activeStatePath: ["main"], storageChanges: kind === "EVENT" ? { counter: { before: 0, after: 1 } } : {},
      acceptedTransitionIds: kind === "EVENT" ? ["stay"] : [], behaviour: null, diagnostics: [],
    }], diagnostics: [],
  };
}

function promptRequest(): RequestFunction {
  return vi.fn().mockResolvedValue(response([]));
}

function response(payload: unknown, status = 200): Response {
  return { ok: status >= 200 && status < 300, status, json: async () => payload } as Response;
}
