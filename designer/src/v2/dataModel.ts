import {
  cloneJson,
  isJsonObject,
  type AgentDefinitionV1,
  type ComponentEnvelope,
  type JsonObject,
  type JsonValue,
  type PromptSection,
} from "../model/agentDefinition";
import {
  nextStableId,
  projectDefinition,
  type DataItemProjection,
  type DataRole,
  type DesignerV2Projection,
} from "./projection";

export type GuidedFieldType = "string" | "integer" | "number" | "boolean" | "string-list";
export type DataValueType = GuidedFieldType | "object";

export interface GuidedField {
  key: string;
  label: string;
  type: GuidedFieldType;
  required: boolean;
  enumValues: string[];
}

export interface DataReference {
  pointer: string;
  label: string;
  owner: "state" | "rule" | "initializer" | "resource";
}

export interface TypedChoiceSetup {
  initializerIndex: number;
  resourceIndex: number | null;
  values: JsonValue[];
  source: "resource" | "inline";
}

export interface OutcomeAttachment {
  transitionIndex: number;
  actionIndex: number;
  ruleId: string;
  envelope: ComponentEnvelope;
}

export interface OutcomeConversionPreview {
  before: JsonObject;
  after: JsonObject;
  changedRuleIds: string[];
}

export function addDataItem(
  projection: DesignerV2Projection,
  role: Exclude<DataRole, "outcome-report">,
  preferredKey: string,
  type: GuidedFieldType = "string",
): DesignerV2Projection {
  const definition = cloneJson(projection.source);
  const key = nextDataKey(preferredKey || role, definition.storage.map((item) => String(item.key ?? "")));
  const schema = schemaForType(type);
  const declaration: JsonObject = {
    key,
    description: humanize(key),
    valueSchema: schema,
    required: role === "starting-context",
    visibility: "internal",
    reset: role === "starting-context" ? "initial" : "remove",
    examples: [],
  };
  if (role === "starting-context") declaration.initialValue = defaultValue(schema);
  definition.storage.push(declaration);
  return projectDefinition(definition);
}

export function updateDataDescription(
  projection: DesignerV2Projection,
  storageIndex: number,
  description: string,
): DesignerV2Projection {
  return updateDeclaration(projection, storageIndex, (declaration) => {
    if (description.trim()) declaration.description = description;
    else delete declaration.description;
  });
}

export function updateDataRequirements(
  projection: DesignerV2Projection,
  storageIndex: number,
  patch: { required?: boolean; reset?: "initial" | "preserve" | "remove" },
): DesignerV2Projection {
  return updateDeclaration(projection, storageIndex, (declaration) => Object.assign(declaration, patch));
}

export function replaceDataSchema(
  projection: DesignerV2Projection,
  storageIndex: number,
  schema: JsonObject,
): DesignerV2Projection {
  const definition = cloneJson(projection.source);
  const declaration = definition.storage[storageIndex];
  if (!declaration) return projection;
  declaration.valueSchema = cloneJson(schema);
  if (Object.hasOwn(declaration, "initialValue")) declaration.initialValue = coerceValue(declaration.initialValue, schema);
  coerceInitializerValues(definition, String(declaration.key ?? ""), schema);
  syncOutcomeExtraction(definition, String(declaration.key ?? ""), schema, false);
  return projectDefinition(definition);
}

export function setDataType(
  projection: DesignerV2Projection,
  storageIndex: number,
  type: DataValueType,
): DesignerV2Projection {
  return replaceDataSchema(projection, storageIndex, schemaForType(type));
}

export function setDataEnumValues(
  projection: DesignerV2Projection,
  storageIndex: number,
  values: string[],
): DesignerV2Projection {
  const declaration = projection.source.storage[storageIndex];
  const current = isJsonObject(declaration?.valueSchema) ? cloneJson(declaration.valueSchema) : { type: "string" };
  const unique = uniqueStrings(values);
  if (unique.length) current.enum = unique;
  else delete current.enum;
  return replaceDataSchema(projection, storageIndex, current);
}

