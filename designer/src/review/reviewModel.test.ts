import { describe, expect, it } from "vitest";
import type { DefinitionDiagnostic } from "../api/designerApi";
import { createDefaultDefinition } from "../v2/projection";
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
      diagnostic("", "WARNING"),
      diagnostic("/verification/scenarios/0", "WARNING"),
      diagnostic("/storage/0/valueSchema", "ERROR"),
      diagnostic("/transitions/0/actions/0/config", "ERROR"),
      diagnostic("/states/0/policy/config/responsePrompt/sections/0", "ERROR"),
      diagnostic("/states/0/id", "WARNING"),
      diagnostic("/interaction/supportedBehaviourModalities/0", "ERROR"),
      diagnostic("/interaction/supportedObservations/0", "WARNING"),
      diagnostic("/metadata/displayName", "ERROR"),
    ];

    expect(groupDiagnostics(diagnostics).map((group) => group.stepId)).toEqual([
      "brief", "capabilities", "interaction", "data-outcome", "try", "review",
    ]);
    expect(diagnosticStep(diagnostics[3])).toBe("interaction");
    expect(groupDiagnostics(diagnostics)[2].diagnostics[0].severity).toBe("ERROR");
  });

  it("summarizes authored behavior without introducing runtime semantics", () => {
    const definition = createDefaultDefinition();
    definition.interaction.supportedObservations = ["obs.text"];
    definition.transitions.push({ id: "stay", sourceStateId: "main", targetStateId: "main", order: 10, decisions: [], actions: [] });

    expect(plainSummary(definition).map((item) => item.value)).toEqual([
      "No purpose description yet.", "1 input · 0 outputs", "1 situation · 1 rule", "0 data items · 0 outcomes", "0 scenarios",
    ]);
  });
});

function diagnostic(pointer: string, severity: "ERROR" | "WARNING"): DefinitionDiagnostic {
  return { code: `CODE_${pointer}`, severity, pointer, message: pointer || "whole definition", hint: "Fix it" };
}
