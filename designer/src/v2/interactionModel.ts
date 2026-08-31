import type { ComponentDefinition } from "../api/designerApi";
import {
  cloneJson,
  isJsonObject,
  type AgentDefinitionV1,
  type ComponentEnvelope,
  type JsonObject,
  type PromptSection,
  type TransitionDefinition,
} from "../model/agentDefinition";
import { guidanceIntent } from "./authoringCatalog";
import {
  nextStableId,
  nextTransitionOrder,
  projectDefinition,
  type ContinuationKind,
  type DesignerV2Projection,
} from "./projection";
import { replaceScopedGuidance } from "./transforms";

export const LATEST_EVENT_KIND = "prometheus.decision.latest-event-type";
export const PROMPT_CONDITION_KIND = "prometheus.decision.prompt";
export const PROMPT_EFFECT_KIND = "prometheus.action.prompt-behaviour";

export interface SituationDeletion {
  allowed: boolean;
  reason: string;
}

export function globalRuleSource(projection: DesignerV2Projection): string | null {
  const initial = projection.source.states.find((state) => state.id === projection.source.lifecycle.initialStateId);
  if (initial?.kind === "composite") return initial.id;
  const main = projection.situations.find((situation) => situation.main);
  return main?.parentStateIds[0]
    ?? projection.source.states.find((state) => state.kind === "composite")?.id
    ?? null;
}

export function addSituation(
  projection: DesignerV2Projection,
  name: string,
  promptPolicy?: ComponentEnvelope,
): DesignerV2Projection {
  const definition = cloneJson(projection.source);
  const id = nextStableId(name || "new-situation", definition.states.map((state) => state.id));
  const main = projection.situations.find((situation) => situation.main);
  const directParent = definition.states.find((state) => state.kind === "composite"
    && main && state.childStateIds.includes(main.id));
  definition.states.push({
    id,
    name: name.trim() || "New situation",
    kind: "atomic",
    entryMode: "start",
    oblivious: false,
    eventSelector: { kind: "prometheus.selector.state-path", version: 1, config: {} },
    policy: promptPolicy ? cloneJson(promptPolicy) : defaultPromptPolicy(),
  });
  if (directParent?.kind === "composite") directParent.childStateIds.push(id);
  return projectDefinition(definition);
}

export function renameSituation(
  projection: DesignerV2Projection,
  stateId: string,
  name: string,
): DesignerV2Projection {
  const definition = cloneJson(projection.source);
  const state = definition.states.find((candidate) => candidate.id === stateId);
  if (state?.kind === "atomic") state.name = name;
  return projectDefinition(definition);
}

export function situationDeletion(
  projection: DesignerV2Projection,
  stateId: string,
): SituationDeletion {
  const situation = projection.situations.find((candidate) => candidate.id === stateId);
  if (!situation) return { allowed: false, reason: "This situation is not available." };
  if (situation.main) return { allowed: false, reason: "Main is the required starting interaction." };
  const references = projection.rules.filter((rule) => rule.sourceStateId === stateId || rule.targetStateId === stateId);
  if (references.length > 0) {
    return { allowed: false, reason: `${references.length} rule${references.length === 1 ? " still refers" : "s still refer"} to this situation.` };
  }
  return { allowed: true, reason: "" };
}

export function deleteSituation(
  projection: DesignerV2Projection,
  stateId: string,
): DesignerV2Projection {
  if (!situationDeletion(projection, stateId).allowed) return projection;
  const definition = cloneJson(projection.source);
  definition.states = definition.states.filter((state) => state.id !== stateId);
  definition.states.forEach((state) => {
    if (state.kind !== "composite") return;
    state.childStateIds = state.childStateIds.filter((childId) => childId !== stateId);
    if (state.initialChildStateId === stateId) state.initialChildStateId = state.childStateIds[0] ?? "";
  });
  return projectDefinition(definition);
}

export function addRule(
  projection: DesignerV2Projection,
  sourceStateId: string,
  eventType: string,
): DesignerV2Projection {
  const definition = cloneJson(projection.source);
  const source = definition.states.find((state) => state.id === sourceStateId && state.kind !== "final");
  if (!source || !eventType) return projection;
  definition.transitions.push({
    id: nextStableId(`when-${eventType}-${sourceStateId}`, definition.transitions.map((transition) => transition.id)),
    sourceStateId,
    targetStateId: sourceStateId,
    order: nextTransitionOrder(definition.transitions, sourceStateId),
    decisions: [{ kind: LATEST_EVENT_KIND, version: 1, config: { eventType } }],
    actions: [],
  });
  return projectDefinition(definition);
}

