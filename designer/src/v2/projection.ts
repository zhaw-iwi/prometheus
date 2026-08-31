import {
  type AgentDefinitionV1,
  cloneJson,
  type ComponentEnvelope,
  isJsonObject,
  type JsonObject,
  type PromptSection,
  type StateDefinition,
  type TransitionDefinition,
} from "../model/agentDefinition";

export type GuidanceScope = "agent" | "situation";
export type ContinuationKind = "stay" | "move" | "finish";
export type DataRole = "starting-context" | "working-data" | "learned-information" | "outcome-report";

export interface IdentityProjection {
  key: string;
  revision: number;
  metadata: AgentDefinitionV1["metadata"];
}

export interface GuidanceProjection extends PromptSection {
  scope: GuidanceScope;
  stateId: string;
  promptField: string;
  sectionIndex: number;
  pointer: string;
}

export interface ComponentUseProjection {
  envelope: ComponentEnvelope;
  pointer: string;
  role: "ordinary-policy" | "selector" | "condition" | "effect" | "initializer" | "resource";
}

export interface CapabilityProjection {
  observations: string[];
  behaviourModalities: string[];
  profileTags: string[];
  installedComponents: Array<{
    kind: string;
    version: number;
    uses: ComponentUseProjection[];
  }>;
}

export interface SituationProjection {
  id: string;
  name: string;
  stateIndex: number;
  main: boolean;
  parentStateIds: string[];
  guidance: GuidanceProjection[];
  ordinaryPolicy: ComponentUseProjection | null;
  ruleIds: string[];
}

export interface InteractionRuleProjection {
  id: string;
  transitionIndex: number;
  pointer: string;
  sourceStateId: string;
  targetStateId: string;
  scope: "global" | "situation" | "technical";
  order: number;
  eventTypes: string[];
  conditions: ComponentUseProjection[];
  effects: ComponentUseProjection[];
  continuation: ContinuationKind;
}

export interface DataItemProjection {
  key: string;
  storageIndex: number;
  pointer: string;
  role: DataRole;
  declaration: JsonObject;
}

export interface DataProjection {
  items: DataItemProjection[];
  resources: JsonObject[];
  initializers: ComponentEnvelope[];
}

export interface OutcomeProjection {
  items: DataItemProjection[];
  effects: ComponentUseProjection[];
}

export interface VerificationProjection {
  scenarios: JsonObject[];
}

export interface DesignerV2Projection {
  source: AgentDefinitionV1;
  identity: IdentityProjection;
  guidance: GuidanceProjection[];
  capabilities: CapabilityProjection;
  situations: SituationProjection[];
  ordinaryPolicies: ComponentUseProjection[];
  rules: InteractionRuleProjection[];
  data: DataProjection;
  outcomes: OutcomeProjection;
  verification: VerificationProjection;
}

export function createDefaultDefinition(): AgentDefinitionV1 {
  const promptConfig = () => ({ consumedObservations: [], emittedModalities: [] });
  const selector = () => ({
    kind: "prometheus.selector.state-path",
    version: 1,
    config: {},
  });
  return {
    $schema: "/agent-definitions/schema/agent-definition.schema.json",
    schemaVersion: 1,
    key: "",
    revision: 1,
    metadata: {
      displayName: "",
      description: "",
      categoryPath: "designer",
      languageCode: "en",
      tags: [],
    },
    interaction: {
      supportedObservations: [],
      supportedBehaviourModalities: [],
      profileTags: [],
    },
    lifecycle: {
      initialStateId: "context",
      startOnCreation: true,
      initializers: [],
      reset: { storage: "initial", history: "clear" },
    },
    storage: [],
    resources: [],
    states: [
      {
        id: "context",
        name: "Agent context",
        kind: "composite",
        entryMode: "start",
        oblivious: false,
        eventSelector: selector(),
        policy: { kind: "prometheus.policy.prompt", version: 1, config: promptConfig() },
        childStateIds: ["main"],
        initialChildStateId: "main",
      },
      {
        id: "main",
        name: "Main interaction",
        kind: "atomic",
        entryMode: "start",
        oblivious: false,
        eventSelector: selector(),
        policy: { kind: "prometheus.policy.prompt", version: 1, config: promptConfig() },
      },
    ],
    transitions: [],
    verification: { scenarios: [] },
  };
}

