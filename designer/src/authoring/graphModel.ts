import type { ComponentDefinition } from "../api/designerApi";
import {
  type AgentDefinitionV1,
  cloneJson,
  type ComponentEnvelope,
  type CompositeStateDefinition,
  type StateDefinition,
  type TransitionDefinition,
} from "../model/agentDefinition";
import { STABLE_ID_PATTERN } from "./editorModel";

export type StateKind = StateDefinition["kind"];

export interface MissingCapabilities {
  observations: string[];
  modalities: string[];
}

export function addState(
  definition: AgentDefinitionV1,
  kind: StateKind,
  name = kind === "final" ? "Finished" : "New situation",
): AgentDefinitionV1 {
  const next = cloneJson(definition);
  const id = uniqueId(next.states.map((state) => state.id), stableSegment(name) || kind);
  const wrappedInitialId = next.states.some((state) => state.id === next.lifecycle.initialStateId)
    ? next.lifecycle.initialStateId : next.states[0]?.id ?? "";
  const state: StateDefinition = kind === "final"
    ? { id, name, kind }
    : kind === "composite"
      ? {
          id, name, kind, entryMode: "start", oblivious: false, eventSelector: null, policy: null,
          childStateIds: wrappedInitialId ? [wrappedInitialId] : [], initialChildStateId: wrappedInitialId,
        }
      : {
          id, name, kind, entryMode: "start", oblivious: false,
          eventSelector: { kind: "prometheus.selector.state-path", version: 1, config: {} },
          policy: null,
        };
  next.states.push(state);
  if (kind === "composite" && wrappedInitialId) next.lifecycle.initialStateId = id;
  return next;
}

export function expandDefaultState(definition: AgentDefinitionV1): AgentDefinitionV1 {
  if (definition.states.length !== 1) return cloneJson(definition);
  return addState(definition, "atomic", "Next situation");
}

export function renameState(definition: AgentDefinitionV1, stateId: string, name: string): AgentDefinitionV1 {
  const next = cloneJson(definition);
  const state = next.states.find((candidate) => candidate.id === stateId);
  if (state) state.name = name;
  return next;
}

export function replaceState(definition: AgentDefinitionV1, replacement: StateDefinition): AgentDefinitionV1 {
  const next = cloneJson(definition);
  const index = next.states.findIndex((state) => state.id === replacement.id);
  if (index >= 0) next.states[index] = cloneJson(replacement);
  return next;
}

export function deleteState(definition: AgentDefinitionV1, stateId: string): AgentDefinitionV1 {
  if (definition.states.length <= 1) return cloneJson(definition);
  const next = cloneJson(definition);
  next.states = next.states.filter((state) => state.id !== stateId);
  next.transitions = next.transitions.filter((transition) => transition.sourceStateId !== stateId
    && transition.targetStateId !== stateId);
  for (const state of next.states) {
    if (state.kind !== "composite") continue;
    state.childStateIds = state.childStateIds.filter((childId) => childId !== stateId);
    if (state.initialChildStateId === stateId) state.initialChildStateId = state.childStateIds[0] ?? "";
  }
  if (next.lifecycle.initialStateId === stateId) {
    next.lifecycle.initialStateId = next.states.find((state) => !parentId(next, state.id))?.id ?? next.states[0].id;
  }
  return normalizeTransitionOrders(next);
}

export function moveState(definition: AgentDefinitionV1, stateId: string, direction: -1 | 1): AgentDefinitionV1 {
  const next = cloneJson(definition);
  const index = next.states.findIndex((state) => state.id === stateId);
  const target = index + direction;
  if (index < 0 || target < 0 || target >= next.states.length) return next;
  [next.states[index], next.states[target]] = [next.states[target], next.states[index]];
  return next;
}

export function setInitialState(definition: AgentDefinitionV1, stateId: string): AgentDefinitionV1 {
  const next = cloneJson(definition);
  if (next.states.some((state) => state.id === stateId) && !parentId(next, stateId)) {
    next.lifecycle.initialStateId = stateId;
  }
  return next;
}

export function assignParent(
  definition: AgentDefinitionV1,
  childId: string,
  compositeId: string | null,
): AgentDefinitionV1 {
  if (compositeId && (compositeId === childId || descendantIds(definition, childId).has(compositeId)
    || !definition.states.some((state) => state.id === compositeId && state.kind === "composite"))) {
    return cloneJson(definition);
  }
  const next = cloneJson(definition);
  for (const state of next.states) {
    if (state.kind !== "composite") continue;
    state.childStateIds = state.childStateIds.filter((id) => id !== childId);
    if (state.initialChildStateId === childId) state.initialChildStateId = state.childStateIds[0] ?? "";
  }
  if (!compositeId) return next;
  const parent = next.states.find((state): state is CompositeStateDefinition => state.id === compositeId
    && state.kind === "composite");
  if (!parent) return next;
  parent.childStateIds.push(childId);
  if (!parent.initialChildStateId) parent.initialChildStateId = childId;
  if (next.lifecycle.initialStateId === childId) next.lifecycle.initialStateId = parent.id;
  return next;
}

