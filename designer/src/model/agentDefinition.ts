export type JsonScalar = string | number | boolean | null;
export type JsonValue = JsonScalar | JsonValue[] | { [key: string]: JsonValue };
export type JsonObject = Record<string, unknown>;

export interface PromptSection {
  id: string;
  kind: string;
  content: string;
}

export interface ComponentEnvelope {
  kind: string;
  version: number;
  config: JsonObject;
}

export interface AgentMetadata {
  displayName: string;
  description: string;
  categoryPath: string;
  languageCode: string | null;
  tags: string[];
}

export interface InteractionContract {
  supportedObservations: string[];
  supportedBehaviourModalities: string[];
  profileTags: string[];
}

export interface AtomicStateDefinition {
  id: string;
  name: string;
  kind: "atomic";
  entryMode: "start" | "reprocess-event";
  oblivious: boolean;
  eventSelector: ComponentEnvelope | null;
  policy: ComponentEnvelope | null;
  [key: string]: unknown;
}

export interface CompositeStateDefinition {
  id: string;
  name: string;
  kind: "composite";
  entryMode: "start" | "reprocess-event";
  oblivious: boolean;
  eventSelector: ComponentEnvelope | null;
  policy: ComponentEnvelope | null;
  childStateIds: string[];
  initialChildStateId: string;
  [key: string]: unknown;
}

export interface FinalStateDefinition {
  id: string;
  name: string;
  kind: "final";
  [key: string]: unknown;
}

export type StateDefinition = AtomicStateDefinition | CompositeStateDefinition | FinalStateDefinition;

export interface TransitionDefinition {
  id: string;
  sourceStateId: string;
  targetStateId: string;
  order: number;
  decisions: ComponentEnvelope[];
  actions: ComponentEnvelope[];
  [key: string]: unknown;
}

export interface AgentDefinitionV1 {
  $schema: "/agent-definitions/schema/agent-definition.schema.json";
  schemaVersion: 1;
  key: string;
  revision: number;
  metadata: AgentMetadata;
  interaction: InteractionContract;
  lifecycle: {
    initialStateId: string;
    startOnCreation: boolean;
    initializers: ComponentEnvelope[];
    reset: { storage: "initial"; history: "clear" };
  };
  storage: JsonObject[];
  resources: JsonObject[];
  states: StateDefinition[];
  transitions: TransitionDefinition[];
  verification?: { scenarios: JsonObject[] };
}

export function cloneJson<T>(value: T): T {
  return structuredClone(value);
}

export function isJsonObject(value: unknown): value is JsonObject {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
