import type { AgentMetadata } from "../model/agentDefinition";
import { cloneJson, isJsonObject, type JsonObject, type PromptSection } from "../model/agentDefinition";
import {
  type DesignerV2Projection,
  projectDefinition,
} from "./projection";
import type { ComponentEnvelope } from "../model/agentDefinition";

export function updateIdentity(
  projection: DesignerV2Projection,
  identity: { key?: string; revision?: number; metadata?: Partial<AgentMetadata> },
): DesignerV2Projection {
  const definition = cloneJson(projection.source);
  if (identity.key !== undefined) definition.key = identity.key;
  if (identity.revision !== undefined) definition.revision = identity.revision;
  if (identity.metadata) definition.metadata = { ...definition.metadata, ...cloneJson(identity.metadata) };
  return projectDefinition(definition);
}

export function updateCapabilities(
  projection: DesignerV2Projection,
  capabilities: Partial<DesignerV2Projection["capabilities"]>,
): DesignerV2Projection {
  const definition = cloneJson(projection.source);
  if (capabilities.observations) {
    definition.interaction.supportedObservations = [...capabilities.observations];
  }
  if (capabilities.behaviourModalities) {
    definition.interaction.supportedBehaviourModalities = [...capabilities.behaviourModalities];
  }
  if (capabilities.profileTags) definition.interaction.profileTags = [...capabilities.profileTags];
  return projectDefinition(definition);
}

export function replaceScopedGuidance(
  projection: DesignerV2Projection,
  target: { stateId: string; promptField: string },
  sections: PromptSection[],
): DesignerV2Projection {
  const definition = cloneJson(projection.source);
  const state = definition.states.find((candidate) => candidate.id === target.stateId);
  if (!state || state.kind === "final" || !state.policy) {
    throw new Error(`State ${target.stateId} does not have an ordinary policy.`);
  }
  const config = cloneJson(state.policy.config);
  const current = config[target.promptField];
  const prompt: JsonObject = isJsonObject(current)
    ? cloneJson(current) : {};
  prompt.sections = cloneJson(sections);
  config[target.promptField] = prompt;
  state.policy = { ...state.policy, config };
  return projectDefinition(definition);
}

export function replaceSituationPolicy(
  projection: DesignerV2Projection,
  stateId: string,
  policy: ComponentEnvelope,
): DesignerV2Projection {
  const definition = cloneJson(projection.source);
  const state = definition.states.find((candidate) => candidate.id === stateId);
  if (!state || state.kind === "final") throw new Error(`State ${stateId} cannot have an ordinary policy.`);
  state.policy = cloneJson(policy);
  return projectDefinition(definition);
}