export function dataReferences(definition: AgentDefinitionV1, storageKey: string): DataReference[] {
  const references: DataReference[] = [];
  definition.lifecycle.initializers.forEach((envelope, index) => collectEnvelopeReferences(
    envelope, storageKey, `/lifecycle/initializers/${index}`, "initializer", "Starting-value setup", references,
  ));
  definition.states.forEach((state, stateIndex) => {
    if (state.kind === "final") return;
    if (state.eventSelector) collectEnvelopeReferences(state.eventSelector, storageKey,
      `/states/${stateIndex}/eventSelector`, "state", `${state.name} event selection`, references);
    if (state.policy) collectEnvelopeReferences(state.policy, storageKey,
      `/states/${stateIndex}/policy`, "state", `${state.name} ordinary response`, references);
  });
  definition.transitions.forEach((transition, transitionIndex) => {
    transition.decisions.forEach((envelope, index) => collectEnvelopeReferences(envelope, storageKey,
      `/transitions/${transitionIndex}/decisions/${index}`, "rule", `${transition.id} condition`, references));
    transition.actions.forEach((envelope, index) => collectEnvelopeReferences(envelope, storageKey,
      `/transitions/${transitionIndex}/actions/${index}`, "rule", `${transition.id} effect`, references));
  });
  return references;
}

export function renameDataItem(
  projection: DesignerV2Projection,
  storageIndex: number,
  preferredKey: string,
): DesignerV2Projection {
  const declaration = projection.source.storage[storageIndex];
  const oldKey = typeof declaration?.key === "string" ? declaration.key : "";
  if (!oldKey || dataReferences(projection.source, oldKey).length > 0) return projection;
  const used = projection.source.storage.flatMap((item, index) => index === storageIndex ? [] : [String(item.key ?? "")]);
  const key = nextDataKey(preferredKey, used);
  return updateDeclaration(projection, storageIndex, (item) => { item.key = key; });
}

export function deleteDataItem(projection: DesignerV2Projection, storageIndex: number): DesignerV2Projection {
  const declaration = projection.source.storage[storageIndex];
  const key = typeof declaration?.key === "string" ? declaration.key : "";
  if (!key || dataReferences(projection.source, key).length > 0) return projection;
  const definition = cloneJson(projection.source);
  definition.storage.splice(storageIndex, 1);
  return projectDefinition(definition);
}

export function fixedInitialValue(definition: AgentDefinitionV1, item: DataItemProjection): JsonValue | undefined {
  if (Object.hasOwn(item.declaration, "initialValue")) return cloneJson(item.declaration.initialValue as JsonValue);
  const initializer = definition.lifecycle.initializers.find((candidate) => candidate.kind === "prometheus.initializer.constant"
    && candidate.config.storageKey === item.key);
  return initializer && Object.hasOwn(initializer.config, "value")
    ? cloneJson(initializer.config.value as JsonValue) : undefined;
}

export function setFixedInitialValue(
  projection: DesignerV2Projection,
  storageIndex: number,
  value: JsonValue,
): DesignerV2Projection {
  const definition = cloneJson(projection.source);
  const declaration = definition.storage[storageIndex];
  if (!declaration || typeof declaration.key !== "string") return projection;
  removeRandomInitialization(definition, declaration.key);
  const constant = definition.lifecycle.initializers.find((candidate) => candidate.kind === "prometheus.initializer.constant"
    && candidate.config.storageKey === declaration.key);
  if (constant) constant.config.value = cloneJson(value);
  else declaration.initialValue = cloneJson(value);
  declaration.required = true;
  declaration.reset = "initial";
  return projectDefinition(definition);
}

