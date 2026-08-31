import type { DefinitionDiagnostic } from "../api/designerApi";
import {
  type AgentDefinitionV1,
  type ComponentEnvelope,
  isJsonObject,
  type JsonObject,
} from "../model/agentDefinition";
import { DESIGNER_STEPS, type DesignerStepId } from "../stepper/DesignerStepper";
import {
  capabilityOption,
  EXPRESSION_CAPABILITIES,
  OBSERVATION_CAPABILITIES,
} from "../v2/authoringCatalog";
import { diagnosticStep } from "../v2/diagnostics";
import {
  projectDefinition,
  type CapabilityProjection,
  type ContinuationKind,
  type DataRole,
} from "../v2/projection";

export interface DiagnosticGroup {
  stepId: DesignerStepId;
  title: string;
  diagnostics: DefinitionDiagnostic[];
}

export interface JsonParseFailure {
  message: string;
  line: number | null;
  column: number | null;
}

export type JsonParseResult =
  | { ok: true; definition: AgentDefinitionV1 }
  | { ok: false; failure: JsonParseFailure };

export interface ReviewNarrativeSection {
  id: "brief" | "capabilities" | "interaction" | "data-outcome" | "try";
  title: string;
  summary: string;
  statements: string[];
}

export interface AdvancedStateAudit {
  id: string;
  name: string;
  kind: "atomic" | "composite" | "final";
  parentStateIds: string[];
  initialFor: string[];
  childStateIds: string[];
  initialChildStateId: string | null;
  entryMode: "start" | "reprocess-event" | null;
  oblivious: boolean | null;
  eventSelector: ComponentEnvelope | null;
  policy: ComponentEnvelope | null;
}

export interface AdvancedRuleAudit {
  id: string;
  sourceStateId: string;
  targetStateId: string;
  order: number;
  eventTypes: string[];
  continuation: ContinuationKind;
  decisions: ComponentEnvelope[];
  actions: ComponentEnvelope[];
  pointer: string;
}

export interface AdvancedDefinitionAudit {
  lifecycle: AgentDefinitionV1["lifecycle"];
  states: AdvancedStateAudit[];
  rules: AdvancedRuleAudit[];
  components: CapabilityProjection["installedComponents"];
  storage: Array<{ pointer: string; declaration: JsonObject }>;
  resources: Array<{ pointer: string; declaration: JsonObject }>;
}

export function parseDefinitionJson(source: string): JsonParseResult {
  try {
    const value = JSON.parse(source) as unknown;
    if (typeof value !== "object" || value === null || Array.isArray(value)) {
      return { ok: false, failure: { message: "The canonical definition must be a JSON object.", line: null, column: null } };
    }
    return { ok: true, definition: value as AgentDefinitionV1 };
  } catch (error) {
    const message = error instanceof Error ? error.message : "The JSON could not be parsed.";
    const offset = parseOffset(message);
    const location = offset === null ? { line: null, column: null } : lineColumn(source, offset);
    return { ok: false, failure: { message, ...location } };
  }
}

export function prettyDefinition(definition: AgentDefinitionV1): string {
  return `${JSON.stringify(definition, null, 2)}\n`;
}

export function groupDiagnostics(diagnostics: DefinitionDiagnostic[]): DiagnosticGroup[] {
  const severityRank = { ERROR: 0, WARNING: 1 } as const;
  const sorted = [...diagnostics].sort((left, right) => severityRank[left.severity] - severityRank[right.severity]
    || left.pointer.localeCompare(right.pointer) || left.code.localeCompare(right.code));
  return DESIGNER_STEPS.map((step) => ({
    stepId: step.id,
    title: step.title,
    diagnostics: sorted.filter((diagnostic) => diagnosticStep(diagnostic) === step.id),
  })).filter((group) => group.diagnostics.length > 0);
}

