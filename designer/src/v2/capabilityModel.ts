import type { ComponentDefinition } from "../api/designerApi";
import { cloneJson, isJsonObject, type JsonObject } from "../model/agentDefinition";
import type { DesignerV2Projection } from "./projection";
import { replaceSituationPolicy } from "./transforms";

export interface ComponentGroup {
  id: string;
  components: ComponentDefinition[];
  installedKinds: string[];
  ownedDataKeys: string[];
}

export function responseStrategies(components: ComponentDefinition[]): ComponentDefinition[] {
  return components.filter((component) => component.exposure === "GUIDED"
    && component.authoringRole === "RESPONSE_STRATEGY");
}

export function deterministicOperationGroups(
  projection: DesignerV2Projection,
  components: ComponentDefinition[],
): ComponentGroup[] {
  const groups = new Map<string, ComponentDefinition[]>();
  components.filter((component) => component.exposure === "GUIDED"
    && component.authoringRole === "DETERMINISTIC_OPERATION" && component.capabilityGroup)
    .forEach((component) => groups.set(component.capabilityGroup!, [
      ...(groups.get(component.capabilityGroup!) ?? []), component,
    ]));
  return [...groups].map(([id, grouped]) => {
    const kinds = new Set(grouped.map((component) => component.kind));
    const installedKinds = projection.capabilities.installedComponents
      .filter((component) => kinds.has(component.kind)).map((component) => component.kind);
    const referenced = new Set<string>();
    projection.capabilities.installedComponents.filter((component) => kinds.has(component.kind))
      .flatMap((component) => component.uses)
      .forEach((use) => collectStorageKeys(use.envelope.config, referenced));
    return {
      id,
      components: [...grouped].sort((left, right) => left.label.localeCompare(right.label)),
      installedKinds,
      ownedDataKeys: projection.data.items.map((item) => item.key).filter((key) => referenced.has(key)),
    };
  }).sort((left, right) => left.id.localeCompare(right.id));
}

export function mainStrategy(projection: DesignerV2Projection) {
  return projection.situations.find((situation) => situation.main)?.ordinaryPolicy ?? null;
}

export function selectMainStrategy(
  projection: DesignerV2Projection,
  component: ComponentDefinition,
): DesignerV2Projection {
  const main = projection.situations.find((situation) => situation.main);
  if (!main) throw new Error("This definition has no editable Main interaction.");
  return replaceSituationPolicy(projection, main.id, {
    kind: component.kind,
    version: component.version,
    config: cloneJson(component.defaultConfig),
  });
}

export function fixedStrategyRequirements(component: ComponentDefinition): {
  observations: string[];
  modalities: string[];
} {
  if (component.capabilityGroup === "prompt-response") return { observations: [], modalities: [] };
  return {
    observations: [...component.capabilities.consumedObservations],
    modalities: [...component.capabilities.emittedBehaviourModalities],
  };
}

export function strategyCompatibility(projection: DesignerV2Projection, component: ComponentDefinition) {
  const requirements = fixedStrategyRequirements(component);
  const missingObservations = requirements.observations.filter((id) => !projection.capabilities.observations.includes(id));
  const missingModalities = requirements.modalities.filter((id) => !projection.capabilities.behaviourModalities.includes(id));
  return { compatible: missingObservations.length === 0 && missingModalities.length === 0, missingObservations, missingModalities };
}

export function capabilityUses(projection: DesignerV2Projection, capabilityId: string) {
  return projection.capabilities.installedComponents.flatMap((component) => component.uses)
    .filter((use) => containsString(use.envelope.config, capabilityId));
}

function containsString(value: unknown, expected: string): boolean {
  if (typeof value === "string") return value === expected;
  if (Array.isArray(value)) return value.some((item) => containsString(item, expected));
  return isJsonObject(value) && Object.values(value).some((item) => containsString(item, expected));
}

function collectStorageKeys(value: unknown, result: Set<string>, propertyName = "") {
  if (typeof value === "string" && /storagekey$/i.test(propertyName)) {
    result.add(value);
    return;
  }
  if (Array.isArray(value)) {
    value.forEach((item) => collectStorageKeys(item, result, propertyName));
    return;
  }
  if (!isJsonObject(value)) return;
  Object.entries(value).forEach(([key, item]) => collectStorageKeys(item, result, key));
}

export function technicalEnvelope(kind: string, version: number, config: JsonObject) {
  return JSON.stringify({ kind, version, config }, null, 2);
}