export function typedChoiceSetup(definition: AgentDefinitionV1, storageKey: string): TypedChoiceSetup | null {
  const initializerIndex = definition.lifecycle.initializers.findIndex((candidate) =>
    candidate.kind === "prometheus.initializer.random-choice" && candidate.config.storageKey === storageKey);
  if (initializerIndex < 0) return null;
  const initializer = definition.lifecycle.initializers[initializerIndex];
  const resourceId = initializer.config.choicesResourceId;
  if (typeof resourceId === "string") {
    const resourceIndex = definition.resources.findIndex((resource) => resource.id === resourceId);
    const resource = definition.resources[resourceIndex];
    const values = isJsonObject(resource?.config) && Array.isArray(resource.config.values)
      ? resource.config.values as JsonValue[] : [];
    return { initializerIndex, resourceIndex: resourceIndex < 0 ? null : resourceIndex, values: cloneJson(values), source: "resource" };
  }
  return {
    initializerIndex,
    resourceIndex: null,
    values: Array.isArray(initializer.config.choices) ? cloneJson(initializer.config.choices as JsonValue[]) : [],
    source: "inline",
  };
}

export function useTypedChoices(
  projection: DesignerV2Projection,
  storageIndex: number,
  values?: JsonValue[],
): DesignerV2Projection {
  const definition = cloneJson(projection.source);
  const declaration = definition.storage[storageIndex];
  if (!declaration || typeof declaration.key !== "string") return projection;
  const key = declaration.key;
  removeInitialization(definition, key);
  delete declaration.initialValue;
  declaration.required = true;
  declaration.reset = "initial";
  const resourceId = nextStableId(`${key}-choices`, definition.resources.map((resource) => String(resource.id ?? "")));
  const schema = isJsonObject(declaration.valueSchema) ? declaration.valueSchema : { type: "string" };
  const initialValues = values?.length ? cloneJson(values) : [defaultValue(schema), alternateValue(schema)];
  definition.resources.push({
    id: resourceId,
    kind: "prometheus.resource.typed-choices",
    version: 1,
    config: { values: initialValues },
  });
  definition.lifecycle.initializers.push({
    kind: "prometheus.initializer.random-choice",
    version: 1,
    config: { storageKey: key, choicesResourceId: resourceId },
  });
  return projectDefinition(definition);
}

export function replaceTypedChoices(
  projection: DesignerV2Projection,
  storageKey: string,
  values: JsonValue[],
): DesignerV2Projection {
  const definition = cloneJson(projection.source);
  const setup = typedChoiceSetup(definition, storageKey);
  if (!setup) return projection;
  if (setup.source === "resource" && setup.resourceIndex !== null) {
    const resource = definition.resources[setup.resourceIndex];
    resource.config = { ...cloneJson(resource.config as JsonObject), values: cloneJson(values) };
  } else {
    definition.lifecycle.initializers[setup.initializerIndex].config.choices = cloneJson(values);
  }
  return projectDefinition(definition);
}

export function removeDataInitialization(
  projection: DesignerV2Projection,
  storageIndex: number,
): DesignerV2Projection {
  const definition = cloneJson(projection.source);
  const declaration = definition.storage[storageIndex];
  if (!declaration || typeof declaration.key !== "string") return projection;
  delete declaration.initialValue;
  removeInitialization(definition, declaration.key);
  declaration.required = false;
  declaration.reset = "remove";
  return projectDefinition(definition);
}

export function operationOwnedData(projection: DesignerV2Projection): Array<{
  group: string;
  itemKeys: string[];
}> {
  const rpsKinds = new Set([
    "prometheus.policy.rps-reveal", "prometheus.policy.rps-result",
    "prometheus.action.rps-select-sign", "prometheus.action.rps-evaluate-round",
  ]);
  const keys = new Set<string>();
  projection.capabilities.installedComponents.filter((component) => rpsKinds.has(component.kind))
    .flatMap((component) => component.uses)
    .forEach((use) => collectNamedStrings(use.envelope.config, /storagekey$/i, keys));
  const itemKeys = projection.data.items.filter((item) => keys.has(item.key)).map((item) => item.key);
  return itemKeys.length ? [{ group: "rock-scissor-paper", itemKeys }] : [];
}

