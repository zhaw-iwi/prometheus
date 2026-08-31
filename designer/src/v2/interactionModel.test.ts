import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import type { ComponentDefinition } from "../api/designerApi";
import type { AgentDefinitionV1 } from "../model/agentDefinition";
import {
  addRule,
  addRuleComponent,
  addRulePromptSection,
  addSituation,
  addSituationGuidance,
  createSituationForRule,
  deleteRule,
  deleteSituation,
  globalRuleSource,
  moveRule,
  moveRuleComponent,
  setRuleContinuation,
  situationDeletion,
  updateRulePromptSection,
} from "./interactionModel";
import { createDefaultDefinition, projectDefinition, serializedDefinition } from "./projection";

const CATALOG_ROOT = resolve(process.cwd(), "src/main/resources/agent-definitions/catalog/main");

describe("Designer V2 interaction model", () => {
  it("projects global, branch, cycle, finish, and inherited topology without changing representative definitions", () => {
    const expectations = [
      ["core.facial_expression_sensitivity", "global"],
      ["core.role_clarification_guessing_game", "branch"],
      ["core.rock_scissor_paper", "cycle"],
      ["usecases.healthcare.therapy_appointment_reminder_intro", "healthcare"],
      ["core.social_context_sensitivity", "inherited"],
    ] as const;
    for (const [key, topology] of expectations) {
      const definition = definitionByKey(key);
      const before = serializedDefinition(definition);
      const projection = projectDefinition(definition);
      expect(serializedDefinition(projection.source), topology).toBe(before);
      expect(projection.rules.some((rule) => rule.scope === "global"), topology).toBe(true);
      expect(projection.rules.some((rule) => rule.continuation === "finish"), topology).toBe(true);
      expect(projection.situations.length, topology).toBeGreaterThan(0);
    }
    const branch = projectDefinition(definitionByKey("core.role_clarification_guessing_game"));
    expect(new Set(branch.rules.filter((rule) => rule.continuation === "move").map((rule) => rule.targetStateId)).size).toBeGreaterThan(1);
    const rps = projectDefinition(definitionByKey("core.rock_scissor_paper"));
    expect(rps.rules.some((rule) => rule.targetStateId === "reveal" && rule.sourceStateId === "result")).toBe(true);
  });

  it("authors global, self, cross-situation, cycle, and lazily reused finish rules with stable IDs", () => {
    let projection = editableDefault();
    projection = addRule(projection, "main", "obs.user_utterance");
    expect(projection.rules[0]).toMatchObject({ continuation: "stay", sourceStateId: "main", targetStateId: "main", order: 10 });
    expect(projection.rules[0].id).toMatch(/^[a-z][a-z0-9-]*$/);

    projection = createSituationForRule(projection, projection.rules[0].id, "Follow up");
    expect(projection.situations.map((situation) => situation.name)).toContain("Follow up");
    const followUp = projection.situations.find((situation) => situation.name === "Follow up")!;
    expect(projection.rules[0]).toMatchObject({ continuation: "move", targetStateId: followUp.id });

    projection = addRule(projection, followUp.id, "obs.user_utterance");
    projection = setRuleContinuation(projection, projection.rules.at(-1)!.id, "move", "main");
    expect(projection.rules.at(-1)).toMatchObject({ sourceStateId: followUp.id, targetStateId: "main" });

    const source = globalRuleSource(projection);
    expect(source).toBe("context");
    projection = addRule(projection, source!, "obs.user_utterance");
    expect(projection.rules.at(-1)?.scope).toBe("global");
    projection = setRuleContinuation(projection, projection.rules[0].id, "finish");
    projection = setRuleContinuation(projection, projection.rules[1].id, "finish");
    expect(projection.source.states.filter((state) => state.kind === "final")).toHaveLength(1);
  });

  it("keeps AND conditions, prompt examples, effect order, and source-local rule priority explicit", () => {
    let projection = editableDefault();
    projection = addRule(projection, "main", "obs.user_utterance");
    const ruleId = projection.rules[0].id;
    projection = addRuleComponent(projection, ruleId, "condition", promptCondition());
    projection = addRuleComponent(projection, ruleId, "condition", promptCondition());
    expect(projection.source.transitions[0].decisions.map((item) => item.kind)).toEqual([
      "prometheus.decision.latest-event-type", "prometheus.decision.prompt", "prometheus.decision.prompt",
    ]);
    projection = addRulePromptSection(projection, ruleId, "condition", 1, "decisionPrompt", "positive-example");
    projection = updateRulePromptSection(projection, ruleId, "condition", 1, "decisionPrompt", 1, { content: "The person explicitly asks to continue." });
    expect(projection.source.transitions[0].decisions[1].config).toMatchObject({
      decisionPrompt: { sections: [{ kind: "transition-criterion" }, { kind: "positive-example", content: "The person explicitly asks to continue." }] },
    });

    projection = addRuleComponent(projection, ruleId, "effect", promptEffect());
    projection = addRuleComponent(projection, ruleId, "effect", incrementEffect());
    projection = moveRuleComponent(projection, ruleId, "effect", 1, -1);
    expect(projection.source.transitions[0].actions.map((item) => item.kind)).toEqual([
      "prometheus.action.increment", "prometheus.action.prompt-behaviour",
    ]);

    projection = addRule(projection, "main", "obs.user_utterance");
    const laterId = projection.rules.at(-1)!.id;
    projection = moveRule(projection, laterId, -1);
    expect(projection.source.transitions.filter((transition) => transition.sourceStateId === "main")
      .sort((left, right) => left.order - right.order).map((transition) => transition.id)).toEqual([laterId, ruleId]);
  });

  it("protects Main and referenced situations, then deletes an unreferenced situation without collateral changes", () => {
    let projection = editableDefault();
    projection = addSituation(projection, "Intake");
    const intake = projection.situations.find((situation) => situation.name === "Intake")!;
    expect(situationDeletion(projection, "main")).toMatchObject({ allowed: false });
    expect(situationDeletion(projection, intake.id)).toMatchObject({ allowed: true });
    projection = addRule(projection, "main", "obs.user_utterance");
    projection = setRuleContinuation(projection, projection.rules[0].id, "move", intake.id);
    expect(situationDeletion(projection, intake.id)).toMatchObject({ allowed: false });
    expect(deleteSituation(projection, intake.id).situations.some((item) => item.id === intake.id)).toBe(true);
    projection = deleteRule(projection, projection.rules[0].id);
    projection = deleteSituation(projection, intake.id);
    expect(projection.situations.some((item) => item.id === intake.id)).toBe(false);
    const context = projection.source.states.find((state) => state.id === "context");
    expect(context).toMatchObject({ kind: "composite", childStateIds: ["main"], initialChildStateId: "main" });
  });

  it("does not renumber rules in an unrelated scope when a rule is removed", () => {
    const definition = createDefaultDefinition();
    definition.transitions = [
      { id: "main-rule", sourceStateId: "main", targetStateId: "main", order: 40, decisions: [], actions: [] },
      { id: "global-rule", sourceStateId: "context", targetStateId: "context", order: 70, decisions: [], actions: [] },
    ];
    const projection = deleteRule(projectDefinition(definition), "main-rule");
    expect(projection.source.transitions).toEqual([
      { id: "global-rule", sourceStateId: "context", targetStateId: "context", order: 70, decisions: [], actions: [] },
    ]);
  });

  it("uses the same ordered guidance document shape for agent and situation scopes", () => {
    let projection = editableDefault();
    projection = addSituationGuidance(projection, "main", "objective");
    projection = addSituationGuidance(projection, "main", "starter");
    const local = projection.guidance.filter((section) => section.scope === "situation" && section.stateId === "main");
    expect(local.map((section) => [section.kind, section.promptField])).toEqual([
      ["objective", "responsePrompt"], ["starter", "starterPrompt"],
    ]);
    expect(projection.guidance.filter((section) => section.scope === "agent")).toEqual([]);
  });
});

