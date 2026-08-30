import { describe, expect, it } from "vitest";
import type { DefinitionDiagnostic } from "../api/designerApi";
import { createDefaultDefinition } from "../authoring/editorModel";
import { diagnosticStep, groupDiagnostics, parseDefinitionJson, plainSummary, prettyDefinition } from "./reviewModel";

describe("review model", () => {
  it("reports parse locations and round trips a valid canonical document", () => {
    const definition = createDefaultDefinition();
    definition.key = "designer.review";
    const invalid = parseDefinitionJson('{\n  "key": "broken",\n}');
    expect(invalid).toMatchObject({ ok: false });
    if (!invalid.ok) expect(invalid.failure.line).toBeGreaterThanOrEqual(2);

    const valid = parseDefinitionJson(prettyDefinition(definition));
    expect(valid).toEqual({ ok: true, definition });
  });

  it("groups ordered actionable diagnostics across every guided step", () => {
    const diagnostics: DefinitionDiagnostic[] = [
      diagnostic("/verification/scenarios/0", "WARNING"),
      diagnostic("/transitions/0/actions/0/config", "ERROR"),
      diagnostic("/states/0/policy/config/responsePrompt/sections/0", "ERROR"),
      diagnostic("/states/0/id", "WARNING"),
      diagnostic("/interaction/supportedBehaviourModalities/0", "ERROR"),
      diagnostic("/interaction/supportedObservations/0", "WARNING"),
      diagnostic("/metadata/displayName", "ERROR"),
    ];

    expect(groupDiagnostics(diagnostics).map((group) => group.stepId)).toEqual([
      "purpose", "sensing", "behaviour", "reactions", "state-flow", "review",
    ]);
    expect(diagnosticStep(diagnostics[1])).toBe("reactions");
    expect(groupDiagnostics(diagnostics)[2].diagnostics[0].severity).toBe("ERROR");
  });

  it("summarizes authored behavior without introducing runtime semantics", () => {
    const definition = createDefaultDefinition();
    definition.interaction.supportedObservations = ["obs.text"];
    definition.transitions.push({ id: "stay", sourceStateId: "main", targetStateId: "main", order: 10, decisions: [], actions: [] });

    expect(plainSummary(definition).map((item) => item.value)).toEqual([
      "No purpose description yet.", "1 observation", "0 modalities", "1 move", "1 situation · 1 top-level situation",
    ]);
  });
});

function diagnostic(pointer: string, severity: "ERROR" | "WARNING"): DefinitionDiagnostic {
  return { code: `CODE_${pointer}`, severity, pointer, message: pointer || "whole definition", hint: "Fix it" };
}
