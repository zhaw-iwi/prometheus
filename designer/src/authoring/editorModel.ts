import type { ComponentDefinition } from "../api/designerApi";
import {
  type AgentDefinitionV1,
  type AtomicStateDefinition,
  cloneJson,
  isJsonObject,
  type JsonObject,
  type PromptSection,
} from "../model/agentDefinition";
import { PROMPT_FIELDS } from "./authoringCatalog";

export const DEFINITION_KEY_PATTERN = /^[a-z][a-z0-9_-]*(?:\.[a-z][a-z0-9_-]*)+$/;
export const STABLE_ID_PATTERN = /^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$/;
export const LANGUAGE_CODE_PATTERN = /^[a-z]{2,3}(?:-[A-Za-z0-9]{2,8})*$/;

export interface AuthoringForm {
  key: string;
  revision: number;
  displayName: string;
  description: string;
  categoryPath: string;
  languageCode: string;
  tags: string;
  supportedObservations: string[];
  supportedBehaviourModalities: string[];
  profileTags: string;
  strategyKind: string;
  strategyVersion: number;
  strategyConfig: JsonObject;
  promptSections: PromptSection[];
}

export interface LocalFormIssue {
  fieldId: string;
  message: string;
}

export function createDefaultDefinition(): AgentDefinitionV1 {
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
      initialStateId: "main",
      startOnCreation: true,
      initializers: [],
      reset: { storage: "initial", history: "clear" },
    },
    storage: [],
    resources: [],
    states: [
      {
        id: "main",
        name: "Main",
        kind: "atomic",
        entryMode: "start",
        oblivious: false,
        eventSelector: {
          kind: "prometheus.selector.state-path",
          version: 1,
          config: {},
        },
        policy: {
          kind: "prometheus.policy.prompt",
          version: 1,
          config: {
            consumedObservations: [],
            emittedModalities: [],
          },
        },
      },
    ],
    transitions: [],
    verification: { scenarios: [] },
  };
}

export function definitionToAuthoringForm(definition: AgentDefinitionV1): AuthoringForm {
  const state = initialAtomicState(definition);
  const policy = state?.policy;
  const config = policy?.config && isJsonObject(policy.config) ? cloneJson(policy.config) : {};
  return {
    key: definition.key,
    revision: definition.revision,
    displayName: definition.metadata.displayName,
    description: definition.metadata.description,
    categoryPath: definition.metadata.categoryPath,
    languageCode: definition.metadata.languageCode ?? "",
    tags: definition.metadata.tags.join(", "),
    supportedObservations: [...definition.interaction.supportedObservations],
    supportedBehaviourModalities: [...definition.interaction.supportedBehaviourModalities],
    profileTags: definition.interaction.profileTags.join(", "),
    strategyKind: policy?.kind ?? "prometheus.policy.no-op",
    strategyVersion: policy?.version ?? 1,
    strategyConfig: config,
    promptSections: promptSections(config),
  };
}

export function authoringFormToDefinition(
  source: AgentDefinitionV1,
  form: AuthoringForm,
): AgentDefinitionV1 {
  const definition = cloneJson(source);
  definition.key = form.key.trim();
  definition.revision = form.revision;
  definition.metadata = {
    displayName: form.displayName.trim(),
    description: form.description.trim(),
    categoryPath: form.categoryPath.trim(),
    languageCode: form.languageCode.trim() || null,
    tags: stableList(form.tags),
  };
  definition.interaction = {
    supportedObservations: unique(form.supportedObservations),
    supportedBehaviourModalities: unique(form.supportedBehaviourModalities),
    profileTags: stableList(form.profileTags),
  };

  const stateIndex = definition.states.findIndex((state) => state.id === definition.lifecycle.initialStateId);
  if (stateIndex >= 0 && definition.states[stateIndex].kind === "atomic") {
    const state = cloneJson(definition.states[stateIndex]) as AtomicStateDefinition;
    const config = cloneJson(form.strategyConfig);
    if (form.strategyKind === "prometheus.policy.prompt") {
      config.consumedObservations = [...definition.interaction.supportedObservations];
      config.emittedModalities = [...definition.interaction.supportedBehaviourModalities];
      if (form.promptSections.length > 0) {
        config.responsePrompt = { sections: cloneJson(form.promptSections) };
      } else {
        delete config.responsePrompt;
      }
    }
    state.policy = {
      kind: form.strategyKind,
      version: form.strategyVersion,
      config,
    };
    definition.states[stateIndex] = state;
  }
  return definition;
}

