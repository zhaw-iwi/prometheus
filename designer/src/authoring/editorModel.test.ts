import { describe, expect, it } from "vitest";
import type { ComponentDefinition } from "../api/designerApi";
import {
  adoptStrategy,
  authoringFormToDefinition,
  createDefaultDefinition,
  definitionToAuthoringForm,
  isStrategyCompatible,
  serializedDefinition,
  updatePromptSection,
} from "./editorModel";

describe("guided authoring document mapping", () => {
  it("round-trips form fields without replacing untouched canonical graph content", () => {
    const source = createDefaultDefinition();
    source.key = "designer.round_trip";
    source.metadata.displayName = "Round trip";
    source.metadata.description = "Preserve the complete document.";
    source.transitions.push({
      id: "stay",
      sourceStateId: "main",
      targetStateId: "main",
      order: 10,
      decisions: [],
      actions: [],
    });
    source.resources.push({ id: "safe_resource", kind: "prometheus.resource.typed-choices", version: 1, config: { values: ["one"] } });

    const form = definitionToAuthoringForm(source);
    const result = authoringFormToDefinition(source, form);

    expect(result).toEqual(source);
    expect(result.transitions[0].id).toBe("stay");
    expect(result.resources[0].id).toBe("safe_resource");
    const reordered = JSON.parse(JSON.stringify(source)) as typeof source;
    (reordered.states[0].policy as { config: Record<string, unknown> }).config.responsePrompt = {
      sections: [{ content: "Same content", kind: "objective", id: "purpose.objective" }],
    };
    const mapped = authoringFormToDefinition(reordered, definitionToAuthoringForm(reordered));
    expect(serializedDefinition(mapped)).toBe(serializedDefinition(reordered));
  });

  it("creates the explicit default main state and maps ordered prompt/capability fields", () => {
    const source = createDefaultDefinition();
    let form = definitionToAuthoringForm(source);
    form = {
      ...form,
      key: "designer.coach",
      displayName: "Coach",
      description: "Helps choose a next step.",
      supportedObservations: ["obs.user_utterance"],
      supportedBehaviourModalities: ["speech", "display"],
      promptSections: updatePromptSection(
        updatePromptSection([], "behaviour.fallback", "fallback", "Ask for clarification."),
        "purpose.objective", "objective", "Help choose one next step.",
      ),
    };

    const result = authoringFormToDefinition(source, form);
    const main = result.states[0];
    const policy = main.policy as { kind: string; config: Record<string, unknown> };
    const responsePrompt = policy.config.responsePrompt as { sections: Array<{ id: string }> };

    expect(result.lifecycle.initialStateId).toBe("main");
    expect(main).toMatchObject({ id: "main", kind: "atomic", entryMode: "start" });
    expect(responsePrompt.sections.map((section) => section.id)).toEqual([
      "purpose.objective",
      "behaviour.fallback",
    ]);
    expect(policy.config.consumedObservations).toEqual(["obs.user_utterance"]);
    expect(policy.config.emittedModalities).toEqual(["speech", "display"]);
  });

  it("filters strategy compatibility and adopts backend defaults explicitly", () => {
    const prompt = component("prometheus.policy.prompt", [], true);
    const exact = component("prometheus.policy.exact-text", ["speech"], false);
    const form = {
      ...definitionToAuthoringForm(createDefaultDefinition()),
      supportedBehaviourModalities: ["speech", "display"],
    };

    expect(isStrategyCompatible(prompt, form.supportedBehaviourModalities)).toBe(true);
    expect(isStrategyCompatible(exact, form.supportedBehaviourModalities)).toBe(false);
    const adopted = adoptStrategy({ ...form, supportedBehaviourModalities: ["speech"] }, exact);
    expect(adopted.strategyKind).toBe("prometheus.policy.exact-text");
    expect(adopted.strategyConfig).toEqual({ eventType: "obs.user_utterance" });
    expect(adopted.supportedObservations).toContain("obs.user_utterance");
  });
});

function component(kind: string, emitted: string[], configurableModalities: boolean): ComponentDefinition {
  return {
    kind,
    version: 1,
    category: "POLICY",
    label: kind,
    description: "Test strategy",
    authoringRole: "RESPONSE_STRATEGY",
    exposure: "GUIDED",
    capabilityGroup: "test-response",
    advancedReason: null,
    configSchema: configurableModalities ? { properties: { emittedModalities: { type: "array" } } } : {},
    defaultConfig: { eventType: "obs.user_utterance" },
    examples: [],
    capabilities: {
      consumedObservations: kind.endsWith("exact-text") ? ["obs.user_utterance"] : [],
      emittedBehaviourModalities: emitted,
      storage: [], resources: [], states: [],
    },
  };
}