export function reverseExplanation(definition: AgentDefinitionV1): ReviewNarrativeSection[] {
  const projection = projectDefinition(definition);
  const agentGuidance = projection.guidance.filter((guidance) => guidance.scope === "agent");
  const observations = projection.capabilities.observations.map(observationLabel);
  const expressions = projection.capabilities.behaviourModalities.map(expressionLabel);
  const ordinaryStrategies = new Set(projection.situations
    .map((situation) => situation.ordinaryPolicy?.envelope.kind)
    .filter((kind): kind is string => Boolean(kind))
    .map(ordinaryBehaviourLabel));
  const dataByRole = new Map<DataRole, string[]>();
  projection.data.items.forEach((item) => {
    const current = dataByRole.get(item.role) ?? [];
    current.push(dataItemLabel(item.declaration, item.key));
    dataByRole.set(item.role, current);
  });

  return [
    {
      id: "brief",
      title: "Brief",
      summary: definition.metadata.description || "No purpose description has been written yet.",
      statements: [
        agentGuidance.length === 0
          ? "No agent-wide guidance has been added."
          : `${countLabel(agentGuidance.length, "ordered guidance item")} ${agentGuidance.length === 1 ? "applies" : "apply"} across every situation.`,
        definition.metadata.languageCode
          ? `The intended language is ${definition.metadata.languageCode}.`
          : "No intended language has been selected.",
      ],
    },
    {
      id: "capabilities",
      title: "Capabilities",
      summary: observations.length === 0
        ? "The agent does not yet declare what it can notice."
        : `The agent can notice ${joinWords(observations)}.`,
      statements: [
        expressions.length === 0
          ? "It does not yet declare how it can express a response."
          : `It can ${joinWords(expressions.map(lowerFirst))}.`,
        ordinaryStrategies.size === 0
          ? "No ordinary response strategy is configured."
          : `Its ordinary response strategies are ${joinWords([...ordinaryStrategies].map(lowerFirst))}.`,
      ],
    },
    {
      id: "interaction",
      title: "Interaction",
      summary: interactionSummary(projection.situations.length, projection.rules.length),
      statements: [
        ...projection.situations.map((situation) => {
          const start = situation.main ? " is the Main starting situation" : " is a durable situation";
          const behaviour = situation.ordinaryPolicy
            ? ordinaryBehaviourLabel(situation.ordinaryPolicy.envelope.kind)
            : "No ordinary response";
          const guidance = situation.guidance.length === 0
            ? "no local guidance"
            : countLabel(situation.guidance.length, "local guidance item");
          return `${situation.name}${start}; it uses ${lowerFirst(behaviour)} with ${guidance}.`;
        }),
        ...projection.rules.map((rule) => ruleExplanation(rule, definition)),
      ],
    },
    {
      id: "data-outcome",
      title: "Data & outcome",
      summary: projection.data.items.length === 0
        ? "The agent does not keep authored data."
        : `The agent defines ${countLabel(projection.data.items.length, "data item")}.`,
      statements: [
        ...dataRoleStatements(dataByRole),
        projection.outcomes.items.length === 0
          ? "No outcome report is declared."
          : `Outcome reports contain ${joinWords(projection.outcomes.items.map((item) => dataItemLabel(item.declaration, item.key)))}.`,
      ],
    },
    {
      id: "try",
      title: "Try",
      summary: projection.verification.scenarios.length === 0
        ? "No Given / When / Expect scenario has been saved."
        : `${countLabel(projection.verification.scenarios.length, "Given / When / Expect scenario")} is saved.`,
      statements: projection.verification.scenarios.map((scenario, index) => scenarioExplanation(scenario, index)),
    },
  ];
}

export function advancedDefinitionAudit(definition: AgentDefinitionV1): AdvancedDefinitionAudit {
  const projection = projectDefinition(definition);
  const directParents = new Map<string, string>();
  definition.states.forEach((state) => {
    if (state.kind === "composite") state.childStateIds.forEach((childId) => directParents.set(childId, state.id));
  });

  return {
    lifecycle: structuredClone(definition.lifecycle),
    states: definition.states.map((state) => {
      const parentStateIds: string[] = [];
      const visited = new Set<string>();
      let parent = directParents.get(state.id);
      while (parent && !visited.has(parent)) {
        visited.add(parent);
        parentStateIds.unshift(parent);
        parent = directParents.get(parent);
      }
      const initialFor = definition.states.flatMap((candidate) => candidate.kind === "composite"
        && candidate.initialChildStateId === state.id ? [candidate.id] : []);
      if (definition.lifecycle.initialStateId === state.id) initialFor.unshift("lifecycle");
      return {
        id: state.id,
        name: state.name,
        kind: state.kind,
        parentStateIds,
        initialFor,
        childStateIds: state.kind === "composite" ? [...state.childStateIds] : [],
        initialChildStateId: state.kind === "composite" ? state.initialChildStateId : null,
        entryMode: state.kind === "final" ? null : state.entryMode,
        oblivious: state.kind === "final" ? null : state.oblivious,
        eventSelector: state.kind === "final" ? null : structuredClone(state.eventSelector),
        policy: state.kind === "final" ? null : structuredClone(state.policy),
      };
    }),
    rules: definition.transitions.map((transition, index) => ({
      id: transition.id,
      sourceStateId: transition.sourceStateId,
      targetStateId: transition.targetStateId,
      order: transition.order,
      eventTypes: projection.rules[index]?.eventTypes ?? [],
      continuation: projection.rules[index]?.continuation ?? "move",
      decisions: structuredClone(transition.decisions),
      actions: structuredClone(transition.actions),
      pointer: `/transitions/${index}`,
    })),
    components: projection.capabilities.installedComponents,
    storage: definition.storage.map((declaration, index) => ({
      pointer: `/storage/${index}`,
      declaration: structuredClone(declaration),
    })),
    resources: definition.resources.map((declaration, index) => ({
      pointer: `/resources/${index}`,
      declaration: structuredClone(declaration),
    })),
  };
}

