import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import type { AgentDefinitionV1, JsonObject, PromptSection } from "../model/agentDefinition";
import {
  createDefaultDefinition,
  definitionFromProjection,
  nextStableId,
  nextTransitionOrder,
  projectDefinition,
  serializedDefinition,
} from "./projection";
import { replaceScopedGuidance, updateCapabilities, updateIdentity } from "./transforms";

const CATALOG_ROOT = resolve(process.cwd(), "src/main/resources/agent-definitions/catalog/main");

describe("Designer V2 canonical projection", () => {
  it("projects all twelve production definitions and returns each canonical document unchanged", () => {
    const definitions = bundledDefinitions();
    expect(definitions).toHaveLength(12);

    for (const definition of definitions) {
      const projection = projectDefinition(definition);
      expect(projection.identity.key).toBe(definition.key);
      expect(projection.situations.length).toBeGreaterThan(0);
      expect(projection.guidance).toHaveLength(expectedOrdinaryGuidanceCount(definition));
      expect(serializedDefinition(definitionFromProjection(projection))).toBe(serializedDefinition(definition));
      expect(projection.source).not.toBe(definition);
    }
  });

  it("represents prompt, exact-text, RPS, and healthcare data topologies without restructuring", () => {
    const byKey = new Map(bundledDefinitions().map((definition) => [definition.key, projectDefinition(definition)]));
    const exact = byKey.get("core.talk_to_me")!;
    expect(exact.situations).toHaveLength(1);
    expect(exact.ordinaryPolicies[0].envelope.kind).toBe("prometheus.policy.exact-text");

    const rps = byKey.get("core.rock_scissor_paper")!;
    expect(rps.situations.map((situation) => situation.name)).toEqual(expect.arrayContaining([
      "Valerian Core RPS Start", "Valerian Core RPS Reveal Sign", "Valerian Core RPS Round Result",
    ]));
    expect(rps.capabilities.installedComponents.map((component) => component.kind)).toEqual(expect.arrayContaining([
      "prometheus.action.rps-select-sign", "prometheus.action.rps-evaluate-round",
      "prometheus.policy.rps-reveal", "prometheus.policy.rps-result",
    ]));
    expect(rps.data.items.every((item) => item.role === "working-data")).toBe(true);

    const therapy = byKey.get("usecases.healthcare.therapy_appointment_reminder")!;
    expect(therapy.data.items.find((item) => item.key === "therapyAppointmentContext")?.role).toBe("starting-context");
    expect(therapy.data.items.find((item) => item.key === "outcome")?.role).toBe("outcome-report");
    expect(therapy.data.resources).toHaveLength(1);
    expect(therapy.data.initializers).toHaveLength(1);
  });

  it("preserves unknown guidance, envelopes, schemas, resources, initializers, transitions, and scenarios", () => {
    const definition = createDefaultDefinition() as AgentDefinitionV1 & { extension?: JsonObject };
    definition.extension = { retained: [1, { future: true }] };
    const outer = definition.states[0];
    if (outer.kind === "final" || !outer.policy) throw new Error("Expected prompt context");
    outer.policy.config.responsePrompt = {
      sections: [{ id: "future.guidance", kind: "future-kind", content: "Keep this section." }],
      futurePromptOption: { exact: true },
    };
    definition.storage.push({
      key: "custom", description: "Future data", valueSchema: { type: ["string", "null"], futureKeyword: 7 },
      required: false, visibility: "internal", reset: "remove", examples: [null, "value"], future: true,
    });
    definition.resources.push({ id: "future-resource", kind: "vendor.resource.future", version: 9, config: { x: 1 } });
    definition.lifecycle.initializers.push({ kind: "vendor.initializer.future", version: 4, config: { storageKey: "custom" } });
    definition.transitions.push({
      id: "future-rule", sourceStateId: "main", targetStateId: "main", order: 37,
      decisions: [{ kind: "vendor.decision.future", version: 2, config: { nested: { keep: true } } }],
      actions: [{ kind: "vendor.action.future", version: 3, config: { storageKey: "custom" } }],
      futureTransitionField: "kept",
    });
    definition.verification = { scenarios: [{ name: "Future", futureScenarioField: { value: 42 } }] };

    const projection = projectDefinition(definition);
    expect(projection.guidance[0]).toMatchObject({ id: "future.guidance", kind: "future-kind", scope: "agent" });
    expect(projection.rules[0].conditions[0].envelope.kind).toBe("vendor.decision.future");
    expect(projection.verification.scenarios[0]).toHaveProperty("futureScenarioField");
    expect(definitionFromProjection(projection)).toEqual(definition);
  });

  it("applies focused immutable edits without rebuilding unrelated canonical content", () => {
    const source = bundledDefinitions().find((definition) =>
      definition.key === "usecases.healthcare.therapy_appointment_reminder")!;
    const original = structuredClone(source);
    let projection = updateIdentity(projectDefinition(source), {
      metadata: { displayName: "Edited name" },
    });
    projection = updateCapabilities(projection, {
      observations: [...projection.capabilities.observations, "obs.future"],
    });
    const guidance = projection.guidance.find((item) => item.scope === "agent");
    if (guidance) {
      projection = replaceScopedGuidance(projection, guidance, [{
        id: guidance.id, kind: guidance.kind, content: `${guidance.content}\nFocused edit.`,
      } satisfies PromptSection]);
    }

    expect(projection.source.metadata.displayName).toBe("Edited name");
    expect(projection.source.interaction.supportedObservations.at(-1)).toBe("obs.future");
    expect(projection.source.resources).toEqual(original.resources);
    expect(projection.source.lifecycle.initializers).toEqual(original.lifecycle.initializers);
    expect(projection.source.storage).toEqual(original.storage);
    expect(source).toEqual(original);
  });

  it("creates the prompt-context baseline and generates collision-free IDs and source priorities", () => {
    const definition = createDefaultDefinition();
    expect(definition.lifecycle.initialStateId).toBe("context");
    expect(definition.states).toMatchObject([
      { id: "context", kind: "composite", childStateIds: ["main"], initialChildStateId: "main" },
      { id: "main", kind: "atomic" },
    ]);
    expect(projectDefinition(definition).situations[0]).toMatchObject({ id: "main", main: true });
    expect(nextStableId("Follow up", ["follow-up", "follow-up-2"])).toBe("follow-up-3");
    expect(nextStableId("Other", ["main"])).toBe("other");
    expect(nextTransitionOrder([
      { id: "a", sourceStateId: "main", targetStateId: "main", order: 20, decisions: [], actions: [] },
      { id: "b", sourceStateId: "other", targetStateId: "main", order: 80, decisions: [], actions: [] },
    ], "main")).toBe(30);
  });
});

function bundledDefinitions(): AgentDefinitionV1[] {
  const manifest = JSON.parse(readFileSync(resolve(CATALOG_ROOT, "manifest.json"), "utf8")) as {
    entries: Array<{ resource: string }>;
  };
  return manifest.entries.map((entry) => JSON.parse(
    readFileSync(resolve(CATALOG_ROOT, entry.resource), "utf8"),
  ) as AgentDefinitionV1);
}

function expectedOrdinaryGuidanceCount(definition: AgentDefinitionV1): number {
  return definition.states.reduce((total, state) => {
    if (state.kind === "final" || !state.policy) return total;
    return total + Object.entries(state.policy.config).reduce((promptTotal, [key, value]) => {
      if (!key.endsWith("Prompt") || typeof value !== "object" || value === null || Array.isArray(value)) {
        return promptTotal;
      }
      const sections = (value as { sections?: unknown }).sections;
      return promptTotal + (Array.isArray(sections) ? sections.length : 0);
    }, 0);
  }, 0);
}
