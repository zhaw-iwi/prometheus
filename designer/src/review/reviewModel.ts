import type { DefinitionDiagnostic } from "../api/designerApi";
import type { AgentDefinitionV1 } from "../model/agentDefinition";
import { DESIGNER_STEPS, type DesignerStepId } from "../stepper/DesignerStepper";
import { diagnosticStep } from "../v2/diagnostics";
import { projectDefinition } from "../v2/projection";

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

export function plainSummary(definition: AgentDefinitionV1): Array<{ label: string; value: string }> {
  const projection = projectDefinition(definition);
  return [
    { label: "Brief", value: definition.metadata.description || "No purpose description yet." },
    { label: "Capabilities", value: `${countLabel(projection.capabilities.observations.length, "input")} · ${countLabel(projection.capabilities.behaviourModalities.length, "output")}` },
    { label: "Interaction", value: `${countLabel(projection.situations.length, "situation")} · ${countLabel(projection.rules.length, "rule")}` },
    { label: "Data & outcome", value: `${countLabel(projection.data.items.length, "data item")} · ${countLabel(projection.outcomes.items.length, "outcome")}` },
    { label: "Try", value: countLabel(projection.verification.scenarios.length, "scenario") },
  ];
}

export { diagnosticStep };

function countLabel(count: number, singular: string): string {
  return `${count} ${count === 1 ? singular : `${singular}s`}`;
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