export function setInitialChild(
  definition: AgentDefinitionV1,
  compositeId: string,
  childId: string,
): AgentDefinitionV1 {
  const next = cloneJson(definition);
  const composite = next.states.find((state): state is CompositeStateDefinition => state.id === compositeId
    && state.kind === "composite");
  if (composite?.childStateIds.includes(childId)) composite.initialChildStateId = childId;
  return next;
}

export function moveCompositeChild(
  definition: AgentDefinitionV1,
  compositeId: string,
  childId: string,
  direction: -1 | 1,
): AgentDefinitionV1 {
  const next = cloneJson(definition);
  const composite = next.states.find((state): state is CompositeStateDefinition => state.id === compositeId
    && state.kind === "composite");
  if (!composite) return next;
  const index = composite.childStateIds.indexOf(childId);
  const target = index + direction;
  if (index < 0 || target < 0 || target >= composite.childStateIds.length) return next;
  [composite.childStateIds[index], composite.childStateIds[target]] = [
    composite.childStateIds[target], composite.childStateIds[index],
  ];
  return next;
}

export function addTransition(
  definition: AgentDefinitionV1,
  sourceStateId: string,
  targetStateId: string,
  prefix = "move",
): AgentDefinitionV1 {
  const next = cloneJson(definition);
  if (!next.states.some((state) => state.id === sourceStateId && state.kind !== "final")
    || !next.states.some((state) => state.id === targetStateId)) return next;
  const id = uniqueId(next.transitions.map((transition) => transition.id), `${prefix}_${sourceStateId}`);
  const sourceOrders = next.transitions.filter((transition) => transition.sourceStateId === sourceStateId)
    .map((transition) => transition.order);
  next.transitions.push({
    id,
    sourceStateId,
    targetStateId,
    order: (sourceOrders.length ? Math.max(...sourceOrders) : 0) + 10,
    decisions: [],
    actions: [],
  });
  return next;
}

export function addReaction(
  definition: AgentDefinitionV1,
  observation: string,
): AgentDefinitionV1 {
  const active = activeLeafId(definition);
  const sourceStateId = definition.states.find((state) => state.id === active && state.kind !== "final")?.id
    ?? definition.states.find((state) => state.kind !== "final")?.id;
  if (!sourceStateId) return cloneJson(definition);
  let next = addTransition(definition, sourceStateId, sourceStateId, "reaction");
  const transition = next.transitions.at(-1);
  if (!transition) return next;
  transition.decisions = [{
    kind: "prometheus.decision.latest-event-type",
    version: 1,
    config: { eventType: observation },
  }];
  return next;
}

export function replaceTransition(
  definition: AgentDefinitionV1,
  replacement: TransitionDefinition,
): AgentDefinitionV1 {
  const next = cloneJson(definition);
  const index = next.transitions.findIndex((transition) => transition.id === replacement.id);
  if (index < 0) return next;
  const oldSource = next.transitions[index].sourceStateId;
  next.transitions[index] = cloneJson(replacement);
  return normalizeTransitionOrders(next, new Set([oldSource, replacement.sourceStateId]));
}

export function deleteTransition(definition: AgentDefinitionV1, transitionId: string): AgentDefinitionV1 {
  const next = cloneJson(definition);
  const source = next.transitions.find((transition) => transition.id === transitionId)?.sourceStateId;
  next.transitions = next.transitions.filter((transition) => transition.id !== transitionId);
  return source ? normalizeTransitionOrders(next, new Set([source])) : next;
}

export function moveTransition(
  definition: AgentDefinitionV1,
  transitionId: string,
  direction: -1 | 1,
): AgentDefinitionV1 {
  const next = cloneJson(definition);
  const transition = next.transitions.find((candidate) => candidate.id === transitionId);
  if (!transition) return next;
  const sourceTransitions = next.transitions.filter((candidate) => candidate.sourceStateId === transition.sourceStateId)
    .sort((left, right) => left.order - right.order);
  const index = sourceTransitions.findIndex((candidate) => candidate.id === transitionId);
  const target = index + direction;
  if (target < 0 || target >= sourceTransitions.length) return next;
  const currentDocumentIndex = next.transitions.findIndex((candidate) => candidate.id === sourceTransitions[index].id);
  const targetDocumentIndex = next.transitions.findIndex((candidate) => candidate.id === sourceTransitions[target].id);
  [next.transitions[currentDocumentIndex], next.transitions[targetDocumentIndex]] = [
    next.transitions[targetDocumentIndex], next.transitions[currentDocumentIndex],
  ];
  next.transitions.filter((candidate) => candidate.sourceStateId === transition.sourceStateId)
    .sort((left, right) => next.transitions.indexOf(left) - next.transitions.indexOf(right))
    .forEach((candidate, orderIndex) => { candidate.order = (orderIndex + 1) * 10; });
  return next;
}