export function projectDefinition(definition: AgentDefinitionV1): DesignerV2Projection {
  const source = cloneJson(definition);
  const stateById = new Map(source.states.map((state) => [state.id, state]));
  const parents = parentMap(source.states);
  const mainStateId = initialLeafId(source, stateById);
  const guidance: GuidanceProjection[] = [];
  const ordinaryPolicies: ComponentUseProjection[] = [];
  const componentUses: ComponentUseProjection[] = [];

  source.states.forEach((state, stateIndex) => {
    if (state.kind === "final") return;
    if (state.eventSelector) {
      componentUses.push(componentUse(state.eventSelector, `/states/${stateIndex}/eventSelector`, "selector"));
    }
    if (!state.policy) return;
    const policy = componentUse(state.policy, `/states/${stateIndex}/policy`, "ordinary-policy");
    ordinaryPolicies.push(policy);
    componentUses.push(policy);
    const scope: GuidanceScope = state.kind === "composite" ? "agent" : "situation";
    guidance.push(...guidanceFromPolicy(state.policy, state.id, stateIndex, scope));
  });

  const rules = source.transitions.map((transition, transitionIndex) => {
    const conditions = transition.decisions.map((envelope, index) => componentUse(
      envelope, `/transitions/${transitionIndex}/decisions/${index}`, "condition",
    ));
    const effects = transition.actions.map((envelope, index) => componentUse(
      envelope, `/transitions/${transitionIndex}/actions/${index}`, "effect",
    ));
    componentUses.push(...conditions, ...effects);
    const sourceState = stateById.get(transition.sourceStateId);
    const targetState = stateById.get(transition.targetStateId);
    return {
      id: transition.id,
      transitionIndex,
      pointer: `/transitions/${transitionIndex}`,
      sourceStateId: transition.sourceStateId,
      targetStateId: transition.targetStateId,
      scope: sourceState?.kind === "composite" ? "global"
        : sourceState?.kind === "atomic" ? "situation" : "technical",
      order: transition.order,
      eventTypes: latestEventTypes(transition),
      conditions,
      effects,
      continuation: targetState?.kind === "final" ? "finish"
        : transition.sourceStateId === transition.targetStateId ? "stay" : "move",
    } satisfies InteractionRuleProjection;
  });

  source.lifecycle.initializers.forEach((envelope, index) => componentUses.push(componentUse(
    envelope, `/lifecycle/initializers/${index}`, "initializer",
  )));
  source.resources.forEach((resource, index) => {
    const envelope = resourceEnvelope(resource);
    if (envelope) componentUses.push(componentUse(envelope, `/resources/${index}`, "resource"));
  });

  const situations = source.states.flatMap((state, stateIndex): SituationProjection[] => {
    if (state.kind !== "atomic") return [];
    const policy = ordinaryPolicies.find((candidate) => candidate.pointer === `/states/${stateIndex}/policy`) ?? null;
    return [{
      id: state.id,
      name: state.name,
      stateIndex,
      main: state.id === mainStateId,
      parentStateIds: parents.get(state.id) ?? [],
      guidance: guidance.filter((item) => item.scope === "situation" && item.stateId === state.id),
      ordinaryPolicy: policy,
      ruleIds: rules.filter((rule) => rule.sourceStateId === state.id).map((rule) => rule.id),
    }];
  });

  const initializerKeys = referencedStorageKeys(source.lifecycle.initializers);
  const learnedKeys = writtenStorageKeys(source.transitions.flatMap((transition) => transition.actions));
  const items = source.storage.map((declaration, storageIndex): DataItemProjection => {
    const key = typeof declaration.key === "string" ? declaration.key : `storage-${storageIndex + 1}`;
    return {
      key,
      storageIndex,
      pointer: `/storage/${storageIndex}`,
      role: dataRole(declaration, key, initializerKeys, learnedKeys),
      declaration: cloneJson(declaration),
    };
  });
  const outcomeItems = items.filter((item) => item.role === "outcome-report");
  const outcomeKeys = new Set(outcomeItems.map((item) => item.key));

  return {
    source,
    identity: {
      key: source.key,
      revision: source.revision,
      metadata: cloneJson(source.metadata),
    },
    guidance,
    capabilities: {
      observations: [...source.interaction.supportedObservations],
      behaviourModalities: [...source.interaction.supportedBehaviourModalities],
      profileTags: [...source.interaction.profileTags],
      installedComponents: groupComponentUses(componentUses),
    },
    situations,
    ordinaryPolicies,
    rules,
    data: {
      items,
      resources: cloneJson(source.resources),
      initializers: cloneJson(source.lifecycle.initializers),
    },
    outcomes: {
      items: outcomeItems,
      effects: rules.flatMap((rule) => rule.effects).filter((effect) =>
        [...referencedStorageKeys([effect.envelope])].some((key) => outcomeKeys.has(key))),
    },
    verification: {
      scenarios: cloneJson(source.verification?.scenarios ?? []),
    },
  };
}