export function updatePromptSection(
  sections: PromptSection[],
  id: string,
  kind: string,
  content: string,
): PromptSection[] {
  const next = sections.map((section) => ({ ...section }));
  const existing = next.findIndex((section) => section.id === id);
  if (!content.trim()) {
    return existing < 0 ? next : next.filter((_, index) => index !== existing);
  }
  if (existing >= 0) {
    next[existing] = { ...next[existing], kind, content };
    return next;
  }

  const wantedOrder = PROMPT_FIELDS.findIndex((field) => field.id === id);
  const insertBefore = next.findIndex((section) => {
    const order = PROMPT_FIELDS.findIndex((field) => field.id === section.id);
    return order >= 0 && order > wantedOrder;
  });
  const section = { id, kind, content };
  if (insertBefore < 0) {
    next.push(section);
  } else {
    next.splice(insertBefore, 0, section);
  }
  return next;
}

export function promptContent(form: AuthoringForm, id: string): string {
  return form.promptSections.find((section) => section.id === id)?.content ?? "";
}

export function suggestDefinitionKey(categoryPath: string, displayName: string): string {
  const category = categoryPath
    .toLowerCase()
    .split(".")
    .map(stableSegment)
    .filter(Boolean)
    .join(".") || "designer";
  const name = stableSegment(displayName) || "agent";
  return `${category}.${name}`;
}

export function localFormIssues(form: AuthoringForm, keyConfirmed: boolean): LocalFormIssue[] {
  const issues: LocalFormIssue[] = [];
  if (!form.displayName.trim()) issues.push({ fieldId: "purpose-display-name", message: "Enter a display name." });
  if (!form.description.trim()) issues.push({ fieldId: "purpose-description", message: "Describe the agent's goal." });
  if (!STABLE_ID_PATTERN.test(form.categoryPath.trim())) issues.push({ fieldId: "purpose-category", message: "Use a stable dotted category such as designer.coaching." });
  if (form.languageCode.trim() && !LANGUAGE_CODE_PATTERN.test(form.languageCode.trim())) issues.push({ fieldId: "purpose-language", message: "Use a language code such as en or de-CH." });
  if (!DEFINITION_KEY_PATTERN.test(form.key.trim())) issues.push({ fieldId: "purpose-key", message: "Use a stable dotted key such as designer.coaching_agent." });
  if (!keyConfirmed) issues.push({ fieldId: "purpose-key-confirmed", message: "Confirm the stable key before the first save." });
  return issues;
}

export function policyStrategies(components: ComponentDefinition[]): ComponentDefinition[] {
  return components.filter((component) => component.category === "POLICY");
}

export function isStrategyCompatible(component: ComponentDefinition, modalities: string[]): boolean {
  if (component.category !== "POLICY") return false;
  const properties = isJsonObject(component.configSchema.properties)
    ? component.configSchema.properties
    : {};
  if (Object.hasOwn(properties, "emittedModalities")) return true;
  if (modalities.length === 0) return true;
  return modalities.every((modality) => component.capabilities.emittedBehaviourModalities.includes(modality));
}

export function adoptStrategy(form: AuthoringForm, component: ComponentDefinition): AuthoringForm {
  const config = cloneJson(component.defaultConfig);
  if (component.kind === "prometheus.policy.prompt") {
    delete config.responsePrompt;
    config.consumedObservations = [...form.supportedObservations];
    config.emittedModalities = [...form.supportedBehaviourModalities];
  }
  return {
    ...form,
    strategyKind: component.kind,
    strategyVersion: component.version,
    strategyConfig: config,
    supportedObservations: unique([
      ...form.supportedObservations,
      ...component.capabilities.consumedObservations,
    ]),
    supportedBehaviourModalities: unique([
      ...form.supportedBehaviourModalities,
      ...component.capabilities.emittedBehaviourModalities,
    ]),
  };
}

export function serializedDefinition(definition: AgentDefinitionV1): string {
  return JSON.stringify(stableObject(definition));
}

function initialAtomicState(definition: AgentDefinitionV1): AtomicStateDefinition | undefined {
  const state = definition.states.find((candidate) => candidate.id === definition.lifecycle.initialStateId);
  return state?.kind === "atomic" ? state as AtomicStateDefinition : undefined;
}

function promptSections(config: JsonObject): PromptSection[] {
  const responsePrompt = config.responsePrompt;
  if (!isJsonObject(responsePrompt) || !Array.isArray(responsePrompt.sections)) return [];
  return responsePrompt.sections.filter(isPromptSection).map((section) => ({ ...section }));
}

function isPromptSection(value: unknown): value is PromptSection {
  return isJsonObject(value) && typeof value.id === "string" && typeof value.kind === "string"
    && typeof value.content === "string";
}

function stableList(value: string): string[] {
  return unique(value.split(",").map((item) => item.trim()).filter(Boolean));
}

function unique(values: string[]): string[] {
  return [...new Set(values)];
}

function stableSegment(value: string): string {
  return value.toLowerCase().trim().replace(/[^a-z0-9]+/g, "_").replace(/^_+|_+$/g, "");
}

function stableObject(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(stableObject);
  if (typeof value !== "object" || value === null) return value;
  return Object.fromEntries(Object.entries(value as Record<string, unknown>)
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([key, child]) => [key, stableObject(child)]));
}
