import { type CSSProperties, type ReactNode, useCallback, useEffect, useState } from "react";

export const DESIGNER_STEPS = [
  { id: "brief", title: "Brief", caption: "Set purpose and conduct." },
  { id: "capabilities", title: "Capabilities", caption: "Choose what it can notice and do." },
  { id: "interaction", title: "Interaction", caption: "Design ordinary behavior and exceptions." },
  { id: "data-outcome", title: "Data & outcome", caption: "Define context and results." },
  { id: "try", title: "Try", caption: "Test concrete examples." },
  { id: "review", title: "Review", caption: "Validate and publish." },
] as const;

export type DesignerStepId = (typeof DESIGNER_STEPS)[number]["id"];

export interface ValidationTarget {
  stepId: DesignerStepId;
  fieldId: string;
  message: string;
}

export interface DesignerStepperProps {
  initialStepId?: DesignerStepId;
  activeStepId?: DesignerStepId;
  validationTarget?: ValidationTarget | null;
  panels?: Partial<Record<DesignerStepId, ReactNode>>;
  onStepChange?: (stepId: DesignerStepId) => void;
}

export function clampStepIndex(index: number): number {
  return Math.max(0, Math.min(index, DESIGNER_STEPS.length - 1));
}

function stepIndex(stepId: DesignerStepId | undefined): number {
  const index = DESIGNER_STEPS.findIndex((step) => step.id === stepId);
  return index < 0 ? 0 : index;
}

export function DesignerStepper({
  initialStepId,
  activeStepId,
  validationTarget,
  panels = {},
  onStepChange,
}: DesignerStepperProps) {
  const [activeIndex, setActiveIndex] = useState(() => stepIndex(initialStepId));

  useEffect(() => {
    if (activeStepId !== undefined) setActiveIndex(stepIndex(activeStepId));
  }, [activeStepId]);

  const showStep = useCallback((requestedIndex: number) => {
    const nextIndex = clampStepIndex(requestedIndex);
    setActiveIndex(nextIndex);
    onStepChange?.(DESIGNER_STEPS[nextIndex].id);
  }, [onStepChange]);

  useEffect(() => {
    if (validationTarget) {
      showStep(stepIndex(validationTarget.stepId));
    }
  }, [showStep, validationTarget]);

  useEffect(() => {
    if (validationTarget && DESIGNER_STEPS[activeIndex].id === validationTarget.stepId) {
      document.getElementById(validationTarget.fieldId)?.focus();
    }
  }, [activeIndex, validationTarget]);

  return (
    <section className="editor-workflow" data-testid="designer-workflow">
      <ol className="wizard-progress" role="tablist" aria-label="Agent design steps"
        data-testid="designer-stepper">
        {DESIGNER_STEPS.map((step, index) => {
          const active = index === activeIndex;
          const itemStyle = { "--step-z": DESIGNER_STEPS.length - index } as CSSProperties;
          return (
            <li className="nav-item" style={itemStyle} key={step.id}>
              <button className={`nav-link${active ? " active" : ""}`} type="button" role="tab"
                id={`designer-step-${step.id}`} aria-controls={`designer-panel-${step.id}`}
                aria-selected={active} aria-current={active ? "step" : undefined}
                data-step-target data-testid={`step-target-${step.id}`}
                onClick={() => showStep(index)}>
                <span className="step-index">{index + 1}</span>
                <span className="step-copy">
                  <span className="step-title">{step.title}</span>
                  <span className="step-caption">{step.caption}</span>
                </span>
              </button>
            </li>
          );
        })}
      </ol>

      {validationTarget && (
        <div className="validation-alert" role="alert" data-testid="designer-validation-alert">
          {validationTarget.message}
        </div>
      )}

      {DESIGNER_STEPS.map((step, index) => {
        const active = index === activeIndex;
        return (
          <section className={`wizard-step${active ? " active" : ""}`} role="tabpanel"
            id={`designer-panel-${step.id}`} aria-labelledby={`designer-step-${step.id}`}
            hidden={!active} key={step.id} data-testid={`step-panel-${step.id}`}>
            <div className="step-panel-heading">
              <div>
                <span className="eyebrow">Step {index + 1} of {DESIGNER_STEPS.length}</span>
                <h2>{step.title}</h2>
                <p>{step.caption}</p>
              </div>
              <span className="foundation-badge">{panels[step.id] ? "Authoring" : "Foundation"}</span>
            </div>
            <div className={`step-placeholder${panels[step.id] ? " populated" : ""}`}>
              {panels[step.id] ?? <p>This guided authoring panel is prepared for the next roadmap milestone.</p>}
            </div>
            <div className="step-actions">
              <button className="button secondary" type="button" data-prev data-testid={`step-back-${step.id}`}
                disabled={activeIndex === 0} onClick={() => showStep(activeIndex - 1)}>
                Back
              </button>
              <span className="step-position">{step.title}</span>
              <button className="button primary" type="button" data-next data-testid={`step-next-${step.id}`}
                disabled={activeIndex === DESIGNER_STEPS.length - 1}
                onClick={() => showStep(activeIndex + 1)}>
                {activeIndex === DESIGNER_STEPS.length - 2 ? "Review" : "Next"}
              </button>
            </div>
          </section>
        );
      })}
    </section>
  );
}
