import {
  cloneJson,
  isJsonObject,
  type AgentDefinitionV1,
  type JsonObject,
  type JsonValue,
} from "../model/agentDefinition";
import { projectDefinition, type DesignerV2Projection } from "./projection";

export interface ScenarioEventView {
  eventIndex: number;
  source: JsonObject;
  type: string;
  actor: string;
  kind: string;
  payload: string;
}

export interface ScenarioView {
  scenarioIndex: number;
  pointer: string;
  source: JsonObject;
  name: string;
  description: string;
  initializerSeed: number | null;
  initialStorage: JsonObject;
  events: ScenarioEventView[];
  expected: JsonObject;
}

export function scenarioViews(projection: DesignerV2Projection): ScenarioView[] {
  return projection.verification.scenarios.map((source, scenarioIndex) => {
    const events = Array.isArray(source.events) ? source.events : [];
    return {
      scenarioIndex,
      pointer: `/verification/scenarios/${scenarioIndex}`,
      source: cloneJson(source),
      name: typeof source.name === "string" ? source.name : "",
      description: typeof source.description === "string" ? source.description : "",
      initializerSeed: typeof source.initializerSeed === "number" ? source.initializerSeed : null,
      initialStorage: isJsonObject(source.initialStorage) ? cloneJson(source.initialStorage) : {},
      events: events.flatMap((event, eventIndex): ScenarioEventView[] => isJsonObject(event) ? [{
        eventIndex,
        source: cloneJson(event),
        type: typeof event.type === "string" ? event.type : "",
        actor: typeof event.actor === "string" ? event.actor : "",
        kind: typeof event.kind === "string" ? event.kind : "",
        payload: typeof event.payload === "string" ? event.payload : "",
      }] : []),
      expected: isJsonObject(source.expected) ? cloneJson(source.expected) : {},
    };
  });
}

export function addScenario(projection: DesignerV2Projection, name = "New scenario"): DesignerV2Projection {
  const definition = cloneJson(projection.source);
  if (!definition.verification) definition.verification = { scenarios: [] };
  definition.verification.scenarios.push({ name, events: [], expected: {} });
  return projectDefinition(definition);
}

export function updateScenario(
  projection: DesignerV2Projection,
  scenarioIndex: number,
  patch: { name?: string; description?: string; initializerSeed?: number | null },
): DesignerV2Projection {
  return updateScenarioSource(projection, scenarioIndex, (scenario) => {
    if (patch.name !== undefined) scenario.name = patch.name;
    if (patch.description !== undefined) {
      if (patch.description.trim()) scenario.description = patch.description;
      else delete scenario.description;
    }
    if (patch.initializerSeed !== undefined) {
      if (patch.initializerSeed === null) delete scenario.initializerSeed;
      else scenario.initializerSeed = Math.trunc(patch.initializerSeed);
    }
  });
}

export function deleteScenario(projection: DesignerV2Projection, scenarioIndex: number): DesignerV2Projection {
  const definition = cloneJson(projection.source);
  if (!definition.verification?.scenarios[scenarioIndex]) return projection;
  definition.verification.scenarios.splice(scenarioIndex, 1);
  if (definition.verification.scenarios.length === 0) delete definition.verification;
  return projectDefinition(definition);
}

export function moveScenario(
  projection: DesignerV2Projection,
  scenarioIndex: number,
  direction: -1 | 1,
): DesignerV2Projection {
  const definition = cloneJson(projection.source);
  const scenarios = definition.verification?.scenarios;
  const destination = scenarioIndex + direction;
  if (!scenarios || !scenarios[scenarioIndex] || destination < 0 || destination >= scenarios.length) return projection;
  [scenarios[scenarioIndex], scenarios[destination]] = [scenarios[destination], scenarios[scenarioIndex]];
  return projectDefinition(definition);
}

export function setScenarioStorage(
  projection: DesignerV2Projection,
  scenarioIndex: number,
  field: "initialStorage" | "expected",
  key: string,
  value: JsonValue,
): DesignerV2Projection {
  return updateScenarioSource(projection, scenarioIndex, (scenario) => {
    const target = field === "initialStorage" ? ensureObject(scenario, "initialStorage")
      : ensureObject(ensureObject(scenario, "expected"), "storage");
    target[key] = cloneJson(value);
  });
}

export function removeScenarioStorage(
  projection: DesignerV2Projection,
  scenarioIndex: number,
  field: "initialStorage" | "expected",
  key: string,
): DesignerV2Projection {
  return updateScenarioSource(projection, scenarioIndex, (scenario) => {
    const target = field === "initialStorage" ? scenario.initialStorage
      : isJsonObject(scenario.expected) ? scenario.expected.storage : undefined;
    if (!isJsonObject(target)) return;
    delete target[key];
    if (Object.keys(target).length > 0) return;
    if (field === "initialStorage") delete scenario.initialStorage;
    else delete (scenario.expected as JsonObject).storage;
  });
}

export function addScenarioEvent(
  projection: DesignerV2Projection,
  scenarioIndex: number,
  type: string,
): DesignerV2Projection {
  return updateScenarioSource(projection, scenarioIndex, (scenario) => {
    const events = Array.isArray(scenario.events) ? scenario.events : [];
    events.push(eventTemplate(type));
    scenario.events = events;
  });
}

export function updateScenarioEvent(
  projection: DesignerV2Projection,
  scenarioIndex: number,
  eventIndex: number,
  patch: Partial<Pick<ScenarioEventView, "type" | "actor" | "kind" | "payload">>,
): DesignerV2Projection {
  return updateScenarioSource(projection, scenarioIndex, (scenario) => {
    if (!Array.isArray(scenario.events) || !isJsonObject(scenario.events[eventIndex])) return;
    Object.assign(scenario.events[eventIndex], patch);
  });
}