export function addGuidedOutcome(
  projection: DesignerV2Projection,
  preferredKey = "outcome",
  fields: GuidedField[] = [defaultOutcomeField()],
): DesignerV2Projection {
  const definition = cloneJson(projection.source);
  const key = nextDataKey(preferredKey, definition.storage.map((item) => String(item.key ?? "")));
  const schema = schemaFromFields(fields);
  definition.storage.push({
    key,
    description: "Caller-visible outcome report",
    valueSchema: schema,
    required: false,
    visibility: "outcome",
    reset: "remove",
    examples: [],
  });
  attachExtractionToFinishRules(definition, key, schema, fields);
  return projectDefinition(definition);
}

export function outcomeAttachments(definition: AgentDefinitionV1, storageKey: string): OutcomeAttachment[] {
  return definition.transitions.flatMap((transition, transitionIndex) => transition.actions.flatMap(
    (envelope, actionIndex): OutcomeAttachment[] => envelope.kind === "prometheus.action.extract"
      && envelope.config.targetStorageKey === storageKey
      ? [{ transitionIndex, actionIndex, ruleId: transition.id, envelope: cloneJson(envelope) }] : [],
  ));
}

export function outcomeMode(definition: AgentDefinitionV1, item: DataItemProjection): "guided" | "custom" {
  const fields = guidedFields(item.declaration.valueSchema);
  if (!fields) return "custom";
  const attachments = outcomeAttachments(definition, item.key);
  return attachments.every((attachment) => generatedExtraction(attachment.envelope)) ? "guided" : "custom";
}

export function guidedOutcomeFields(item: DataItemProjection): GuidedField[] {
  return guidedFields(item.declaration.valueSchema) ?? [];
}

export function guidedFieldsFromSchema(schema: unknown): GuidedField[] | null {
  return guidedFields(schema);
}

export function replaceStructuredDataFields(
  projection: DesignerV2Projection,
  storageIndex: number,
  fields: GuidedField[],
): DesignerV2Projection {
  return replaceDataSchema(projection, storageIndex, schemaFromFields(fields));
}

export function replaceGuidedOutcomeFields(
  projection: DesignerV2Projection,
  storageIndex: number,
  fields: GuidedField[],
): DesignerV2Projection {
  const definition = cloneJson(projection.source);
  const declaration = definition.storage[storageIndex];
  if (!declaration || typeof declaration.key !== "string" || declaration.visibility !== "outcome") return projection;
  const schema = schemaFromFields(fields.length ? fields : [defaultOutcomeField()]);
  declaration.valueSchema = schema;
  syncOutcomeExtraction(definition, declaration.key, schema, true, fields);
  return projectDefinition(definition);
}

export function outcomeConversionPreview(
  projection: DesignerV2Projection,
  storageIndex: number,
  fields: GuidedField[],
): OutcomeConversionPreview | null {
  const declaration = projection.source.storage[storageIndex];
  if (!declaration || typeof declaration.key !== "string" || declaration.visibility !== "outcome") return null;
  const schema = schemaFromFields(fields.length ? fields : [defaultOutcomeField()]);
  const sections = generatedExtractionSections(declaration.key, fields);
  return {
    before: {
      valueSchema: cloneJson(declaration.valueSchema as JsonObject),
      extractionPrompts: outcomeAttachments(projection.source, declaration.key).map((attachment) =>
        cloneJson(attachment.envelope.config.extractionPrompt)),
    },
    after: { valueSchema: schema, extractionPrompt: { sections } },
    changedRuleIds: outcomeAttachments(projection.source, declaration.key).map((attachment) => attachment.ruleId),
  };
}

export function convertOutcomeToGuided(
  projection: DesignerV2Projection,
  storageIndex: number,
  fields: GuidedField[],
): DesignerV2Projection {
  const definition = cloneJson(projection.source);
  const declaration = definition.storage[storageIndex];
  if (!declaration || typeof declaration.key !== "string") return projection;
  const normalized = fields.length ? fields : [defaultOutcomeField()];
  const schema = schemaFromFields(normalized);
  declaration.valueSchema = schema;
  syncOutcomeExtraction(definition, declaration.key, schema, true, normalized);
  return projectDefinition(definition);
}

