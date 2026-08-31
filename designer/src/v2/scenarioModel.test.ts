import { describe, expect, it } from "vitest";
import type { AgentDefinitionV1 } from "../model/agentDefinition";
import {
  addBehaviourFragment,
  addScenario,
  addScenarioEvent,
  deleteScenarioEvent,
  eventTemplate,
  moveScenarioEvent,
  parseJsonValue,
  replaceScenarioEvent,
  scenarioViews,
  setExpectedSituation,
  setScenarioStorage,
  updateScenario,
  updateScenarioEvent,
} from "./scenarioModel";
import { createDefaultDefinition, projectDefinition, serializedDefinition } from "./projection";

describe("Designer V2 verification scenario model", () => {
  it("projects and returns an imported scenario with unknown fields unchanged", () => {
    const definition = fixture();
    const before = serializedDefinition(definition);
    const scenario = scenarioViews(projectDefinition(definition))[0];

    expect(scenario).toMatchObject({ name: "Imported", description: "Preserve me", initializerSeed: 42 });
    expect(scenario.events[0].source).toHaveProperty("futureEventOption", true);
    expect(scenario.source).toHaveProperty("futureScenarioOption");
    expect(serializedDefinition(projectDefinition(definition).source)).toBe(before);
  });

  it("authors Given / When / Expect while preserving unrelated imported content", () => {
    let projection = projectDefinition(fixture());
    projection = updateScenario(projection, 0, { name: "Edited", description: "New description", initializerSeed: 7 });
    projection = setScenarioStorage(projection, 0, "initialStorage", "count", 3);
    projection = addScenarioEvent(projection, 0, "obs.hand.sign");
    projection = updateScenarioEvent(projection, 0, 1, { payload: "{\"sign\":\"paper\"}" });
    projection = setExpectedSituation(projection, 0, "main");
    projection = setScenarioStorage(projection, 0, "expected", "count", 4);
    projection = addBehaviourFragment(projection, 0, { speech: "done" });

    const scenario = projection.source.verification!.scenarios[0];
    expect(scenario).toMatchObject({
      name: "Edited", description: "New description", initializerSeed: 7,
      initialStorage: { count: 3 },
      futureScenarioOption: { retained: true },
      expected: { activeStatePath: ["context", "main"], storage: { count: 4 } },
    });
    expect((scenario.events as Array<Record<string, unknown>>)[0].futureEventOption).toBe(true);
    expect((scenario.events as Array<Record<string, unknown>>)[1]).toMatchObject({
      type: "obs.hand.sign", actor: "sensor", kind: "observation", payload: "{\"sign\":\"paper\"}",
    });
  });

  it("keeps ordered events stable across edits, advanced replacement, movement, and deletion", () => {
    let projection = addScenario(projectDefinition(createDefaultDefinition()), "Order");
    projection = addScenarioEvent(projection, 0, "obs.user_utterance");
    projection = addScenarioEvent(projection, 0, "obs.social.context");
    projection = replaceScenarioEvent(projection, 0, 1, {
      type: "sys.custom", actor: "system", kind: "signal", payload: "advanced", future: 9,
    });
    projection = moveScenarioEvent(projection, 0, 1, -1);
    expect(scenarioViews(projection)[0].events.map((event) => event.type)).toEqual(["sys.custom", "obs.user_utterance"]);
    projection = deleteScenarioEvent(projection, 0, 1);
    expect(projection.source.verification?.scenarios[0].events).toEqual([{
      type: "sys.custom", actor: "system", kind: "signal", payload: "advanced", future: 9,
    }]);
  });

  it("builds capability event templates and parses JSON values without mutating on failure", () => {
    expect(eventTemplate("obs.user_utterance")).toEqual({
      type: "obs.user_utterance", actor: "user", kind: "observation", payload: "Example user message",
    });
    expect(eventTemplate("obs.hand.sign")).toMatchObject({ actor: "sensor", payload: "{\"sign\":\"rock\"}" });
    expect(parseJsonValue("{\"value\":2}")).toEqual({ ok: true, value: { value: 2 } });
    expect(parseJsonValue("{").ok).toBe(false);
  });
});

function fixture(): AgentDefinitionV1 {
  const definition = createDefaultDefinition();
  definition.interaction.supportedObservations = ["obs.user_utterance", "obs.hand.sign", "obs.social.context"];
  definition.storage.push({
    key: "count", description: "Count", valueSchema: { type: "integer" }, required: false,
    visibility: "internal", reset: "remove", examples: [],
  });
  definition.verification = { scenarios: [{
    name: "Imported",
    description: "Preserve me",
    initializerSeed: 42,
    initialStorage: { count: 1 },
    events: [{
      type: "obs.user_utterance", actor: "user", kind: "observation", payload: "hello",
      futureEventOption: true,
    }],
    expected: { activeStatePath: ["context", "main"], storage: { count: 2 }, behaviourFragments: [] },
    futureScenarioOption: { retained: true },
  }] };
  return definition;
}
