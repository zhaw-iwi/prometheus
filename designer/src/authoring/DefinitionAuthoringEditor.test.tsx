import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { DefinitionRevisionView, RequestFunction } from "../api/designerApi";
import type { AgentDefinitionV1 } from "../model/agentDefinition";
import { DefinitionAuthoringEditor, targetForDiagnostic } from "./DefinitionAuthoringEditor";
import { createDefaultDefinition } from "./editorModel";

describe("DefinitionAuthoringEditor persistence", () => {
  it("maps backend graph pointers to stable situation and move targets", () => {
    const definition = createDefaultDefinition();
    definition.transitions.push({ id: "stay", sourceStateId: "main", targetStateId: "main", order: 10, decisions: [], actions: [] });
    expect(targetForDiagnostic({ code: "STATE", severity: "ERROR", pointer: "/states/0/policy", message: "State", hint: null }, definition))
      .toMatchObject({ stepId: "state-flow", fieldId: "graph-diagnostic-state-main" });
    expect(targetForDiagnostic({ code: "EDGE", severity: "ERROR", pointer: "/transitions/0/targetStateId", message: "Edge", hint: null }, definition))
      .toMatchObject({ stepId: "state-flow", fieldId: "graph-diagnostic-transition-stay" });
  });

  it("creates a complete explicit single-state draft and displays backend diagnostics", async () => {
    const user = userEvent.setup();
    const onSaved = vi.fn();
    const request = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      const body = init?.body ? JSON.parse(String(init.body)) as { definition: AgentDefinitionV1 } : null;
      if (init?.method === "POST" && String(_input).endsWith("/validation")) {
        return response({ valid: true, diagnostics: [{
          code: "UNUSED_OBSERVATION", severity: "WARNING", pointer: "/interaction/supportedObservations/0",
          message: "The observation is selected but unused.", hint: "Use it in a reaction.",
        }] });
      }
      if (init?.method === "POST") return response(revision(body!.definition, 0), 201);
      throw new Error("Unexpected request");
    }) as RequestFunction;
    render(<DefinitionAuthoringEditor route={{ kind: "new" }} components={[]} adminToken="token"
      request={request} onDirtyChange={() => undefined} onSaved={onSaved} />);

    await user.type(screen.getByLabelText(/What should this agent be called/), "Focused coach");
    await user.type(screen.getByLabelText(/What is it intended to accomplish/), "Helps select one next step.");
    await user.click(screen.getByTestId("use-key-suggestion"));
    await user.click(screen.getByLabelText("I confirm this stable key for the first save."));
    await user.type(screen.getByTestId("prompt-purpose-objective"), "Guide one concrete next step.");
    await user.click(screen.getByTestId("save-draft"));

    expect(await screen.findByTestId("backend-diagnostics")).not.toBeNull();
    expect(screen.getByText("The observation is selected but unused.")).not.toBeNull();
    expect(onSaved).toHaveBeenCalledWith("designer.focused_coach", 1);
    const posted = JSON.parse(String((request as ReturnType<typeof vi.fn>).mock.calls[0][1]?.body)).definition;
    expect(posted.lifecycle.initialStateId).toBe("main");
    expect(posted.states[0]).toMatchObject({ id: "main", kind: "atomic" });
    expect(posted.states[0].policy.config.responsePrompt.sections[0]).toMatchObject({
      id: "purpose.objective",
      content: "Guide one concrete next step.",
    });
  });

  it("loads the newest optimistic version after conflict and requires an explicit local overwrite retry", async () => {
    const user = userEvent.setup();
    const source = validDefinition("designer.concurrent", "Original");
    const server = validDefinition("designer.concurrent", "Server change");
    let getCount = 0;
    let putCount = 0;
    const request = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      if (!init?.method) {
        getCount += 1;
        return response(revision(getCount === 1 ? source : server, getCount === 1 ? 1 : 2));
      }
      if (init.method === "PUT") {
        putCount += 1;
        if (putCount === 1) return response({ code: "OPTIMISTIC_CONFLICT", diagnostics: [] }, 409);
        const body = JSON.parse(String(init.body)) as { definition: AgentDefinitionV1; optimisticVersion: number };
        expect(body.optimisticVersion).toBe(2);
        expect(body.definition.metadata.displayName).toBe("Local change");
        return response(revision(body.definition, 3));
      }
      if (String(input).endsWith("/validation")) return response({ valid: true, diagnostics: [] });
      throw new Error("Unexpected request");
    }) as RequestFunction;
    render(<DefinitionAuthoringEditor route={{ kind: "editor", key: source.key, revision: 1 }} components={[]}
      adminToken="token" request={request} onDirtyChange={() => undefined} onSaved={() => undefined} />);

    const name = await screen.findByLabelText(/What should this agent be called/);
    await user.clear(name);
    await user.type(name, "Local change");
    await user.click(screen.getByTestId("save-draft"));
    expect(await screen.findByTestId("optimistic-conflict")).not.toBeNull();

    await user.click(screen.getByText("Keep local changes"));
    expect(screen.getByTestId("save-message").textContent).toContain("Save again");
    await user.click(screen.getByTestId("save-draft"));

    await waitFor(() => expect(screen.getByTestId("save-message").textContent).toContain("validation passed"));
    expect(putCount).toBe(2);
  });

  it("marks form edits dirty and installs an unsaved browser-navigation warning", async () => {
    const user = userEvent.setup();
    const onDirtyChange = vi.fn();
    render(<DefinitionAuthoringEditor route={{ kind: "new" }} components={[]} adminToken="token"
      request={vi.fn() as RequestFunction} onDirtyChange={onDirtyChange} onSaved={() => undefined} />);

    await user.type(screen.getByLabelText(/What should this agent be called/), "Unsaved");
    const event = new Event("beforeunload", { cancelable: true });
    window.dispatchEvent(event);

    expect(screen.getByTestId("dirty-state").textContent).toBe("Unsaved changes");
    expect(event.defaultPrevented).toBe(true);
    expect(onDirtyChange).toHaveBeenLastCalledWith(true);
  });
});

function validDefinition(key: string, name: string): AgentDefinitionV1 {
  const definition = createDefaultDefinition();
  definition.key = key;
  definition.metadata.displayName = name;
  definition.metadata.description = "A valid draft used by the editor test.";
  return definition;
}

function revision(definition: AgentDefinitionV1, optimisticVersion: number): DefinitionRevisionView {
  return {
    id: 1,
    key: definition.key,
    revision: definition.revision,
    schemaVersion: 1,
    status: "DRAFT",
    contentHash: "a".repeat(64),
    provenance: "DESIGNER",
    sourceDetail: "test",
    optimisticVersion,
    createdAt: "2026-08-30T00:00:00Z",
    updatedAt: "2026-08-30T00:00:00Z",
    publishedAt: null,
    archivedAt: null,
    definition,
  };
}

function response(payload: unknown, status = 200): Response {
  return { ok: status >= 200 && status < 300, status, json: async () => payload } as Response;
}
