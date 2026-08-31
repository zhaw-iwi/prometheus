import type { PromptSection } from "../model/agentDefinition";
import { GUIDANCE_EXAMPLES, GUIDANCE_INTENTS, guidanceIntent } from "./authoringCatalog";
import { nextStableId, type DesignerV2Projection } from "./projection";
import { replaceScopedGuidance } from "./transforms";

export const DEFINITION_KEY_PATTERN = /^[a-z][a-z0-9_-]*(?:\.[a-z][a-z0-9_-]*)+$/;
export const STABLE_ID_PATTERN = /^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$/;
export const LANGUAGE_CODE_PATTERN = /^[a-z]{2,3}(?:-[A-Za-z0-9]{2,8})*$/;

export interface GuidanceTarget {
  stateId: string;
  promptField: string;
}

export function suggestDefinitionKey(displayName: string): string {
  const slug = displayName.toLowerCase().trim().replace(/[^a-z0-9]+/g, "_").replace(/^_+|_+$/g, "");
  return `designer.${slug || "agent"}`;
}

export function parseStableIds(value: string): string[] {
  return [...new Set(value.split(",").map((part) => part.trim()).filter(Boolean))];
}

export function briefIssues(projection: DesignerV2Projection, keyConfirmed: boolean): Record<string, string> {
  const { key, metadata } = projection.identity;
  const issues: Record<string, string> = {};
  if (!DEFINITION_KEY_PATTERN.test(key)) issues.key = "Use at least two lower-case key segments, for example designer.welcome_guide.";
  else if (!keyConfirmed) issues.key = "Confirm the stable key before the first save.";
  if (!metadata.displayName.trim()) issues.displayName = "Give the agent a clear name.";
  if (!metadata.description.trim()) issues.description = "Describe why this agent exists and whom it helps.";
  if (!STABLE_ID_PATTERN.test(metadata.categoryPath)) issues.categoryPath = "Use a stable lower-case category such as designer or healthcare.guide.";
  if (metadata.languageCode !== null && !LANGUAGE_CODE_PATTERN.test(metadata.languageCode)) {
    issues.languageCode = "Use a language code such as en, de, or de-CH, or leave it unspecified.";
  }
  const invalidTag = metadata.tags.find((tag) => !STABLE_ID_PATTERN.test(tag));
  if (invalidTag) issues.tags = `“${invalidTag}” is not a stable lower-case tag.`;
  return issues;
}

export function defaultAgentGuidanceTarget(projection: DesignerV2Projection, promptField = "responsePrompt"): GuidanceTarget | null {
  const existing = projection.guidance.find((item) => item.scope === "agent" && item.promptField === promptField);
  if (existing) return { stateId: existing.stateId, promptField };
  const agentState = projection.source.states.find((state) => state.kind === "composite"
    && state.policy?.kind === "prometheus.policy.prompt");
  return agentState ? { stateId: agentState.id, promptField } : null;
}

export function addGuidanceIntent(
  projection: DesignerV2Projection,
  kind: string,
  content?: string,
): DesignerV2Projection {
  const intent = guidanceIntent(kind);
  const promptField = intent?.promptField ?? "responsePrompt";
  const target = defaultAgentGuidanceTarget(projection, promptField);
  if (!target) throw new Error("This definition has no agent-wide guided prompt scope.");
  const current = sectionsAt(projection, target);
  const id = nextStableId(kind, projection.guidance.map((item) => item.id));
  return replaceScopedGuidance(projection, target, [...current, {
    id,
    kind,
    content: content ?? intent?.example ?? "",
  }]);
}

export function updateGuidanceSection(
  projection: DesignerV2Projection,
  target: GuidanceTarget,
  sectionIndex: number,
  patch: Partial<PromptSection>,
): DesignerV2Projection {
  const sections = sectionsAt(projection, target);
  if (!sections[sectionIndex]) return projection;
  sections[sectionIndex] = { ...sections[sectionIndex], ...patch };
  return replaceScopedGuidance(projection, target, sections);
}

export function moveGuidanceSection(
  projection: DesignerV2Projection,
  target: GuidanceTarget,
  sectionIndex: number,
  direction: -1 | 1,
): DesignerV2Projection {
  const sections = sectionsAt(projection, target);
  const destination = sectionIndex + direction;
  if (!sections[sectionIndex] || destination < 0 || destination >= sections.length) return projection;
  [sections[sectionIndex], sections[destination]] = [sections[destination], sections[sectionIndex]];
  return replaceScopedGuidance(projection, target, sections);
}

export function removeGuidanceSection(
  projection: DesignerV2Projection,
  target: GuidanceTarget,
  sectionIndex: number,
): DesignerV2Projection {
  return replaceScopedGuidance(projection, target,
    sectionsAt(projection, target).filter((_, index) => index !== sectionIndex));
}

export function adoptGuidanceExample(projection: DesignerV2Projection, exampleId: string): DesignerV2Projection {
  const example = GUIDANCE_EXAMPLES.find((candidate) => candidate.id === exampleId);
  if (!example) return projection;
  return example.sections.reduce((current, section) => addGuidanceIntent(current, section.kind, section.content), projection);
}

export function intentOptionsForField(promptField: string) {
  return GUIDANCE_INTENTS.filter((intent) => intent.promptField === promptField);
}

function sectionsAt(projection: DesignerV2Projection, target: GuidanceTarget): PromptSection[] {
  return projection.guidance
    .filter((item) => item.scope === "agent" && item.stateId === target.stateId && item.promptField === target.promptField)
    .sort((left, right) => left.sectionIndex - right.sectionIndex)
    .map(({ id, kind, content }) => ({ id, kind, content }));
}