export function reactionObservation(transition: TransitionDefinition): string {
  const trigger = transition.decisions.find((decision) => decision.kind === "prometheus.decision.latest-event-type");
  return typeof trigger?.config.eventType === "string" ? trigger.config.eventType : "";
}

export function setReactionObservation(
  transition: TransitionDefinition,
  observation: string,
): TransitionDefinition {
  const next = cloneJson(transition);
  const index = next.decisions.findIndex((decision) => decision.kind === "prometheus.decision.latest-event-type");
  const trigger: ComponentEnvelope = {
    kind: "prometheus.decision.latest-event-type", version: 1, config: { eventType: observation },
  };
  if (index < 0) next.decisions.unshift(trigger);
  else next.decisions[index] = trigger;
  return next;
}

export function envelopeFromComponent(component: ComponentDefinition): ComponentEnvelope {
  return { kind: component.kind, version: component.version, config: cloneJson(component.defaultConfig) };
}

export function missingCapabilities(
  definition: AgentDefinitionV1,
  components: ComponentDefinition[],
): MissingCapabilities {
  const observations = new Set<string>();
  const modalities = new Set<string>();
  const envelopes = [
    ...definition.states.flatMap((state) => state.kind === "final"
      ? [] : [state.eventSelector, state.policy].filter((value): value is ComponentEnvelope => value !== null)),
    ...definition.transitions.flatMap((transition) => [...transition.decisions, ...transition.actions]),
  ];
  for (const envelope of envelopes) {
    const component = components.find((candidate) => candidate.kind === envelope.kind
      && candidate.version === envelope.version);
    component?.capabilities.consumedObservations.forEach((value) => observations.add(value));
    component?.capabilities.emittedBehaviourModalities.forEach((value) => modalities.add(value));
    collectStringConfig(envelope.config, "eventType").forEach((value) => observations.add(value));
    collectStringConfig(envelope.config, "consumedObservations").forEach((value) => observations.add(value));
    collectStringConfig(envelope.config, "emittedModalities").forEach((value) => modalities.add(value));
  }
  return {
    observations: [...observations].filter((value) => !definition.interaction.supportedObservations.includes(value)),
    modalities: [...modalities].filter((value) => !definition.interaction.supportedBehaviourModalities.includes(value)),
  };
}

export function synchronizeCapabilities(
  definition: AgentDefinitionV1,
  missing: MissingCapabilities,
): AgentDefinitionV1 {
  const next = cloneJson(definition);
  next.interaction.supportedObservations = unique([
    ...next.interaction.supportedObservations, ...missing.observations,
  ]);
  next.interaction.supportedBehaviourModalities = unique([
    ...next.interaction.supportedBehaviourModalities, ...missing.modalities,
  ]);
  return next;
}

export function parentId(definition: AgentDefinitionV1, stateId: string): string | null {
  return definition.states.find((state) => state.kind === "composite"
    && state.childStateIds.includes(stateId))?.id ?? null;
}

export function activeLeafId(definition: AgentDefinitionV1): string | null {
  let id = definition.lifecycle.initialStateId;
  const visited = new Set<string>();
  while (id && !visited.has(id)) {
    visited.add(id);
    const state = definition.states.find((candidate) => candidate.id === id);
    if (!state) return null;
    if (state.kind !== "composite") return state.id;
    id = state.initialChildStateId;
  }
  return null;
}

export function isStableId(value: string): boolean {
  return STABLE_ID_PATTERN.test(value);
}

function normalizeTransitionOrders(
  definition: AgentDefinitionV1,
  sources = new Set(definition.transitions.map((transition) => transition.sourceStateId)),
): AgentDefinitionV1 {
  for (const source of sources) {
    definition.transitions.filter((transition) => transition.sourceStateId === source)
      .sort((left, right) => left.order - right.order)
      .forEach((transition, index) => { transition.order = (index + 1) * 10; });
  }
  return definition;
}

function descendantIds(definition: AgentDefinitionV1, stateId: string): Set<string> {
  const result = new Set<string>();
  const queue = [stateId];
  while (queue.length) {
    const current = queue.shift();
    const state = definition.states.find((candidate) => candidate.id === current);
    if (state?.kind !== "composite") continue;
    for (const child of state.childStateIds) {
      if (result.has(child)) continue;
      result.add(child);
      queue.push(child);
    }
  }
  return result;
}

function collectStringConfig(config: Record<string, unknown>, key: string): string[] {
  const value = config[key];
  if (typeof value === "string") return [value];
  if (Array.isArray(value)) return value.filter((item): item is string => typeof item === "string");
  return [];
}

function unique(values: string[]): string[] {
  return [...new Set(values.filter(Boolean))];
}

function uniqueId(existing: string[], wanted: string): string {
  const base = isStableId(wanted) ? wanted : "item";
  if (!existing.includes(base)) return base;
  let suffix = 2;
  while (existing.includes(`${base}_${suffix}`)) suffix += 1;
  return `${base}_${suffix}`;
}

function stableSegment(value: string): string {
  return value.toLowerCase().trim().replace(/[^a-z0-9]+/g, "_").replace(/^_+|_+$/g, "");
}