export { diagnosticStep };

function countLabel(count: number, singular: string): string {
  return `${count} ${count === 1 ? singular : `${singular}s`}`;
}

function observationLabel(id: string): string {
  return capabilityOption(id, OBSERVATION_CAPABILITIES)?.label ?? "an additional registered observation";
}

function expressionLabel(id: string): string {
  return capabilityOption(id, EXPRESSION_CAPABILITIES)?.label ?? "use an additional registered expression";
}

function ordinaryBehaviourLabel(kind: string): string {
  const labels: Record<string, string> = {
    "prometheus.policy.prompt": "Guided responses",
    "prometheus.policy.exact-text": "Exact text responses",
    "prometheus.policy.rps-reveal": "Rock, scissor, paper reveal responses",
    "prometheus.policy.rps-result": "Rock, scissor, paper result responses",
    "prometheus.policy.no-op": "No automatic response",
  };
  return labels[kind] ?? "A registered Advanced response strategy";
}

function interactionSummary(situationCount: number, ruleCount: number): string {
  if (situationCount === 0) return "No editable situations are present.";
  return `${countLabel(situationCount, "situation")} and ${countLabel(ruleCount, "ordered interaction rule")} shape the interaction.`;
}

function ruleExplanation(
  rule: ReturnType<typeof projectDefinition>["rules"][number],
  definition: AgentDefinitionV1,
): string {
  const source = definition.states.find((state) => state.id === rule.sourceStateId)?.name ?? rule.sourceStateId;
  const target = definition.states.find((state) => state.id === rule.targetStateId)?.name ?? rule.targetStateId;
  const conditionCount = rule.conditions.filter((condition) => condition.envelope.kind !== "prometheus.decision.latest-event-type").length;
  const event = rule.eventTypes.length > 0
    ? `${joinWords(rule.eventTypes.map(observationLabel))} occurs`
    : conditionCount > 0 ? "a registered trigger occurs" : "any acknowledged event occurs";
  const conditions = conditionCount === 0 ? ""
    : ` and ${countLabel(conditionCount, "condition")} ${conditionCount === 1 ? "agrees" : "agree"}`;
  const effects = rule.effects.length === 0 ? "no additional effect runs" : `${countLabel(rule.effects.length, "ordered effect")} runs`;
  const continuation = rule.continuation === "stay" ? `the agent stays in ${source}`
    : rule.continuation === "finish" ? "the interaction finishes"
      : `the agent moves from ${source} to ${target}`;
  const scope = rule.scope === "global" ? "Across all situations, " : `In ${source}, `;
  return `${scope}when ${event}${conditions}, ${effects}, then ${continuation}.`;
}

function dataRoleStatements(items: Map<DataRole, string[]>): string[] {
  const labels: Array<[DataRole, string]> = [
    ["starting-context", "Starting context"],
    ["working-data", "Working data"],
    ["learned-information", "Learned information"],
  ];
  return labels.flatMap(([role, label]) => {
    const names = items.get(role) ?? [];
    return names.length === 0 ? [] : [`${label}: ${joinWords(names)}.`];
  });
}

function dataItemLabel(declaration: JsonObject, fallback: string): string {
  for (const key of ["title", "label", "name"]) {
    if (typeof declaration[key] === "string" && declaration[key].trim()) return declaration[key].trim();
  }
  return fallback;
}

function scenarioExplanation(scenario: JsonObject, index: number): string {
  const name = typeof scenario.name === "string" && scenario.name.trim() ? scenario.name.trim() : `Scenario ${index + 1}`;
  const eventCount = Array.isArray(scenario.events) ? scenario.events.length : 0;
  const expected = isJsonObject(scenario.expected) ? Object.keys(scenario.expected).length : 0;
  return `${name} sends ${countLabel(eventCount, "event")} and checks ${countLabel(expected, "expectation group")}.`;
}

function joinWords(values: string[]): string {
  if (values.length === 0) return "nothing yet";
  if (values.length === 1) return values[0];
  if (values.length === 2) return `${values[0]} and ${values[1]}`;
  return `${values.slice(0, -1).join(", ")}, and ${values.at(-1)}`;
}

function lowerFirst(value: string): string {
  return value ? `${value[0].toLowerCase()}${value.slice(1)}` : value;
}

function parseOffset(message: string): number | null {
  const match = message.match(/position\s+(\d+)/i);
  return match ? Number(match[1]) : null;
}

function lineColumn(source: string, offset: number): { line: number; column: number } {
  const prefix = source.slice(0, offset);
  const lines = prefix.split("\n");
  return { line: lines.length, column: (lines.at(-1)?.length ?? 0) + 1 };
}