export function deleteRule(projection: DesignerV2Projection, ruleId: string): DesignerV2Projection {
  const definition = cloneJson(projection.source);
  const source = definition.transitions.find((transition) => transition.id === ruleId)?.sourceStateId;
  definition.transitions = definition.transitions.filter((transition) => transition.id !== ruleId);
  if (source) normalizeRuleOrders(definition, source);
  return projectDefinition(definition);
}

export function moveRule(
  projection: DesignerV2Projection,
  ruleId: string,
  direction: -1 | 1,
): DesignerV2Projection {
  const definition = cloneJson(projection.source);
  const transition = definition.transitions.find((candidate) => candidate.id === ruleId);
  if (!transition) return projection;
  const siblings = definition.transitions.filter((candidate) => candidate.sourceStateId === transition.sourceStateId)
    .sort((left, right) => left.order - right.order);
  const index = siblings.findIndex((candidate) => candidate.id === ruleId);
  const destination = index + direction;
  if (index < 0 || destination < 0 || destination >= siblings.length) return projection;
  const currentOrder = siblings[index].order;
  siblings[index].order = siblings[destination].order;
  siblings[destination].order = currentOrder;
  normalizeRuleOrders(definition, transition.sourceStateId);
  return projectDefinition(definition);
}

export function setRuleEvent(
  projection: DesignerV2Projection,
  ruleId: string,
  eventType: string,
): DesignerV2Projection {
  return updateRule(projection, ruleId, (transition) => {
    const index = transition.decisions.findIndex((decision) => decision.kind === LATEST_EVENT_KIND);
    const trigger = { kind: LATEST_EVENT_KIND, version: 1, config: { eventType } } satisfies ComponentEnvelope;
    if (index < 0) transition.decisions.unshift(trigger);
    else transition.decisions[index] = trigger;
  });
}

export function setRuleContinuation(
  projection: DesignerV2Projection,
  ruleId: string,
  continuation: ContinuationKind,
  targetStateId?: string,
): DesignerV2Projection {
  const definition = cloneJson(projection.source);
  const transition = definition.transitions.find((candidate) => candidate.id === ruleId);
  if (!transition) return projection;
  if (continuation === "stay") transition.targetStateId = transition.sourceStateId;
  if (continuation === "move" && definition.states.some((state) => state.kind === "atomic" && state.id === targetStateId)) {
    transition.targetStateId = targetStateId!;
  }
  if (continuation === "finish") {
    let final = definition.states.find((state) => state.kind === "final");
    if (!final) {
      final = {
        id: nextStableId("done", definition.states.map((state) => state.id)),
        name: "Finished",
        kind: "final",
      };
      definition.states.push(final);
    }
    transition.targetStateId = final.id;
  }
  return projectDefinition(definition);
}

export function createSituationForRule(
  projection: DesignerV2Projection,
  ruleId: string,
  name: string,
  promptPolicy?: ComponentEnvelope,
): DesignerV2Projection {
  const before = new Set(projection.situations.map((situation) => situation.id));
  const withSituation = addSituation(projection, name, promptPolicy);
  const created = withSituation.situations.find((situation) => !before.has(situation.id));
  return created ? setRuleContinuation(withSituation, ruleId, "move", created.id) : projection;
}

export function addRuleComponent(
  projection: DesignerV2Projection,
  ruleId: string,
  role: "condition" | "effect",
  component: ComponentDefinition,
): DesignerV2Projection {
  const envelope = {
    kind: component.kind,
    version: component.version,
    config: cloneJson(component.defaultConfig),
  };
  if (component.kind === PROMPT_CONDITION_KIND) envelope.config = ensurePromptConfig(envelope.config, "decisionPrompt");
  if (component.kind === PROMPT_EFFECT_KIND) envelope.config = ensurePromptConfig(envelope.config, "responsePrompt");
  return updateRule(projection, ruleId, (transition) => {
    (role === "condition" ? transition.decisions : transition.actions).push(envelope);
  });
}

export function removeRuleComponent(
  projection: DesignerV2Projection,
  ruleId: string,
  role: "condition" | "effect",
  index: number,
): DesignerV2Projection {
  return updateRule(projection, ruleId, (transition) => {
    const list = role === "condition" ? transition.decisions : transition.actions;
    if (role === "condition" && list[index]?.kind === LATEST_EVENT_KIND) return;
    list.splice(index, 1);
  });
}