export function updateCustomExtractionSection(
  projection: DesignerV2Projection,
  transitionIndex: number,
  actionIndex: number,
  sectionIndex: number,
  content: string,
): DesignerV2Projection {
  const definition = cloneJson(projection.source);
  const action = definition.transitions[transitionIndex]?.actions[actionIndex];
  if (!action) return projection;
  const prompt = action.config.extractionPrompt;
  if (!isJsonObject(prompt) || !Array.isArray(prompt.sections)) return projection;
  const section = prompt.sections[sectionIndex];
  if (isJsonObject(section) && typeof section.id === "string" && typeof section.kind === "string") {
    section.content = content;
  }
  return projectDefinition(definition);
}

export function detachOutcomeExtraction(
  projection: DesignerV2Projection,
  storageKey: string,
): DesignerV2Projection {
  const definition = cloneJson(projection.source);
  definition.transitions.forEach((transition) => {
    transition.actions = transition.actions.filter((action) => !(action.kind === "prometheus.action.extract"
      && action.config.targetStorageKey === storageKey));
  });
  return projectDefinition(definition);
}

export function attachOutcomeExtraction(
  projection: DesignerV2Projection,
  storageIndex: number,
): DesignerV2Projection {
  const definition = cloneJson(projection.source);
  const declaration = definition.storage[storageIndex];
  if (!declaration || typeof declaration.key !== "string") return projection;
  const schema = isJsonObject(declaration.valueSchema) ? declaration.valueSchema : { type: "object" };
  const fields = guidedFields(schema) ?? [defaultOutcomeField()];
  attachExtractionToFinishRules(definition, declaration.key, schema, fields);
  return projectDefinition(definition);
}

export function defaultOutcomeField(index = 0): GuidedField {
  return {
    key: index === 0 ? "resultSummary" : `field${index + 1}`,
    label: index === 0 ? "Result summary" : `Field ${index + 1}`,
    type: "string",
    required: index === 0,
    enumValues: [],
  };
}

export function defaultDataField(index = 0): GuidedField {
  return {
    key: index === 0 ? "value" : `field${index + 1}`,
    label: index === 0 ? "Value" : `Field ${index + 1}`,
    type: "string",
    required: false,
    enumValues: [],
  };
}

export function schemaFromFields(fields: GuidedField[]): JsonObject {
  const properties: JsonObject = {};
  const required: string[] = [];
  fields.forEach((field, index) => {
    const key = nextDataKey(field.key || `field-${index + 1}`, Object.keys(properties));
    const schema = schemaForType(field.type);
    if (field.label.trim()) schema.title = field.label.trim();
    if (field.type === "string" && field.enumValues.length) schema.enum = uniqueStrings(field.enumValues);
    properties[key] = schema;
    if (field.required) required.push(key);
  });
  return { type: "object", properties, required, additionalProperties: false };
}

export function schemaType(schema: unknown): DataValueType | null {
  if (!isJsonObject(schema)) return null;
  if (schema.type === "array" && isJsonObject(schema.items) && schema.items.type === "string") return "string-list";
  if (schema.type === "object") return "object";
  return schema.type === "string" || schema.type === "integer" || schema.type === "number" || schema.type === "boolean"
    ? schema.type : null;
}

export function parseEditorValue(value: string, schema: unknown): JsonValue {
  const type = isJsonObject(schema) ? schema.type : "string";
  if (type === "integer") return Number.parseInt(value || "0", 10);
  if (type === "number") return Number.parseFloat(value || "0");
  if (type === "boolean") return value === "true";
  if (type === "array") return value.split(",").map((item) => item.trim()).filter(Boolean);
  return value;
}

export function formatEditorValue(value: unknown, schema: unknown): string {
  const type = isJsonObject(schema) ? schema.type : "string";
  if (type === "array" && Array.isArray(value)) return value.map(String).join(", ");
  if (type === "object") return JSON.stringify(value ?? {}, null, 2);
  return value === undefined || value === null ? "" : String(value);
}

