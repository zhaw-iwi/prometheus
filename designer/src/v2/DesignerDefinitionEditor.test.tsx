import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { DefinitionRevisionView, RequestFunction } from "../api/designerApi";
import type { AgentDefinitionV1 } from "../model/agentDefinition";
import { targetForDiagnostic } from "./diagnostics";
import { DesignerDefinitionEditor } from "./DesignerDefinitionEditor";
import { createDefaultDefinition } from "./projection";

describe("DesignerDefinitionEditor V2 shell", () => {
  it("maps backend pointers to the six V2 steps and stable projected targets", () => {
    const definition = validDefinition("designer.targets", "Targets");
    definition.storage.push({ key: "result" });
    definition.lifecycle.initializers.push({
      kind: "prometheus.initializer.random-choice", version: 1,
      config: { storageKey: "result", choicesResourceId: "result-choices" },
    });
    definition.resources.push({ id: "result-choices", kind: "prometheus.resource.typed-choices", version: 1, config: { values: ["ok"] } });
    definition.transitions.push({
      id: "stay", sourceStateId: "main", targetStateId: "main", order: 10, decisions: [],
      actions: [
        { kind: "prometheus.action.prompt-behaviour", version: 1, config: {} },
        { kind: "prometheus.action.extract", version: 1, config: { targetStorageKey: "result" } },
      ],
    });
    expect(targetForDiagnostic(diagnostic("/metadata/displayName"), definition)).toMatchObject({ stepId: "brief", fieldId: "brief-display-name" });
    expect(targetForDiagnostic(diagnostic("/interaction/supportedObservations/0"), definition)).toMatchObject({ stepId: "capabilities" });
    expect(targetForDiagnostic(diagnostic("/states/1/policy"), definition)).toMatchObject({ stepId: "interaction", fieldId: "interaction-situation-main" });
    expect(targetForDiagnostic(diagnostic("/transitions/0/decisions/1"), definition)).toMatchObject({ stepId: "interaction", fieldId: "interaction-rule-stay-condition-1" });
    expect(targetForDiagnostic(diagnostic("/transitions/0/actions/0"), definition)).toMatchObject({ stepId: "interaction", fieldId: "interaction-rule-stay-effect-0" });
    expect(targetForDiagnostic(diagnostic("/storage/0/valueSchema"), definition)).toMatchObject({ stepId: "data-outcome", fieldId: "data-item-result" });
    expect(targetForDiagnostic(diagnostic("/lifecycle/initializers/0/config/storageKey"), definition)).toMatchObject({ stepId: "data-outcome", fieldId: "data-item-result" });
    expect(targetForDiagnostic(diagnostic("/resources/0/config/values"), definition)).toMatchObject({ stepId: "data-outcome", fieldId: "data-item-result" });
    expect(targetForDiagnostic(diagnostic("/transitions/0/actions/1/config/outputSchema"), definition)).toMatchObject({ stepId: "data-outcome", fieldId: "data-item-result" });
    expect(targetForDiagnostic(diagnostic("/verification/scenarios/0"), definition)).toMatchObject({ stepId: "try" });
    expect(targetForDiagnostic(diagnostic(""), definition)).toMatchObject({ stepId: "review" });
  });

  it("opens the canonical document in all six V2 panels and navigation alone stays clean", async () => {
    const user = userEvent.setup();
    const source = validDefinition("designer.safe_navigation", "Safe navigation");
    const request = vi.fn(async (input: RequestInfo | URL) => {
      if (String(input).endsWith("/prompt-previews")) return response([]);
      return response(revision(source, 2));
    }) as RequestFunction;
    const onDirtyChange = vi.fn();
    render(<DesignerDefinitionEditor route={{ kind: "editor", key: source.key, revision: 1 }} components={[]}
      adminToken="token" request={request} onDirtyChange={onDirtyChange} onSaved={() => undefined} />);

    await screen.findByTestId("brief-authoring");
    for (const step of ["brief", "capabilities", "interaction", "data-outcome", "try", "review"]) {
      await user.click(screen.getByTestId(`step-target-${step}`));
      expect((screen.getByTestId(`step-panel-${step}`) as HTMLElement).hidden).toBe(false);
    }
    expect(JSON.parse((screen.getByTestId("canonical-json-editor") as HTMLTextAreaElement).value)).toEqual(source);
    expect(screen.getByTestId("dirty-state").textContent).toBe("Saved draft");
    expect(onDirtyChange).toHaveBeenLastCalledWith(false);
  });

  it("applies canonical JSON, saves it, and retains backend validation diagnostics", async () => {
    const user = userEvent.setup();
    const source = validDefinition("designer.json_edit", "Before");
    const request = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (!init?.method) return response(revision(source, 1));
      if (path.endsWith("/prompt-previews")) return response([]);
      if (path.endsWith("/validation")) return response({ valid: true, diagnostics: [diagnostic("/metadata/displayName")] });
      if (init.method === "PUT") {
        const body = JSON.parse(String(init.body)) as { definition: AgentDefinitionV1 };
        return response(revision(body.definition, 2));
      }
      throw new Error(`Unexpected ${path}`);
    }) as RequestFunction;
    render(<DesignerDefinitionEditor route={{ kind: "editor", key: source.key, revision: 1 }} components={[]}
      adminToken="token" request={request} onDirtyChange={() => undefined} onSaved={() => undefined} />);

    await user.click(await screen.findByTestId("step-target-review"));
    const changed = structuredClone(source);
    changed.metadata.displayName = "After";
    fireEvent.change(screen.getByTestId("canonical-json-editor"), { target: { value: JSON.stringify(changed) } });
    await user.click(screen.getByTestId("apply-canonical-json"));
    expect(await screen.findByText(/JSON applied to the V2 projection/)).not.toBeNull();
    expect(screen.getByTestId("dirty-state").textContent).toBe("Unsaved changes");
    await user.click(screen.getByTestId("save-draft"));
    await waitFor(() => expect(screen.getByTestId("dirty-state").textContent).toBe("Saved draft"));
    expect(screen.getByTestId("backend-diagnostics")).not.toBeNull();
  });

  it("reloads the optimistic version on conflict and keeps local canonical changes for an explicit retry", async () => {
    const user = userEvent.setup();
    const source = validDefinition("designer.concurrent", "Original");
    const server = validDefinition("designer.concurrent", "Server change");
    let getCount = 0;
    let putCount = 0;
    const request = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (!init?.method) {
        getCount += 1;
        return response(revision(getCount === 1 ? source : server, getCount === 1 ? 1 : 2));
      }
      if (path.endsWith("/prompt-previews")) return response([]);
      if (path.endsWith("/validation")) return response({ valid: true, diagnostics: [] });
      if (init.method === "PUT") {
        putCount += 1;
        if (putCount === 1) return response({ code: "OPTIMISTIC_CONFLICT", diagnostics: [] }, 409);
        const body = JSON.parse(String(init.body)) as { definition: AgentDefinitionV1; optimisticVersion: number };
        expect(body.optimisticVersion).toBe(2);
        expect(body.definition.metadata.displayName).toBe("Local change");
        return response(revision(body.definition, 3));
      }
      throw new Error(`Unexpected ${path}`);
    }) as RequestFunction;
    render(<DesignerDefinitionEditor route={{ kind: "editor", key: source.key, revision: 1 }} components={[]}
      adminToken="token" request={request} onDirtyChange={() => undefined} onSaved={() => undefined} />);

    await user.click(await screen.findByTestId("step-target-review"));
    const changed = structuredClone(source);
    changed.metadata.displayName = "Local change";
    fireEvent.change(screen.getByTestId("canonical-json-editor"), { target: { value: JSON.stringify(changed) } });
    await user.click(screen.getByTestId("apply-canonical-json"));
    await screen.findByText(/JSON applied to the V2 projection/);
    await user.click(screen.getByTestId("save-draft"));
    await screen.findByTestId("optimistic-conflict");
    await user.click(screen.getByText("Keep local changes"));
    await user.click(screen.getByTestId("save-draft"));

    await waitFor(() => expect(screen.getByTestId("dirty-state").textContent).toBe("Saved draft"));
    expect(putCount).toBe(2);
  });
});

function validDefinition(key: string, name: string): AgentDefinitionV1 {
  const definition = createDefaultDefinition();
  definition.key = key;
  definition.metadata.displayName = name;
  definition.metadata.description = "A valid V2 projection fixture.";
  return definition;
}

function diagnostic(pointer: string) {
  return { code: "TEST", severity: "WARNING" as const, pointer, message: "Check this field.", hint: null };
}

function revision(definition: AgentDefinitionV1, optimisticVersion: number): DefinitionRevisionView {
  return {
    id: 1, key: definition.key, revision: definition.revision, schemaVersion: 1, status: "DRAFT",
    contentHash: "a".repeat(64), provenance: "DESIGNER", sourceDetail: "test", optimisticVersion,
    createdAt: "2026-08-30T00:00:00Z", updatedAt: "2026-08-30T00:00:00Z",
    publishedAt: null, archivedAt: null, definition,
  };
}

function response(payload: unknown, status = 200): Response {
  return { ok: status >= 200 && status < 300, status, json: async () => payload } as Response;
}
