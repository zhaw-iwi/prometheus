import { useEffect, useMemo, useState } from "react";
import {
  createDefinitionDraft,
  type ComponentDefinition,
  type DefinitionDiagnostic,
  type DefinitionRevisionView,
  type DefinitionSummary,
  DesignerApiError,
  fetchDefinitionRevision,
  type RequestFunction,
  updateDefinitionDraft,
  validateDefinition,
  validateDefinitionForPublication,
} from "../api/designerApi";
import type { AgentDefinitionV1 } from "../model/agentDefinition";
import type { DesignerRoute } from "../routing/designerRoute";
import { DesignerStepper, type DesignerStepId, type ValidationTarget } from "../stepper/DesignerStepper";
import { ReviewPanel, type ApplyResult } from "../review/ReviewPanel";
import { diagnosticStep } from "../review/reviewModel";
import { BehaviourPanel, PurposePanel, SensingPanel } from "./AuthoringPanels";
import { ReactionPanel } from "./ReactionPanel";
import { StateFlowPanel } from "./StateFlowPanel";
import {
  authoringFormToDefinition,
  createDefaultDefinition,
  definitionToAuthoringForm,
  type AuthoringForm,
  localFormIssues,
  serializedDefinition,
} from "./editorModel";

interface DefinitionAuthoringEditorProps {
  route: Exclude<DesignerRoute, { kind: "catalog" }>;
  components: ComponentDefinition[];
  definitionSummary?: DefinitionSummary;
  adminToken: string;
  request: RequestFunction;
  onDirtyChange: (dirty: boolean) => void;
  onSaved: (key: string, revision: number) => void;
  onWorkspaceChanged?: (target?: { key: string; revision: number }) => void;
}