function updateDeclaration(
  projection: DesignerV2Projection,
  storageIndex: number,
  updater: (declaration: JsonObject) => void,
): DesignerV2Projection {
  const definition = cloneJson(projection.source);
  const declaration = definition.storage[storageIndex];
  if (!declaration) return projection;
  updater(declaration);
  return projectDefinition(definition);
}

function collectEnvelopeReferences(
  envelope: ComponentEnvelope,
  expected: string,
  pointer: string,
  owner: DataReference["owner"],
  label: string,
  result: DataReference[],
) {
  collectReferences(envelope.config, expected, `${pointer}/config`, owner, label, result);
}

function collectReferences(
  value: unknown,
  expected: string,
  pointer: string,
  owner: DataReference["owner"],
  label: string,
  result: DataReference[],
  propertyName = "",
) {
  if (typeof value === "string" && value === expected && (/storagekey$/i.test(propertyName) || propertyName === "key")) {
    result.push({ pointer, owner, label });
    return;
  }
  if (Array.isArray(value)) {
    value.forEach((item, index) => collectReferences(item, expected, `${pointer}/${index}`, owner, label, result, propertyName));
    return;
  }
  if (!isJsonObject(value)) return;
  Object.entries(value).forEach(([key, child]) => collectReferences(
    child, expected, `${pointer}/${escapePointer(key)}`, owner, label, result, key,
  ));
}

function removeRandomInitialization(definition: AgentDefinitionV1, storageKey: string) {
  const random = definition.lifecycle.initializers.filter((candidate) => candidate.kind === "prometheus.initializer.random-choice"
    && candidate.config.storageKey === storageKey);
  const resourceIds = new Set(random.flatMap((candidate) => typeof candidate.config.choicesResourceId === "string"
    ? [candidate.config.choicesResourceId] : []));
  definition.lifecycle.initializers = definition.lifecycle.initializers.filter((candidate) => !random.includes(candidate));
  definition.resources = definition.resources.filter((resource) => !resourceIds.has(String(resource.id ?? ""))
    || resourceReferenced(definition, String(resource.id ?? "")));
}

function removeInitialization(definition: AgentDefinitionV1, storageKey: string) {
  const owned = definition.lifecycle.initializers.filter((candidate) => candidate.config.storageKey === storageKey);
  const resourceIds = new Set(owned.flatMap((candidate) => typeof candidate.config.choicesResourceId === "string"
    ? [candidate.config.choicesResourceId] : []));
  definition.lifecycle.initializers = definition.lifecycle.initializers.filter((candidate) => !owned.includes(candidate));
  definition.resources = definition.resources.filter((resource) => !resourceIds.has(String(resource.id ?? ""))
    || resourceReferenced(definition, String(resource.id ?? "")));
}

function resourceReferenced(definition: AgentDefinitionV1, resourceId: string): boolean {
  return definition.lifecycle.initializers.some((initializer) => initializer.config.choicesResourceId === resourceId);
}

function schemaForType(type: DataValueType): JsonObject {
  if (type === "string-list") return { type: "array", items: { type: "string" } };
  if (type === "object") return { type: "object", properties: {}, required: [], additionalProperties: false };
  return { type };
}

function guidedFields(schema: unknown): GuidedField[] | null {
  if (!isJsonObject(schema) || schema.type !== "object" || schema.additionalProperties !== false
    || !isJsonObject(schema.properties)) return null;
  const required = new Set(Array.isArray(schema.required) ? schema.required.filter((item): item is string => typeof item === "string") : []);
  const fields: GuidedField[] = [];
  for (const [key, value] of Object.entries(schema.properties)) {
    const type = schemaType(value);
    if (!type || type === "object" || !isJsonObject(value)) return null;
    const enumValues = Array.isArray(value.enum) && value.enum.every((item) => typeof item === "string")
      ? value.enum as string[] : [];
    fields.push({
      key,
      label: typeof value.title === "string" ? value.title : humanize(key),
      type,
      required: required.has(key),
      enumValues: [...enumValues],
    });
  }
  return fields;
}