export function replaceScenarioEvent(
  projection: DesignerV2Projection,
  scenarioIndex: number,
  eventIndex: number,
  event: JsonObject,
): DesignerV2Projection {
  return updateScenarioSource(projection, scenarioIndex, (scenario) => {
    if (!Array.isArray(scenario.events) || !scenario.events[eventIndex]) return;
    scenario.events[eventIndex] = cloneJson(event);
  });
}

export function deleteScenarioEvent(
  projection: DesignerV2Projection,
  scenarioIndex: number,
  eventIndex: number,
): DesignerV2Projection {
  return updateScenarioSource(projection, scenarioIndex, (scenario) => {
    if (Array.isArray(scenario.events)) scenario.events.splice(eventIndex, 1);
  });
}

export function moveScenarioEvent(
  projection: DesignerV2Projection,
  scenarioIndex: number,
  eventIndex: number,
  direction: -1 | 1,
): DesignerV2Projection {
  return updateScenarioSource(projection, scenarioIndex, (scenario) => {
    if (!Array.isArray(scenario.events)) return;
    const destination = eventIndex + direction;
    if (!scenario.events[eventIndex] || destination < 0 || destination >= scenario.events.length) return;
    [scenario.events[eventIndex], scenario.events[destination]] = [scenario.events[destination], scenario.events[eventIndex]];
  });
}

export function setExpectedSituation(
  projection: DesignerV2Projection,
  scenarioIndex: number,
  situationId: string | null,
): DesignerV2Projection {
  return updateScenarioSource(projection, scenarioIndex, (scenario) => {
    const expected = ensureObject(scenario, "expected");
    if (!situationId) {
      delete expected.activeStatePath;
      return;
    }
    expected.activeStatePath = canonicalStatePath(projection.source, situationId);
  });
}

export function addBehaviourFragment(
  projection: DesignerV2Projection,
  scenarioIndex: number,
  fragment: JsonValue,
): DesignerV2Projection {
  return updateScenarioSource(projection, scenarioIndex, (scenario) => {
    const expected = ensureObject(scenario, "expected");
    const fragments = Array.isArray(expected.behaviourFragments) ? expected.behaviourFragments : [];
    fragments.push(cloneJson(fragment));
    expected.behaviourFragments = fragments;
  });
}

export function updateBehaviourFragment(
  projection: DesignerV2Projection,
  scenarioIndex: number,
  fragmentIndex: number,
  fragment: JsonValue,
): DesignerV2Projection {
  return updateScenarioSource(projection, scenarioIndex, (scenario) => {
    const expected = ensureObject(scenario, "expected");
    if (!Array.isArray(expected.behaviourFragments) || expected.behaviourFragments[fragmentIndex] === undefined) return;
    expected.behaviourFragments[fragmentIndex] = cloneJson(fragment);
  });
}

export function deleteBehaviourFragment(
  projection: DesignerV2Projection,
  scenarioIndex: number,
  fragmentIndex: number,
): DesignerV2Projection {
  return updateScenarioSource(projection, scenarioIndex, (scenario) => {
    const expected = ensureObject(scenario, "expected");
    if (!Array.isArray(expected.behaviourFragments)) return;
    expected.behaviourFragments.splice(fragmentIndex, 1);
    if (expected.behaviourFragments.length === 0) delete expected.behaviourFragments;
  });
}

export function eventTemplate(type: string): JsonObject {
  const structured = type !== "obs.user_utterance";
  const payload = type === "obs.hand.sign" ? "{\"sign\":\"rock\"}" : structured ? "{}" : "Example user message";
  return { type, actor: structured ? "sensor" : "user", kind: "observation", payload };
}

export function expectedSituationId(projection: DesignerV2Projection, scenario: ScenarioView): string {
  const path = Array.isArray(scenario.expected.activeStatePath) ? scenario.expected.activeStatePath : [];
  const candidate = path.at(-1);
  return typeof candidate === "string" && projection.source.states.some((state) => state.id === candidate)
    ? candidate : "";
}

export function canonicalStatePath(definition: AgentDefinitionV1, stateId: string): string[] {
  const parents = new Map<string, string>();
  definition.states.forEach((state) => {
    if (state.kind === "composite") state.childStateIds.forEach((childId) => parents.set(childId, state.id));
  });
  const path = [stateId];
  const visited = new Set(path);
  let current = parents.get(stateId);
  while (current && !visited.has(current)) {
    path.unshift(current);
    visited.add(current);
    current = parents.get(current);
  }
  return path;
}

export function parseJsonValue(source: string): { ok: true; value: JsonValue } | { ok: false; message: string } {
  try {
    return { ok: true, value: JSON.parse(source) as JsonValue };
  } catch (error) {
    return { ok: false, message: error instanceof Error ? error.message : "Invalid JSON value" };
  }
}

function updateScenarioSource(
  projection: DesignerV2Projection,
  scenarioIndex: number,
  updater: (scenario: JsonObject) => void,
): DesignerV2Projection {
  const definition = cloneJson(projection.source);
  const scenario = definition.verification?.scenarios[scenarioIndex];
  if (!scenario) return projection;
  updater(scenario);
  return projectDefinition(definition);
}

function ensureObject(target: JsonObject, key: string): JsonObject {
  if (!isJsonObject(target[key])) target[key] = {};
  return target[key] as JsonObject;
}