export function DefinitionAuthoringEditor({
  route,
  components,
  definitionSummary,
  adminToken,
  request,
  onDirtyChange,
  onSaved,
  onWorkspaceChanged = () => undefined,
}: DefinitionAuthoringEditorProps) {
  const isNew = route.kind === "new";
  const initial = useMemo(() => isNew ? createDefaultDefinition() : null, [isNew]);
  const [source, setSource] = useState<AgentDefinitionV1 | null>(initial);
  const [form, setForm] = useState<AuthoringForm | null>(() => initial ? definitionToAuthoringForm(initial) : null);
  const [persisted, setPersisted] = useState<DefinitionRevisionView | null>(null);
  const [baseline, setBaseline] = useState(() => initial ? serializedDefinition(initial) : "");
  const [keyConfirmed, setKeyConfirmed] = useState(!isNew);
  const [loading, setLoading] = useState(!isNew);
  const [saving, setSaving] = useState(false);
  const [loadError, setLoadError] = useState("");
  const [saveMessage, setSaveMessage] = useState("");
  const [diagnostics, setDiagnostics] = useState<DefinitionDiagnostic[]>([]);
  const [validationTarget, setValidationTarget] = useState<ValidationTarget | null>(null);
  const [conflict, setConflict] = useState<DefinitionRevisionView | null>(null);
  const [validatedFingerprint, setValidatedFingerprint] = useState<string | null>(null);
  const [activeStep, setActiveStep] = useState<DesignerStepId>("purpose");

  useEffect(() => {
    if (route.kind !== "editor") return;
    let current = true;
    setLoading(true);
    void fetchDefinitionRevision(route.key, route.revision, adminToken, request)
      .then((revision) => {
        if (!current) return;
        setPersisted(revision);
        setSource(revision.definition);
        setForm(definitionToAuthoringForm(revision.definition));
        setBaseline(serializedDefinition(revision.definition));
        setKeyConfirmed(true);
        setValidatedFingerprint(null);
        setLoadError("");
      })
      .catch((error) => {
        if (current) setLoadError(error instanceof Error ? error.message : "The revision could not be loaded.");
      })
      .finally(() => { if (current) setLoading(false); });
    return () => { current = false; };
  }, [adminToken, request, route]);

  const document = useMemo(() => source && form ? authoringFormToDefinition(source, form) : null, [form, source]);
  const dirty = document ? serializedDefinition(document) !== baseline : false;

  useEffect(() => onDirtyChange(dirty), [dirty, onDirtyChange]);
  useEffect(() => () => onDirtyChange(false), [onDirtyChange]);

  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => {
      if (!dirty) return;
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);

  if (loading) {
    return <div className="editor-loading" aria-busy="true" data-testid="definition-loading">Loading revision…</div>;
  }
  if (loadError || !source || !form || !document) {
    return <div className="center-state compact-state" role="alert" data-testid="definition-load-error">
      <h2>Revision unavailable</h2><p>{loadError || "The revision could not be represented."}</p>
    </div>;
  }

  const readOnly = persisted !== null && persisted.status !== "DRAFT";
  const save = async () => {
    const issues = localFormIssues(form, keyConfirmed);
    if (issues.length > 0) {
      setValidationTarget({ stepId: "purpose", fieldId: issues[0].fieldId, message: issues[0].message });
      setSaveMessage("Complete the highlighted identity fields before saving.");
      return;
    }
    setSaving(true);
    setSaveMessage("");
    setConflict(null);
    try {
      const saved = persisted
        ? await updateDefinitionDraft(document, persisted.optimisticVersion, adminToken, request)
        : await createDefinitionDraft(document, adminToken, request);
      const validation = await validateDefinition(saved.definition, adminToken, request);
      setPersisted(saved);
      setSource(saved.definition);
      setForm(definitionToAuthoringForm(saved.definition));
      setBaseline(serializedDefinition(saved.definition));
      setDiagnostics(validation.diagnostics);
      setValidatedFingerprint(null);
      setValidationTarget(null);
      setSaveMessage(validation.valid
        ? "Draft saved and backend validation passed."
        : "Draft saved. Backend validation found issues to review.");
      if (!persisted) onSaved(saved.key, saved.revision);
    } catch (error) {
      if (error instanceof DesignerApiError) {
        setDiagnostics(error.diagnostics);
        if (error.code === "OPTIMISTIC_CONFLICT" && route.kind === "editor") {
          try {
            setConflict(await fetchDefinitionRevision(route.key, route.revision, adminToken, request));
          } catch {
            setSaveMessage("The server copy changed and could not be reloaded. Return to the catalog and try again.");
          }
        } else {
          setSaveMessage(error.message);
          const first = error.diagnostics[0];
          if (first) setValidationTarget(targetForDiagnostic(first, document));
        }
      } else {
        setSaveMessage("The draft could not be saved. Check the application and try again.");
      }
    } finally {
      setSaving(false);
    }
  };

  const loadServerCopy = () => {
    if (!conflict) return;
    setPersisted(conflict);
    setSource(conflict.definition);
    setForm(definitionToAuthoringForm(conflict.definition));
    setBaseline(serializedDefinition(conflict.definition));
    setValidatedFingerprint(null);
    setConflict(null);
    setSaveMessage("Loaded the newer server draft.");
  };

  const keepLocalCopy = () => {
    if (!conflict) return;
    setPersisted(conflict);
    setSource(document);
    setBaseline(serializedDefinition(conflict.definition));
    setValidatedFingerprint(null);
    setConflict(null);
    setSaveMessage("Local changes are preserved. Save again to replace the newer server draft.");
  };

  const changeForm = (next: AuthoringForm) => {
    setForm(next);
    setDiagnostics([]);
    setValidationTarget(null);
    setValidatedFingerprint(null);
  };

  const changeDefinition = (next: AgentDefinitionV1) => {
    setSource(next);
    setForm(definitionToAuthoringForm(next));
    setDiagnostics([]);
    setValidationTarget(null);
    setValidatedFingerprint(null);
  };

  const runValidation = async (): Promise<boolean> => {
    setSaveMessage("");
    try {
      const validation = await validateDefinitionForPublication(document, adminToken, request);
      setDiagnostics(validation.diagnostics);
      setValidatedFingerprint(validation.valid ? serializedDefinition(document) : null);
      setSaveMessage(validation.valid
        ? "Backend validation and compilation checks passed for this document."
        : "Backend validation found issues. Follow the Review links to correct them.");
      return validation.valid;
    } catch (error) {
      if (error instanceof DesignerApiError) {
        setDiagnostics(error.diagnostics);
        setValidatedFingerprint(null);
        setSaveMessage(error.message);
      } else {
        setSaveMessage("Backend validation could not be completed. Check the application and retry.");
      }
      return false;
    }
  };

  const applyDefinitionJson = async (next: AgentDefinitionV1): Promise<ApplyResult> => {
    try {
      const validation = await validateDefinition(next, adminToken, request);
      changeDefinition(next);
      setDiagnostics(validation.diagnostics);
      setValidatedFingerprint(null);
      return {
        applied: true,
        message: validation.valid
          ? "JSON applied to the guided form and backend validation passed. Save to persist it."
          : "JSON applied to the guided form. Backend semantic diagnostics are shown in Review.",
      };
    } catch (error) {
      if (error instanceof DesignerApiError) {
        setDiagnostics(error.diagnostics);
        setSaveMessage(error.message);
        return { applied: false, message: `${error.message} The guided form was not changed.` };
      }
      return { applied: false, message: "The backend could not validate this JSON. The guided form was not changed." };
    }
  };

  const panels = {
    purpose: <PurposePanel form={form} onChange={changeForm} isNew={!persisted}
      keyConfirmed={keyConfirmed} onKeyConfirmed={setKeyConfirmed} />,
    sensing: <SensingPanel form={form} onChange={changeForm} />,
    behaviour: <BehaviourPanel form={form} onChange={changeForm} components={components} />,
    reactions: <ReactionPanel definition={document} components={components} diagnostics={diagnostics}
      onChange={changeDefinition} />,
    "state-flow": <StateFlowPanel definition={document} components={components} diagnostics={diagnostics}
      onChange={changeDefinition} />,
    review: <ReviewPanel definition={document} persisted={persisted} definitionSummary={definitionSummary}
      diagnostics={diagnostics} active={activeStep === "review"} dirty={dirty}
      validationCurrent={validatedFingerprint === serializedDefinition(document)} adminToken={adminToken}
      request={request} onValidate={runValidation} onApplyDefinition={applyDefinitionJson}
      onDiagnosticSelect={(diagnostic) => setValidationTarget(targetForDiagnostic(diagnostic, document))}
      onRevisionChange={setPersisted} onWorkspaceChanged={onWorkspaceChanged} />,
  };

  return (
    <>
      <div className="editor-toolbar" data-testid="editor-toolbar">
        <div className="draft-state">
          <span className={`dirty-dot${dirty ? " active" : ""}`} aria-hidden="true"></span>
          <strong data-testid="dirty-state">{dirty ? "Unsaved changes" : persisted
            ? persisted.status === "DRAFT" ? "Saved draft"
              : persisted.status === "PUBLISHED" ? "Published revision" : "Archived revision"
            : "New draft"}</strong>
          {persisted && <small>{persisted.status} · Version {persisted.optimisticVersion}</small>}
        </div>
        <button className="button primary" type="button" onClick={() => void save()}
          disabled={saving || !dirty || readOnly} data-testid="save-draft">
          {saving ? "Saving…" : "Save draft"}
        </button>
      </div>
      {readOnly && <div className="notice-banner" role="status">Published and archived revisions are immutable. A later lifecycle step can clone one into a draft.</div>}
      {saveMessage && <div className="notice-banner" role="status" data-testid="save-message">{saveMessage}</div>}
      {conflict && (
        <div className="conflict-banner" role="alert" data-testid="optimistic-conflict">
          <div><strong>A newer server draft exists.</strong><p>Choose which complete document should become your editing base.</p></div>
          <div className="conflict-actions">
            <button className="button secondary" type="button" onClick={loadServerCopy}>Load server draft</button>
            <button className="button primary" type="button" onClick={keepLocalCopy}>Keep local changes</button>
          </div>
        </div>
      )}
      {diagnostics.length > 0 && (
        <section className="diagnostic-summary" aria-label="Backend validation" data-testid="backend-diagnostics">
          <div><strong>Backend validation</strong><span>{diagnostics.length} diagnostic{diagnostics.length === 1 ? "" : "s"}</span></div>
          <ul>{diagnostics.map((diagnostic, index) => (
            <li key={`${diagnostic.code}:${diagnostic.pointer}:${index}`}>
              <button type="button" onClick={() => setValidationTarget(targetForDiagnostic(diagnostic, document))}>
                <span className={`diagnostic-severity ${diagnostic.severity.toLowerCase()}`}>{diagnostic.severity}</span>
                {diagnostic.message}
              </button>
            </li>
          ))}</ul>
        </section>
      )}
      <DesignerStepper panels={panels} validationTarget={validationTarget} onStepChange={setActiveStep} />
    </>
  );
}

