import { describe, expect, it } from "vitest";
import type { AtomicStateDefinition, CompositeStateDefinition } from "../model/agentDefinition";
import { createDefaultDefinition, projectDefinition, serializedDefinition } from "./projection";
import {
  addGuidanceIntent,
  adoptGuidanceExample,
  briefIssues,
  moveGuidanceSection,
  suggestDefinitionKey,
  updateGuidanceSection,
} from "./briefModel";

describe("V2 Brief model", () => {
  it("suggests schema-valid stable keys and requires explicit confirmation", () => {
    const projection = projectDefinition(createDefaultDefinition());
    expect(suggestDefinitionKey("Friendly Reception Guide")).toBe("designer.friendly_reception_guide");
    expect(briefIssues(projection, false)).toMatchObject({ key: expect.any(String), displayName: expect.any(String), description: expect.any(String) });
    projection.source.key = "designer.reception_guide";
    projection.source.metadata.displayName = "Reception guide";
    projection.source.metadata.description = "Helps visitors in reception.";
    expect(briefIssues(projectDefinition(projection.source), false).key).toContain("Confirm");
    expect(briefIssues(projectDefinition(projection.source), true)).toEqual({});
  });

  it("edits agent-wide guidance without touching situation guidance or topology", () => {
    const definition = createDefaultDefinition();
    const context = definition.states[0] as CompositeStateDefinition;
    const main = definition.states[1] as AtomicStateDefinition;
    context.policy!.config.responsePrompt = { sections: [
      { id: "unknown.first", kind: "partner-specific", content: "Keep me exactly." },
      { id: "known.second", kind: "objective", content: "Original goal." },
    ] };
    main.policy!.config.responsePrompt = { sections: [
      { id: "main.local", kind: "objective", content: "Situation only." },
    ] };
    const originalStates = definition.states.map((state) => ({ id: state.id, kind: state.kind }));
    const projection = projectDefinition(definition);
    const changed = updateGuidanceSection(projection, { stateId: "context", promptField: "responsePrompt" }, 0,
      { content: "Edited without conversion." });

    expect(changed.source.states.map((state) => ({ id: state.id, kind: state.kind }))).toEqual(originalStates);
    expect(changed.guidance.filter((item) => item.scope === "situation")).toEqual(
      projection.guidance.filter((item) => item.scope === "situation"));
    expect(changed.guidance.filter((item) => item.scope === "agent").map(({ id, kind, content }) => ({ id, kind, content }))).toEqual([
      { id: "unknown.first", kind: "partner-specific", content: "Edited without conversion." },
      { id: "known.second", kind: "objective", content: "Original goal." },
    ]);
  });

  it("preserves unknown IDs and kinds while reordering the exact prompt section array", () => {
    const definition = createDefaultDefinition();
    const context = definition.states[0] as CompositeStateDefinition;
    context.policy!.config.responsePrompt = { separator: "\n--\n", sections: [
      { id: "unknown.one", kind: "x-hospital-rule", content: "One" },
      { id: "unknown.two", kind: "x-hospital-rule", content: "Two" },
    ] };
    const moved = moveGuidanceSection(projectDefinition(definition),
      { stateId: "context", promptField: "responsePrompt" }, 1, -1);
    expect((moved.source.states[0] as CompositeStateDefinition).policy!.config.responsePrompt).toEqual({ separator: "\n--\n", sections: [
      { id: "unknown.two", kind: "x-hospital-rule", content: "Two" },
      { id: "unknown.one", kind: "x-hospital-rule", content: "One" },
    ] });
  });

  it("keeps examples inert until explicitly adopted", () => {
    const projection = projectDefinition(createDefaultDefinition());
    const before = serializedDefinition(projection.source);
    expect(serializedDefinition(projection.source)).toBe(before);
    const adopted = adoptGuidanceExample(projection, "helpful-guide");
    expect(serializedDefinition(adopted.source)).not.toBe(before);
    expect(adopted.guidance.filter((item) => item.scope === "agent")).toHaveLength(6);
    expect(projection.guidance).toHaveLength(0);
  });

  it("adds known intentions with collision-free IDs in their registered prompt role", () => {
    const first = addGuidanceIntent(projectDefinition(createDefaultDefinition()), "modality-guidance");
    const second = addGuidanceIntent(first, "modality-guidance");
    expect(second.guidance.map(({ id, kind, promptField }) => ({ id, kind, promptField }))).toEqual([
      { id: "modality-guidance", kind: "modality-guidance", promptField: "nonverbalPlanPrompt" },
      { id: "modality-guidance-2", kind: "modality-guidance", promptField: "nonverbalPlanPrompt" },
    ]);
  });
});
