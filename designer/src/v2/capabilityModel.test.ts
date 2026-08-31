import { describe, expect, it } from "vitest";
import type { ComponentDefinition } from "../api/designerApi";
import type { AtomicStateDefinition } from "../model/agentDefinition";
import { createDefaultDefinition, projectDefinition } from "./projection";
import {
  capabilityUses,
  deterministicOperationGroups,
  responseStrategies,
  selectMainStrategy,
  strategyCompatibility,
} from "./capabilityModel";
import { updateCapabilities } from "./transforms";

describe("V2 Capabilities model", () => {
  it("declares capabilities without creating rules, situations, or mappings", () => {
    const projection = projectDefinition(createDefaultDefinition());
    const changed = updateCapabilities(projection, {
      observations: ["obs.user_utterance", "obs.emotion.face"],
      behaviourModalities: ["speech"],
    });
    expect(changed.capabilities.observations).toEqual(["obs.user_utterance", "obs.emotion.face"]);
    expect(changed.capabilities.behaviourModalities).toEqual(["speech"]);
    expect(changed.source.states).toEqual(projection.source.states);
    expect(changed.source.transitions).toEqual(projection.source.transitions);
    expect(changed.situations).toHaveLength(projection.situations.length);
  });

  it("reports configured usage and leaves unused declarations visible", () => {
    const definition = createDefaultDefinition();
    definition.interaction.supportedObservations = ["obs.user_utterance", "obs.weather.current"];
    (definition.states[1] as AtomicStateDefinition).policy!.config.consumedObservations = ["obs.user_utterance"];
    const projection = projectDefinition(definition);
    expect(capabilityUses(projection, "obs.user_utterance")).toHaveLength(1);
    expect(capabilityUses(projection, "obs.weather.current")).toEqual([]);
  });

  it("derives strategy compatibility and exact-text adoption from backend descriptors", () => {
    const prompt = component("prometheus.policy.prompt", "Prompt policy", "prompt-response", "RESPONSE_STRATEGY", {}, [], []);
    const exact = component("prometheus.policy.exact-text", "Exact text", "exact-text-response", "RESPONSE_STRATEGY",
      { eventType: "obs.user_utterance", actor: "user", eventKind: "observation", maxTextCodePoints: 2000 },
      ["obs.user_utterance"], ["speech"]);
    const projection = projectDefinition(createDefaultDefinition());
    expect(responseStrategies([exact, prompt]).map((item) => item.capabilityGroup)).toEqual(["exact-text-response", "prompt-response"]);
    expect(strategyCompatibility(projection, exact)).toEqual({
      compatible: false, missingObservations: ["obs.user_utterance"], missingModalities: ["speech"],
    });
    const declared = updateCapabilities(projection, { observations: ["obs.user_utterance"], behaviourModalities: ["speech"] });
    expect(strategyCompatibility(declared, exact).compatible).toBe(true);
    const selected = selectMainStrategy(declared, exact);
    expect(selected.situations.find((situation) => situation.main)?.ordinaryPolicy?.envelope).toEqual({
      kind: "prometheus.policy.exact-text", version: 1,
      config: { eventType: "obs.user_utterance", actor: "user", eventKind: "observation", maxTextCodePoints: 2000 },
    });
    expect(selected.source.transitions).toEqual([]);
    expect(selected.source.states).toHaveLength(2);
  });

  it("groups the registered RPS pack and exposes installed kinds plus owned data", () => {
    const rps = [
      component("prometheus.policy.rps-reveal", "RPS reveal", "rock-scissor-paper", "DETERMINISTIC_OPERATION",
        { currentAgentSignStorageKey: "rps_current_agent_sign" }, [], []),
      component("prometheus.action.rps-evaluate-round", "Evaluate round", "rock-scissor-paper", "DETERMINISTIC_OPERATION",
        { roundsStorageKey: "rps_rounds" }, ["obs.hand.sign"], []),
    ];
    const definition = createDefaultDefinition();
    definition.storage = [{ key: "rps_current_agent_sign" }, { key: "rps_rounds" }];
    definition.states[1].policy = { kind: rps[0].kind, version: 1, config: rps[0].defaultConfig };
    definition.transitions.push({ id: "round", sourceStateId: "main", targetStateId: "main", order: 10, decisions: [],
      actions: [{ kind: rps[1].kind, version: 1, config: rps[1].defaultConfig }] });
    const [group] = deterministicOperationGroups(projectDefinition(definition), rps);
    expect(group.id).toBe("rock-scissor-paper");
    expect(group.installedKinds).toEqual(expect.arrayContaining(rps.map((item) => item.kind)));
    expect(group.ownedDataKeys).toEqual(["rps_current_agent_sign", "rps_rounds"]);
  });
});

function component(
  kind: string,
  label: string,
  capabilityGroup: string,
  authoringRole: ComponentDefinition["authoringRole"],
  defaultConfig: Record<string, unknown>,
  consumedObservations: string[],
  emittedBehaviourModalities: string[],
): ComponentDefinition {
  return {
    kind, version: 1, category: kind.includes(".action.") ? "ACTION" : "POLICY", configSchema: { type: "object" },
    label, description: `${label} test descriptor.`, authoringRole, exposure: "GUIDED", capabilityGroup,
    advancedReason: null, defaultConfig, examples: [defaultConfig],
    capabilities: { consumedObservations, emittedBehaviourModalities, storage: [], resources: [], states: [] },
  };
}