function generatedExtraction(envelope: ComponentEnvelope): boolean {
  const prompt = envelope.config.extractionPrompt;
  if (!isJsonObject(prompt) || !Array.isArray(prompt.sections)) return false;
  return prompt.sections.length === 3 && prompt.sections.every((section, index) => isJsonObject(section)
    && section.kind === ["outcome-instruction", "outcome-structure", "outcome-rules"][index]);
}

function syncOutcomeExtraction(
  definition: AgentDefinitionV1,
  storageKey: string,
  schema: JsonObject,
  regeneratePrompt: boolean,
  fields: GuidedField[] = guidedFields(schema) ?? [],
) {
  definition.transitions.forEach((transition) => transition.actions.forEach((action) => {
    if (action.kind !== "prometheus.action.extract" || action.config.targetStorageKey !== storageKey) return;
    action.config.outputSchema = cloneJson(schema);
    if (regeneratePrompt) action.config.extractionPrompt = { sections: generatedExtractionSections(storageKey, fields) };
  }));
}

function attachExtractionToFinishRules(
  definition: AgentDefinitionV1,
  storageKey: string,
  schema: JsonObject,
  fields: GuidedField[],
) {
  const finals = new Set(definition.states.filter((state) => state.kind === "final").map((state) => state.id));
  definition.transitions.filter((transition) => finals.has(transition.targetStateId)).forEach((transition) => {
    if (transition.actions.some((action) => action.kind === "prometheus.action.extract"
      && action.config.targetStorageKey === storageKey)) return;
    transition.actions.push({
      kind: "prometheus.action.extract",
      version: 1,
      config: {
        targetStorageKey: storageKey,
        extractionPrompt: { sections: generatedExtractionSections(storageKey, fields) },
        outputSchema: cloneJson(schema),
      },
    });
  });
}

function generatedExtractionSections(storageKey: string, fields: GuidedField[]): PromptSection[] {
  const example = Object.fromEntries(fields.map((field) => [field.key, exampleValue(field)]));
  return [{
    id: `${storageKey}.instruction`,
    kind: "outcome-instruction",
    content: "Extract the caller-visible outcome from the completed interaction. Return valid JSON only.",
  }, {
    id: `${storageKey}.structure`,
    kind: "outcome-structure",
    content: `Use exactly this report shape:\n${JSON.stringify(example, null, 2)}`,
  }, {
    id: `${storageKey}.rules`,
    kind: "outcome-rules",
    content: `Include only information supported by the interaction. Required fields: ${fields.filter((field) => field.required)
      .map((field) => field.key).join(", ") || "none"}. The output must match the registered schema.`,
  }];
}

function coerceInitializerValues(definition: AgentDefinitionV1, storageKey: string, schema: JsonObject) {
  definition.lifecycle.initializers.filter((initializer) => initializer.config.storageKey === storageKey).forEach((initializer) => {
    if (initializer.kind === "prometheus.initializer.constant" && Object.hasOwn(initializer.config, "value")) {
      initializer.config.value = coerceValue(initializer.config.value, schema);
    }
    if (initializer.kind !== "prometheus.initializer.random-choice") return;
    if (Array.isArray(initializer.config.choices)) {
      initializer.config.choices = initializer.config.choices.map((value) => coerceValue(value, schema));
    }
    const resourceId = initializer.config.choicesResourceId;
    const resource = typeof resourceId === "string"
      ? definition.resources.find((candidate) => candidate.id === resourceId) : undefined;
    if (isJsonObject(resource?.config) && Array.isArray(resource.config.values)) {
      resource.config.values = resource.config.values.map((value) => coerceValue(value, schema));
    }
  });
}