export function moveRuleComponent(
  projection: DesignerV2Projection,
  ruleId: string,
  role: "condition" | "effect",
  index: number,
  direction: -1 | 1,
): DesignerV2Projection {
  return updateRule(projection, ruleId, (transition) => {
    const list = role === "condition" ? transition.decisions : transition.actions;
    const destination = index + direction;
    if (!list[index] || destination < 0 || destination >= list.length) return;
    if (role === "condition" && (list[index].kind === LATEST_EVENT_KIND || list[destination].kind === LATEST_EVENT_KIND)) return;
    [list[index], list[destination]] = [list[destination], list[index]];
  });
}

export function updateRuleComponentConfig(
  projection: DesignerV2Projection,
  ruleId: string,
  role: "condition" | "effect",
  index: number,
  patch: JsonObject,
): DesignerV2Projection {
  return updateRule(projection, ruleId, (transition) => {
    const envelope = (role === "condition" ? transition.decisions : transition.actions)[index];
    if (envelope) envelope.config = { ...cloneJson(envelope.config), ...cloneJson(patch) };
  });
}

export function promptSections(envelope: ComponentEnvelope, promptField: string): PromptSection[] {
  const prompt = envelope.config[promptField];
  if (!isJsonObject(prompt) || !Array.isArray(prompt.sections)) return [];
  return prompt.sections.flatMap((section) => isPromptSection(section)
    ? [{ id: section.id, kind: section.kind, content: section.content }] : []);
}

export function updateRulePromptSection(
  projection: DesignerV2Projection,
  ruleId: string,
  role: "condition" | "effect",
  componentIndex: number,
  promptField: string,
  sectionIndex: number,
  patch: Partial<PromptSection>,
): DesignerV2Projection {
  return updateRulePromptSections(projection, ruleId, role, componentIndex, promptField, (sections) => {
    if (sections[sectionIndex]) sections[sectionIndex] = { ...sections[sectionIndex], ...patch };
  });
}

export function addRulePromptSection(
  projection: DesignerV2Projection,
  ruleId: string,
  role: "condition" | "effect",
  componentIndex: number,
  promptField: string,
  kind: string,
): DesignerV2Projection {
  const used = allPromptSectionIds(projection.source);
  return updateRulePromptSections(projection, ruleId, role, componentIndex, promptField, (sections) => {
    sections.push({ id: nextStableId(kind, used), kind, content: "" });
  });
}

export function removeRulePromptSection(
  projection: DesignerV2Projection,
  ruleId: string,
  role: "condition" | "effect",
  componentIndex: number,
  promptField: string,
  sectionIndex: number,
): DesignerV2Projection {
  return updateRulePromptSections(projection, ruleId, role, componentIndex, promptField,
    (sections) => { sections.splice(sectionIndex, 1); });
}

export function addSituationGuidance(
  projection: DesignerV2Projection,
  stateId: string,
  kind: string,
): DesignerV2Projection {
  const intent = guidanceIntent(kind);
  const promptField = intent?.promptField ?? "responsePrompt";
  const current = situationSections(projection, stateId, promptField);
  return replaceScopedGuidance(projection, { stateId, promptField }, [...current, {
    id: nextStableId(kind, allPromptSectionIds(projection.source)),
    kind,
    content: intent?.example ?? "",
  }]);
}

export function updateSituationGuidance(
  projection: DesignerV2Projection,
  stateId: string,
  promptField: string,
  sectionIndex: number,
  patch: Partial<PromptSection>,
): DesignerV2Projection {
  const sections = situationSections(projection, stateId, promptField);
  if (sections[sectionIndex]) sections[sectionIndex] = { ...sections[sectionIndex], ...patch };
  return replaceScopedGuidance(projection, { stateId, promptField }, sections);
}

export function moveSituationGuidance(
  projection: DesignerV2Projection,
  stateId: string,
  promptField: string,
  sectionIndex: number,
  direction: -1 | 1,
): DesignerV2Projection {
  const sections = situationSections(projection, stateId, promptField);
  const destination = sectionIndex + direction;
  if (!sections[sectionIndex] || destination < 0 || destination >= sections.length) return projection;
  [sections[sectionIndex], sections[destination]] = [sections[destination], sections[sectionIndex]];
  return replaceScopedGuidance(projection, { stateId, promptField }, sections);
}