export function definitionFromProjection(projection: DesignerV2Projection): AgentDefinitionV1 {
  return cloneJson(projection.source);
}

export function serializedDefinition(definition: AgentDefinitionV1): string {
  return JSON.stringify(stableObject(definition));
}

export function nextStableId(preferred: string, usedIds: Iterable<string>): string {
  const used = new Set(usedIds);
  const base = stableSegment(preferred) || "item";
  if (!used.has(base)) return base;
  let suffix = 2;
  while (used.has(`${base}-${suffix}`)) suffix += 1;
  return `${base}-${suffix}`;
}

export function nextTransitionOrder(transitions: TransitionDefinition[], sourceStateId?: string): number {
  const relevant = sourceStateId
    ? transitions.filter((transition) => transition.sourceStateId === sourceStateId)
    : transitions;
  return relevant.length === 0 ? 10 : Math.max(...relevant.map((transition) => transition.order)) + 10;
}

function guidanceFromPolicy(
  policy: ComponentEnvelope,
  stateId: string,
  stateIndex: number,
  scope: GuidanceScope,
): GuidanceProjection[] {
  return Object.entries(policy.config).flatMap(([promptField, prompt]) => {
    if (!promptField.endsWith("Prompt") || !isJsonObject(prompt) || !Array.isArray(prompt.sections)) return [];
    return prompt.sections.flatMap((section, sectionIndex): GuidanceProjection[] => {
      if (!isPromptSection(section)) return [];
      return [{
        ...cloneJson(section),
        scope,
        stateId,
        promptField,
        sectionIndex,
        pointer: `/states/${stateIndex}/policy/config/${escapePointer(promptField)}/sections/${sectionIndex}`,
      }];
    });
  });
}

function componentUse(
  envelope: ComponentEnvelope,
  pointer: string,
  role: ComponentUseProjection["role"],
): ComponentUseProjection {
  return { envelope: cloneJson(envelope), pointer, role };
}

function groupComponentUses(uses: ComponentUseProjection[]): CapabilityProjection["installedComponents"] {
  const grouped = new Map<string, CapabilityProjection["installedComponents"][number]>();
  for (const use of uses) {
    const key = `${use.envelope.kind}@${use.envelope.version}`;
    const current = grouped.get(key);
    if (current) current.uses.push(use);
    else grouped.set(key, { kind: use.envelope.kind, version: use.envelope.version, uses: [use] });
  }
  return [...grouped.values()];
}

