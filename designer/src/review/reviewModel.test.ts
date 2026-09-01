import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import type { DefinitionDiagnostic } from "../api/designerApi";
import type { AgentDefinitionV1 } from "../model/agentDefinition";
import { createDefaultDefinition } from "../v2/projection";
import {
  advancedDefinitionAudit,
  diagnosticStep,
  groupDiagnostics,
  parseDefinitionJson,
  prettyDefinition,
  reverseExplanation,
} from "./reviewModel";

const CATALOG_ROOT = resolve(process.cwd(), "src/main/resources/agent-definitions/catalog/main");

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

  it.each([
    {
      key: "core.talk_to_me",
      expected: ["exact text responses", "Main starting situation", "stays in"],
    },
    {
      key: "core.role_clarification_guessing_game",
      expected: ["3 situations", "moves from", "interaction finishes", "Outcome reports contain outcome"],
    },
    {
      key: "core.rock_scissor_paper",
      expected: ["rock, scissor, paper reveal responses", "rock, scissor, paper result responses", "4 data items"],
    },
    {
      key: "usecases.healthcare.smart_goal_coaching",
      expected: ["Main starting situation", "stays in", "Outcome reports contain outcome"],
    },
  ])("reverse-explains the $key topology without implementation vocabulary", ({ key, expected }) => {
    const explanation = reverseExplanation(bundledDefinition(key));
    expect(explanation.map((section) => section.id)).toEqual([
      "brief", "capabilities", "interaction", "data-outcome", "try",
    ]);
    const prose = explanation.flatMap((section) => [section.summary, ...section.statements]).join(" ");
    expected.forEach((phrase) => expect(prose).toContain(phrase));
    expect(prose).not.toContain("prometheus.");
    expect(prose).not.toContain("eventSelector");
  });

  it("produces a complete domain summary for every bundled definition", () => {
    const definitions = bundledDefinitions();
    expect(definitions).toHaveLength(12);

    for (const definition of definitions) {
      const explanation = reverseExplanation(definition);
      expect(explanation.map((section) => section.id), definition.key).toEqual([
        "brief", "capabilities", "interaction", "data-outcome", "try",
      ]);
      explanation.forEach((section) => expect(section.summary.trim().length, `${definition.key}:${section.id}`).toBeGreaterThan(0));
      const prose = explanation.flatMap((section) => [section.title, section.summary, ...section.statements]).join(" ");
      expect(prose, definition.key).not.toMatch(/prometheus\.|eventSelector|sourceStateId|targetStateId/);
    }
  });

  it("derives a complete Advanced audit from canonical IDs, envelopes, schemas, and lifecycle", () => {
    const definition = bundledDefinition("core.role_clarification_guessing_game");
    const audit = advancedDefinitionAudit(definition);

    expect(audit.states.map((state) => state.id)).toEqual(definition.states.map((state) => state.id));
    expect(audit.rules.map((rule) => rule.id)).toEqual(definition.transitions.map((rule) => rule.id));
    expect(audit.rules.map((rule) => rule.pointer)).toEqual(definition.transitions.map((_, index) => `/transitions/${index}`));
    expect(audit.states.find((state) => state.id === "role_clarification")).toMatchObject({
      parentStateIds: ["context"], initialFor: ["context"], entryMode: "start", oblivious: false,
    });
    expect(audit.components.flatMap((component) => component.uses).map((use) => use.pointer))
      .toContain("/states/0/eventSelector");
    expect(audit.storage.map((item) => item.declaration)).toEqual(definition.storage);
    expect(audit.lifecycle).toEqual(definition.lifecycle);
  });
});

function diagnostic(pointer: string, severity: "ERROR" | "WARNING"): DefinitionDiagnostic {
  return { code: `CODE_${pointer}`, severity, pointer, message: pointer || "whole definition", hint: "Fix it" };
}

function bundledDefinition(key: string): AgentDefinitionV1 {
  const definitions = bundledDefinitions();
  const definition = definitions.find((candidate) => candidate.key === key);
  if (!definition) throw new Error(`Missing ${key}`);
  return definition;
}

function bundledDefinitions(): AgentDefinitionV1[] {
  const manifest = JSON.parse(readFileSync(resolve(CATALOG_ROOT, "manifest.json"), "utf8")) as {
    entries: Array<{ key: string; resource: string }>;
  };
  return manifest.entries.map((entry) => JSON.parse(
    readFileSync(resolve(CATALOG_ROOT, entry.resource), "utf8"),
  ) as AgentDefinitionV1);
}