export function removeSituationGuidance(
  projection: DesignerV2Projection,
  stateId: string,
  promptField: string,
  sectionIndex: number,
): DesignerV2Projection {
  return replaceScopedGuidance(projection, { stateId, promptField },
    situationSections(projection, stateId, promptField).filter((_, index) => index !== sectionIndex));
}

export function availableRuleConditions(components: ComponentDefinition[]): ComponentDefinition[] {
  return components.filter((component) => component.exposure === "GUIDED"
    && component.category === "DECISION" && component.authoringRole === "RULE_CONDITION");
}

export function availableRuleEffects(components: ComponentDefinition[]): ComponentDefinition[] {
  const roles = new Set(["RULE_RESPONSE", "DETERMINISTIC_OPERATION", "DATA_UPDATE"]);
  return components.filter((component) => component.exposure === "GUIDED" && component.category === "ACTION"
    && roles.has(component.authoringRole));
}

function updateRule(
  projection: DesignerV2Projection,
  ruleId: string,
  updater: (transition: TransitionDefinition) => void,
): DesignerV2Projection {
  const definition = cloneJson(projection.source);
  const transition = definition.transitions.find((candidate) => candidate.id === ruleId);
  if (!transition) return projection;
  updater(transition);
  return projectDefinition(definition);
}

function updateRulePromptSections(
  projection: DesignerV2Projection,
  ruleId: string,
  role: "condition" | "effect",
  componentIndex: number,
  promptField: string,
  updater: (sections: PromptSection[]) => void,
): DesignerV2Projection {
  return updateRule(projection, ruleId, (transition) => {
    const envelope = (role === "condition" ? transition.decisions : transition.actions)[componentIndex];
    if (!envelope) return;
    const sections = promptSections(envelope, promptField);
    updater(sections);
    const currentPrompt = envelope.config[promptField];
    envelope.config[promptField] = { ...(isJsonObject(currentPrompt) ? cloneJson(currentPrompt) : {}), sections };
  });
}

function situationSections(
  projection: DesignerV2Projection,
  stateId: string,
  promptField: string,
): PromptSection[] {
  return projection.guidance.filter((item) => item.scope === "situation" && item.stateId === stateId
    && item.promptField === promptField)
    .sort((left, right) => left.sectionIndex - right.sectionIndex)
    .map(({ id, kind, content }) => ({ id, kind, content }));
}

function normalizeRuleOrders(definition: AgentDefinitionV1, source?: string) {
  const sources = source ? [source] : [...new Set(definition.transitions.map((transition) => transition.sourceStateId))];
  sources.forEach((sourceId) => definition.transitions.filter((transition) => transition.sourceStateId === sourceId)
    .sort((left, right) => left.order - right.order)
    .forEach((transition, index) => { transition.order = (index + 1) * 10; }));
}

function ensurePromptConfig(config: JsonObject, promptField: string): JsonObject {
  const next = cloneJson(config);
  const current = next[promptField];
  if (!isJsonObject(current) || !Array.isArray(current.sections) || current.sections.length === 0) {
    next[promptField] = {
      ...(isJsonObject(current) ? cloneJson(current) : {}),
      sections: [{
        id: promptField === "decisionPrompt" ? "decision-criterion" : "rule-response",
        kind: promptField === "decisionPrompt" ? "transition-criterion" : "objective",
        content: promptField === "decisionPrompt"
          ? "Describe when this rule should apply."
          : "Describe the response to produce when this rule applies.",
      }],
    };
  }
  return next;
}

function defaultPromptPolicy(): ComponentEnvelope {
  return {
    kind: "prometheus.policy.prompt",
    version: 1,
    config: { consumedObservations: [], emittedModalities: [] },
  };
}

function allPromptSectionIds(definition: AgentDefinitionV1): string[] {
  const ids: string[] = [];
  const envelopes: ComponentEnvelope[] = [
    ...definition.states.flatMap((state) => state.kind === "final" || !state.policy ? [] : [state.policy]),
    ...definition.transitions.flatMap((transition) => [...transition.decisions, ...transition.actions]),
  ];
  envelopes.forEach((envelope) => Object.values(envelope.config).forEach((value) => {
    if (!isJsonObject(value) || !Array.isArray(value.sections)) return;
    value.sections.forEach((section) => { if (isPromptSection(section)) ids.push(section.id); });
  }));
  return ids;
}

function isPromptSection(value: unknown): value is PromptSection {
  return isJsonObject(value) && typeof value.id === "string" && typeof value.kind === "string"
    && typeof value.content === "string";
}