function resourceEnvelope(resource: JsonObject): ComponentEnvelope | null {
  return typeof resource.kind === "string" && typeof resource.version === "number" && isJsonObject(resource.config)
    ? { kind: resource.kind, version: resource.version, config: resource.config }
    : null;
}

function parentMap(states: StateDefinition[]): Map<string, string[]> {
  const direct = new Map<string, string>();
  states.forEach((state) => {
    if (state.kind === "composite") state.childStateIds.forEach((childId) => direct.set(childId, state.id));
  });
  const result = new Map<string, string[]>();
  states.forEach((state) => {
    const ancestors: string[] = [];
    const visited = new Set<string>();
    let current = direct.get(state.id);
    while (current && !visited.has(current)) {
      visited.add(current);
      ancestors.unshift(current);
      current = direct.get(current);
    }
    result.set(state.id, ancestors);
  });
  return result;
}

function initialLeafId(definition: AgentDefinitionV1, states: Map<string, StateDefinition>): string | null {
  let current = definition.lifecycle.initialStateId;
  const visited = new Set<string>();
  while (current && !visited.has(current)) {
    visited.add(current);
    const state = states.get(current);
    if (!state || state.kind === "final") return null;
    if (state.kind === "atomic") return state.id;
    current = state.initialChildStateId;
  }
  return null;
}

function latestEventTypes(transition: TransitionDefinition): string[] {
  return transition.decisions.flatMap((decision) => {
    if (decision.kind !== "prometheus.decision.latest-event-type") return [];
    const value = decision.config.eventType;
    return typeof value === "string" ? [value] : [];
  });
}

function referencedStorageKeys(envelopes: ComponentEnvelope[]): Set<string> {
  const keys = new Set<string>();
  for (const envelope of envelopes) collectStorageKeys(envelope.config, keys);
  return keys;
}

function writtenStorageKeys(envelopes: ComponentEnvelope[]): Set<string> {
  const keys = new Set<string>();
  envelopes.forEach((envelope) => {
    const target = envelope.config.targetStorageKey;
    if (typeof target === "string") keys.add(target);
    const bindings = envelope.config.storageBindings;
    if (!Array.isArray(bindings)) return;
    bindings.forEach((binding) => {
      if (!isJsonObject(binding) || typeof binding.key !== "string") return;
      if (binding.access === "write" || binding.access === "read-write") keys.add(binding.key);
    });
  });
  return keys;
}

function collectStorageKeys(value: unknown, keys: Set<string>, propertyName = ""): void {
  if (typeof value === "string" && /storagekey$/i.test(propertyName)) {
    keys.add(value);
    return;
  }
  if (Array.isArray(value)) {
    value.forEach((item) => collectStorageKeys(item, keys, propertyName));
    return;
  }
  if (!isJsonObject(value)) return;
  Object.entries(value).forEach(([key, child]) => collectStorageKeys(child, keys, key));
}

function dataRole(
  declaration: JsonObject,
  key: string,
  initializerKeys: Set<string>,
  learnedKeys: Set<string>,
): DataRole {
  if (declaration.visibility === "outcome") return "outcome-report";
  if (initializerKeys.has(key) || Object.hasOwn(declaration, "initialValue")) return "starting-context";
  if (learnedKeys.has(key)) return "learned-information";
  return "working-data";
}

function isPromptSection(value: unknown): value is PromptSection {
  return isJsonObject(value) && typeof value.id === "string" && typeof value.kind === "string"
    && typeof value.content === "string";
}

function stableSegment(value: string): string {
  return value.toLowerCase().trim().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "");
}

function escapePointer(value: string): string {
  return value.replaceAll("~", "~0").replaceAll("/", "~1");
}

function stableObject(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(stableObject);
  if (!isJsonObject(value)) return value;
  return Object.fromEntries(Object.entries(value)
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([key, child]) => [key, stableObject(child)]));
}
