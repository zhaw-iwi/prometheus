import type { DefinitionDiagnostic } from "../api/designerApi";
import type { AgentDefinitionV1 } from "../model/agentDefinition";
import type { DesignerStepId, ValidationTarget } from "../stepper/DesignerStepper";

export function diagnosticStep(diagnostic: DefinitionDiagnostic): DesignerStepId {
  const pointer = diagnostic.pointer;
  if (pointer.startsWith("/interaction")) return "capabilities";
  if (pointer.startsWith("/states") || pointer.startsWith("/transitions")
    || pointer === "/lifecycle/initialStateId" || pointer === "/lifecycle/startOnCreation") return "interaction";
  if (pointer.startsWith("/storage") || pointer.startsWith("/resources")
    || pointer.startsWith("/lifecycle/initializers") || pointer.startsWith("/lifecycle/reset")) return "data-outcome";
  if (pointer.startsWith("/verification")) return "try";
  if (pointer === "") return "review";
  return "brief";
}

export function targetForDiagnostic(
  diagnostic: DefinitionDiagnostic,
  definition?: AgentDefinitionV1,
): ValidationTarget {
  const pointer = diagnostic.pointer;
  const actionMatch = pointer.match(/^\/transitions\/(\d+)\/actions\/(\d+)/);
  const action = actionMatch
    ? definition?.transitions[Number(actionMatch[1])]?.actions[Number(actionMatch[2])]
    : undefined;
  if (action?.kind === "prometheus.action.extract" && typeof action.config.targetStorageKey === "string") {
    return { stepId: "data-outcome", fieldId: `data-item-${action.config.targetStorageKey}`, message: diagnostic.message };
  }
  const stepId = diagnosticStep(diagnostic);
  if (stepId === "interaction") {
    const stateMatch = pointer.match(/^\/states\/(\d+)/);
    const transitionMatch = pointer.match(/^\/transitions\/(\d+)/);
    const stateId = stateMatch ? definition?.states[Number(stateMatch[1])]?.id : undefined;
    const ruleId = transitionMatch ? definition?.transitions[Number(transitionMatch[1])]?.id : undefined;
    const conditionMatch = pointer.match(/^\/transitions\/\d+\/decisions\/(\d+)/);
    const effectMatch = pointer.match(/^\/transitions\/\d+\/actions\/(\d+)/);
    return {
      stepId,
      fieldId: stateId ? `interaction-situation-${stateId}`
        : ruleId && conditionMatch ? `interaction-rule-${ruleId}-condition-${conditionMatch[1]}`
          : ruleId && effectMatch ? `interaction-rule-${ruleId}-effect-${effectMatch[1]}`
            : ruleId ? `interaction-rule-${ruleId}` : "designer-panel-interaction",
      message: diagnostic.message,
    };
  }
  if (stepId === "data-outcome") {
    const storageMatch = pointer.match(/^\/storage\/(\d+)/);
    const initializerMatch = pointer.match(/^\/lifecycle\/initializers\/(\d+)/);
    const resourceMatch = pointer.match(/^\/resources\/(\d+)/);
    const initializer = initializerMatch ? definition?.lifecycle.initializers[Number(initializerMatch[1])] : undefined;
    const resourceId = resourceMatch ? definition?.resources[Number(resourceMatch[1])]?.id : undefined;
    const resourceInitializer = typeof resourceId === "string" ? definition?.lifecycle.initializers.find(
      (candidate) => candidate.config.choicesResourceId === resourceId,
    ) : undefined;
    const key = storageMatch ? definition?.storage[Number(storageMatch[1])]?.key
      : initializer?.config.storageKey ?? resourceInitializer?.config.storageKey;
    return {
      stepId,
      fieldId: typeof key === "string" ? `data-item-${key}` : "designer-panel-data-outcome",
      message: diagnostic.message,
    };
  }
  const fieldId = stepId === "brief" && pointer.startsWith("/metadata/displayName") ? "brief-display-name"
    : stepId === "brief" ? "designer-panel-brief"
      : stepId === "capabilities" ? "designer-panel-capabilities"
        : stepId === "try" ? "designer-panel-try" : "review-validation-title";
  return { stepId, fieldId, message: diagnostic.message };
}
