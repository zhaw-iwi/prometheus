import type { ComponentDefinition } from "../api/designerApi";
import type { CompositeStateDefinition } from "../model/agentDefinition";
import { createDefaultDefinition } from "./editorModel";
import {
  addReaction,
  addState,
  addTransition,
  assignParent,
  deleteState,
  deleteTransition,
  expandDefaultState,
  missingCapabilities,
  moveCompositeChild,
  moveState,
  moveTransition,
  reactionObservation,
  replaceTransition,
  setInitialChild,
  synchronizeCapabilities,
} from "./graphModel";

describe("state graph transformations", () => {
  it("preserves ordered reaction components and unrelated document content", () => {
    const source = createDefaultDefinition();
    source.interaction.supportedObservations = ["obs.user_utterance", "obs.hand.sign"];
    source.resources.push({ id: "choices", kind: "prometheus.resource.typed-choices", version: 1, config: { values: ["rock"] } });
    let next = addReaction(source, "obs.user_utterance");
    next = addReaction(next, "obs.hand.sign");
    const second = next.transitions[1];
    second.decisions.push({ kind: "prometheus.decision.prompt", version: 1, config: { marker: "keep" } });
    second.actions.push({ kind: "prometheus.action.increment", version: 1, config: { targetStorageKey: "round" } });
    next = replaceTransition(next, second);
    next = moveTransition(next, second.id, -1);

    expect(next.transitions.map((transition) => [transition.id, transition.order])).toEqual([
      [second.id, 10], ["reaction_main", 20],
    ]);
    expect(reactionObservation(next.transitions[0])).toBe("obs.hand.sign");
    expect(next.transitions[0].decisions.map((decision) => decision.kind)).toEqual([
      "prometheus.decision.latest-event-type", "prometheus.decision.prompt",
    ]);
    expect(next.transitions[0].actions[0].config.targetStorageKey).toBe("round");
    expect(next.resources).toEqual(source.resources);
  });

  it("converts the default view to multiple situations without moving its reactions", () => {
    const source = createDefaultDefinition();
    source.interaction.supportedObservations = ["obs.user_utterance"];
    const reaction = addReaction(source, "obs.user_utterance");
    const expanded = expandDefaultState(reaction);

    expect(expanded.states.map((state) => state.id)).toEqual(["main", "next_situation"]);
    expect(expanded.transitions[0]).toMatchObject({ sourceStateId: "main", targetStateId: "main" });
  });

  it("adds, edits, reorders, and deletes situations and moves while retaining cycles", () => {
    let definition = expandDefaultState(createDefaultDefinition());
    definition = addState(definition, "final", "Done");
    definition = moveState(definition, "done", -1);
    definition = addTransition(definition, "main", "next_situation", "advance");
    definition = addTransition(definition, "next_situation", "main", "return");
    definition = addTransition(definition, "next_situation", "next_situation", "repeat");
    definition = moveTransition(definition, "repeat_next_situation", -1);

    expect(definition.states.map((state) => state.id)).toEqual(["main", "done", "next_situation"]);
    expect(definition.transitions.map((transition) => [transition.sourceStateId, transition.targetStateId])).toEqual([
      ["main", "next_situation"], ["next_situation", "next_situation"], ["next_situation", "main"],
    ]);
    expect(definition.transitions.filter((transition) => transition.sourceStateId === "next_situation")
      .map((transition) => transition.order)).toEqual([10, 20]);

    definition = deleteTransition(definition, "return_next_situation");
    definition = deleteState(definition, "done");
    expect(definition.transitions.map((transition) => transition.id)).toEqual(["advance_main", "repeat_next_situation"]);
    expect(definition.states.map((state) => state.id)).toEqual(["main", "next_situation"]);
  });

  it("edits composite containment and initial-child order without duplicating children", () => {
    let definition = expandDefaultState(createDefaultDefinition());
    definition = addState(definition, "final", "Done");
    definition = addState(definition, "composite", "Session");
    const composite = definition.states.find((state) => state.kind === "composite") as CompositeStateDefinition;
    expect(composite.childStateIds).toEqual(["main"]);

    definition = assignParent(definition, "next_situation", composite.id);
    definition = assignParent(definition, "done", composite.id);
    definition = setInitialChild(definition, composite.id, "next_situation");
    definition = moveCompositeChild(definition, composite.id, "done", -1);
    const changed = definition.states.find((state) => state.id === composite.id) as CompositeStateDefinition;

    expect(changed.childStateIds).toEqual(["main", "done", "next_situation"]);
    expect(changed.initialChildStateId).toBe("next_situation");
    expect(definition.lifecycle.initialStateId).toBe(composite.id);
  });

  it("offers and applies synchronized declarations for advanced component capabilities", () => {
    let definition = createDefaultDefinition();
    definition = addTransition(definition, "main", "main", "advanced");
    definition.transitions[0].decisions.push({
      kind: "test.decision.sensor", version: 1, config: { eventType: "obs.hand.sign" },
    });
    definition.transitions[0].actions.push({
      kind: "test.action.display", version: 1, config: { emittedModalities: ["display"] },
    });
    const missing = missingCapabilities(definition, [
      component("test.decision.sensor", "DECISION", ["obs.social.context"], []),
      component("test.action.display", "ACTION", [], ["speech"]),
    ]);
    expect(missing).toEqual({
      observations: ["obs.social.context", "obs.hand.sign"],
      modalities: ["speech", "display"],
    });

    definition = synchronizeCapabilities(definition, missing);
    expect(definition.interaction.supportedObservations).toEqual(["obs.social.context", "obs.hand.sign"]);
    expect(definition.interaction.supportedBehaviourModalities).toEqual(["speech", "display"]);
  });
});

function component(
  kind: string,
  category: ComponentDefinition["category"],
  observations: string[],
  modalities: string[],
): ComponentDefinition {
  return {
    kind, version: 1, category, configSchema: {}, label: kind, description: kind, defaultConfig: {}, examples: [],
    authoringRole: category === "DECISION" ? "RULE_CONDITION" : "DETERMINISTIC_OPERATION",
    exposure: "GUIDED", capabilityGroup: "test-component", advancedReason: null,
    capabilities: { consumedObservations: observations, emittedBehaviourModalities: modalities, storage: [], resources: [], states: [] },
  };
}