function editableDefault() {
  const definition = createDefaultDefinition();
  definition.interaction.supportedObservations = ["obs.user_utterance"];
  return projectDefinition(definition);
}

function definitionByKey(key: string): AgentDefinitionV1 {
  const manifest = JSON.parse(readFileSync(resolve(CATALOG_ROOT, "manifest.json"), "utf8")) as {
    entries: Array<{ key: string; resource: string }>;
  };
  const entry = manifest.entries.find((candidate) => candidate.key === key);
  if (!entry) throw new Error(`Missing ${key}`);
  return JSON.parse(readFileSync(resolve(CATALOG_ROOT, entry.resource), "utf8")) as AgentDefinitionV1;
}

function promptCondition(): ComponentDefinition {
  return component("prometheus.decision.prompt", "DECISION", "RULE_CONDITION", {
    decisionPrompt: { sections: [{ id: "criterion", kind: "transition-criterion", content: "Only when appropriate." }] },
  });
}

function promptEffect(): ComponentDefinition {
  return component("prometheus.action.prompt-behaviour", "ACTION", "RULE_RESPONSE", {
    responsePrompt: { sections: [{ id: "response", kind: "objective", content: "Respond briefly." }] },
  });
}

function incrementEffect(): ComponentDefinition {
  return component("prometheus.action.increment", "ACTION", "DATA_UPDATE", { targetStorageKey: "count" });
}

function component(kind: string, category: ComponentDefinition["category"], authoringRole: ComponentDefinition["authoringRole"], defaultConfig: Record<string, unknown>): ComponentDefinition {
  return {
    kind, version: 1, category, configSchema: { type: "object" }, label: kind, description: kind,
    authoringRole, exposure: "GUIDED", capabilityGroup: "test", advancedReason: null,
    defaultConfig, examples: [], capabilities: {
      consumedObservations: [], emittedBehaviourModalities: [], storage: [], resources: [], states: [],
    },
  };
}