function exampleValue(field: GuidedField): JsonValue {
  if (field.enumValues.length) return field.enumValues[0];
  if (field.type === "boolean") return false;
  if (field.type === "integer" || field.type === "number") return 0;
  if (field.type === "string-list") return [];
  return "string";
}

function defaultValue(schema: JsonObject): JsonValue {
  if (Array.isArray(schema.enum) && schema.enum.length) return cloneJson(schema.enum[0] as JsonValue);
  if (schema.type === "boolean") return false;
  if (schema.type === "integer" || schema.type === "number") return 0;
  if (schema.type === "array") return [];
  if (schema.type === "object") {
    const properties = isJsonObject(schema.properties) ? schema.properties : {};
    return Object.fromEntries(Object.entries(properties).map(([key, value]) =>
      [key, isJsonObject(value) ? defaultValue(value) : null])) as JsonValue;
  }
  return "";
}

function alternateValue(schema: JsonObject): JsonValue {
  if (Array.isArray(schema.enum) && schema.enum.length > 1) return cloneJson(schema.enum[1] as JsonValue);
  if (schema.type === "boolean") return true;
  if (schema.type === "integer" || schema.type === "number") return 1;
  if (schema.type === "array") return ["example"];
  if (schema.type === "object") return defaultValue(schema);
  return "Example";
}

function coerceValue(value: unknown, schema: JsonObject): JsonValue {
  if (schema.type === "string") return String(value ?? "");
  if (schema.type === "integer") return Number.isFinite(Number(value)) ? Math.trunc(Number(value)) : 0;
  if (schema.type === "number") return Number.isFinite(Number(value)) ? Number(value) : 0;
  if (schema.type === "boolean") return Boolean(value);
  if (schema.type === "array") {
    if (!Array.isArray(value)) return [];
    const itemSchema = isJsonObject(schema.items) ? schema.items : null;
    return itemSchema ? value.map((item) => coerceValue(item, itemSchema)) : cloneJson(value as JsonValue[]);
  }
  if (schema.type === "object") {
    if (!isJsonObject(value)) return defaultValue(schema);
    const properties = isJsonObject(schema.properties) ? schema.properties : {};
    const coerced = Object.fromEntries(Object.entries(properties).map(([key, child]) => [
      key,
      isJsonObject(child) ? coerceValue(value[key], child) : cloneJson((value[key] ?? null) as JsonValue),
    ]));
    return schema.additionalProperties === false
      ? coerced as JsonValue : { ...cloneJson(value), ...coerced } as JsonValue;
  }
  return null;
}

function collectNamedStrings(value: unknown, propertyPattern: RegExp, result: Set<string>, propertyName = "") {
  if (typeof value === "string" && propertyPattern.test(propertyName)) {
    result.add(value);
    return;
  }
  if (Array.isArray(value)) return value.forEach((item) => collectNamedStrings(item, propertyPattern, result, propertyName));
  if (isJsonObject(value)) Object.entries(value).forEach(([key, child]) => collectNamedStrings(child, propertyPattern, result, key));
}

function uniqueStrings(values: string[]): string[] {
  return [...new Set(values.map((value) => value.trim()).filter(Boolean))];
}

function nextDataKey(preferred: string, usedKeys: Iterable<string>): string {
  const used = new Set(usedKeys);
  const normalized = preferred.trim()
    .replace(/[^A-Za-z0-9._-]+/g, "-")
    .replace(/^[^A-Za-z]+/, "")
    .replace(/[._-]+$/, "") || "value";
  if (!used.has(normalized)) return normalized;
  let suffix = 2;
  while (used.has(`${normalized}-${suffix}`)) suffix += 1;
  return `${normalized}-${suffix}`;
}

function humanize(value: string): string {
  return value.replace(/([a-z])([A-Z])/g, "$1 $2").split(/[._-]+/).filter(Boolean)
    .map((word) => `${word[0]?.toUpperCase() ?? ""}${word.slice(1)}`).join(" ");
}

function escapePointer(value: string): string {
  return value.replaceAll("~", "~0").replaceAll("/", "~1");
}