export function targetForDiagnostic(
  diagnostic: DefinitionDiagnostic,
  definition?: AgentDefinitionV1,
): ValidationTarget {
  const pointer = diagnostic.pointer;
  const step = diagnosticStep(diagnostic);
  if (step === "review") {
    return { stepId: "review", fieldId: "review-validation-title", message: diagnostic.message };
  }
  if (pointer.startsWith("/interaction/supportedObservations")) {
    return { stepId: "sensing", fieldId: "designer-step-sensing", message: diagnostic.message };
  }
  if (pointer.startsWith("/interaction/supportedBehaviourModalities")) {
    return { stepId: "behaviour", fieldId: "designer-step-behaviour", message: diagnostic.message };
  }
  if (step === "behaviour") {
    return { stepId: "behaviour", fieldId: "designer-step-behaviour", message: diagnostic.message };
  }
  const stateMatch = pointer.match(/^\/states\/(\d+)/);
  if (stateMatch) {
    const id = definition?.states[Number(stateMatch[1])]?.id;
    return { stepId: "state-flow", fieldId: id ? `graph-diagnostic-state-${id}` : "designer-step-state-flow", message: diagnostic.message };
  }
  const transitionMatch = pointer.match(/^\/transitions\/(\d+)/);
  if (transitionMatch) {
    const id = definition?.transitions[Number(transitionMatch[1])]?.id;
    if (step === "reactions") {
      return { stepId: "reactions", fieldId: id ? `reaction-${id}` : "designer-step-reactions", message: diagnostic.message };
    }
    return { stepId: "state-flow", fieldId: id ? `graph-diagnostic-transition-${id}` : "designer-step-state-flow", message: diagnostic.message };
  }
  if (step === "state-flow") {
    return { stepId: "state-flow", fieldId: "designer-step-state-flow", message: diagnostic.message };
  }
  const fieldId = pointer === "/key" ? "purpose-key"
    : pointer.startsWith("/metadata/displayName") ? "purpose-display-name"
      : pointer.startsWith("/metadata/description") ? "purpose-description"
        : pointer.startsWith("/metadata/categoryPath") ? "purpose-category"
          : pointer.startsWith("/metadata/languageCode") ? "purpose-language"
            : "designer-step-purpose";
  return { stepId: "purpose", fieldId, message: diagnostic.message };
}
