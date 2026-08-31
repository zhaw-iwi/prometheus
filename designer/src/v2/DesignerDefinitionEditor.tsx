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
import { ReviewPanel, type ApplyResult } from "../review/ReviewPanel";
import type { DesignerRoute } from "../routing/designerRoute";
import { DesignerStepper, type DesignerStepId, type ValidationTarget } from "../stepper/DesignerStepper";
import { targetForDiagnostic } from "./diagnostics";
import { ProjectionOverviewPanels } from "./ProjectionOverviewPanels";
import { BriefPanel } from "./BriefPanel";
import { CapabilitiesPanel } from "./CapabilitiesPanel";
import { briefIssues } from "./briefModel";
import { InteractionPanel } from "./InteractionPanel";
import { DataOutcomePanel } from "./DataOutcomePanel";
import {
  createDefaultDefinition,
  projectDefinition,
  serializedDefinition,
} from "./projection";

interface DesignerDefinitionEditorProps {
  route: Exclude<DesignerRoute, { kind: "catalog" }>;
  components: ComponentDefinition[];
  definitionSummary?: DefinitionSummary;
  adminToken: string;
  request: RequestFunction;
  onDirtyChange: (dirty: boolean) => void;
  onSaved: (key: string, revision: number) => void;
  onWorkspaceChanged?: (target?: { key: string; revision: number }) => void;
}

export function DesignerDefinitionEditor({
  route,
  components,
  definitionSummary,
  adminToken,
  request,
  onDirtyChange,
  onSaved,
  onWorkspaceChanged = () => undefined,
}: DesignerDefinitionEditorProps) {
  const isNew = route.kind === "new";
  const initial = useMemo(() => isNew ? createDefaultDefinition() : null, [isNew]);
  const [document, setDocument] = useState<AgentDefinitionV1 | null>(initial);
  const [persisted, setPersisted] = useState<DefinitionRevisionView | null>(null);
  const [baseline, setBaseline] = useState(() => initial ? serializedDefinition(initial) : "");
  const [loading, setLoading] = useState(!isNew);
  const [saving, setSaving] = useState(false);
  const [loadError, setLoadError] = useState("");
  const [saveMessage, setSaveMessage] = useState("");
  const [diagnostics, setDiagnostics] = useState<DefinitionDiagnostic[]>([]);
  const [validationTarget, setValidationTarget] = useState<ValidationTarget | null>(null);
  const [conflict, setConflict] = useState<DefinitionRevisionView | null>(null);
  const [validatedFingerprint, setValidatedFingerprint] = useState<string | null>(null);
  const [activeStep, setActiveStep] = useState<DesignerStepId>("brief");
  const [keyConfirmed, setKeyConfirmed] = useState(!isNew);

  useEffect(() => {
    if (route.kind !== "editor") return;
    let current = true;
    setLoading(true);
    void fetchDefinitionRevision(route.key, route.revision, adminToken, request)
      .then((revision) => {
        if (!current) return;
        setPersisted(revision);
        setDocument(revision.definition);
        setBaseline(serializedDefinition(revision.definition));
        setValidatedFingerprint(null);
        setKeyConfirmed(true);
        setLoadError("");
      })
      .catch((error) => {
        if (current) setLoadError(error instanceof Error ? error.message : "The revision could not be loaded.");
      })
      .finally(() => { if (current) setLoading(false); });
    return () => { current = false; };
  }, [adminToken, request, route]);

  const projection = useMemo(() => document ? projectDefinition(document) : null, [document]);
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
  if (loadError || !document || !projection) {
    return <div className="center-state compact-state" role="alert" data-testid="definition-load-error">
      <h2>Revision unavailable</h2><p>{loadError || "The revision could not be projected safely."}</p>
    </div>;
  }

  const readOnly = persisted !== null && persisted.status !== "DRAFT";
  const localIssues = briefIssues(projection, persisted !== null || keyConfirmed);
  const changeDefinition = (next: AgentDefinitionV1) => {
    setDocument(next);
    setDiagnostics([]);
    setValidationTarget(null);
    setValidatedFingerprint(null);
  };

  const save = async () => {
    if (Object.keys(localIssues).length > 0) {
      setSaveMessage("Complete the highlighted Brief fields and confirm the stable key before saving.");
      setActiveStep("brief");
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
      setDocument(saved.definition);
      setBaseline(serializedDefinition(saved.definition));
      setDiagnostics(validation.diagnostics);
      setValidatedFingerprint(null);
      setValidationTarget(null);
      setKeyConfirmed(true);
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
    setDocument(conflict.definition);
    setBaseline(serializedDefinition(conflict.definition));
    setValidatedFingerprint(null);
    setConflict(null);
    setSaveMessage("Loaded the newer server draft.");
    setKeyConfirmed(true);
  };

  const keepLocalCopy = () => {
    if (!conflict) return;
    setPersisted(conflict);
    setBaseline(serializedDefinition(conflict.definition));
    setValidatedFingerprint(null);
    setConflict(null);
    setSaveMessage("Local changes are preserved. Save again to replace the newer server draft.");
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
      if (!persisted) setKeyConfirmed(false);
      setDiagnostics(validation.diagnostics);
      setValidatedFingerprint(null);
      return {
        applied: true,
        message: validation.valid
          ? "JSON applied to the V2 projection and backend validation passed. Save to persist it."
          : "JSON applied to the V2 projection. Backend semantic diagnostics are shown in Review.",
      };
    } catch (error) {
      if (error instanceof DesignerApiError) {
        setDiagnostics(error.diagnostics);
        setSaveMessage(error.message);
        return { applied: false, message: `${error.message} The projected document was not changed.` };
      }
      return { applied: false, message: "The backend could not validate this JSON. The projected document was not changed." };
    }
  };

  const overviewPanels = ProjectionOverviewPanels({ projection });
  const panels = {
    ...overviewPanels,
    brief: <BriefPanel projection={projection} persisted={persisted !== null} keyConfirmed={keyConfirmed}
      readOnly={readOnly} adminToken={adminToken} request={request} onKeyConfirmedChange={setKeyConfirmed}
      onChange={changeDefinition} />,
    capabilities: <CapabilitiesPanel projection={projection} components={components} readOnly={readOnly}
      onChange={changeDefinition} onGoToInteraction={() => setActiveStep("interaction")} />,
    interaction: <InteractionPanel projection={projection} components={components} readOnly={readOnly}
      onChange={changeDefinition} onGoToCapabilities={() => setActiveStep("capabilities")} />,
    "data-outcome": <DataOutcomePanel projection={projection} readOnly={readOnly}
      onChange={changeDefinition} onGoToInteraction={() => setActiveStep("interaction")} />,
    review: <ReviewPanel definition={document} persisted={persisted} definitionSummary={definitionSummary}
      diagnostics={diagnostics} active={activeStep === "review"} dirty={dirty}
      validationCurrent={validatedFingerprint === serializedDefinition(document)} adminToken={adminToken}
      request={request} onValidate={runValidation} onApplyDefinition={applyDefinitionJson}
      onDiagnosticSelect={(diagnostic) => setValidationTarget(targetForDiagnostic(diagnostic, document))}
      onRevisionChange={setPersisted} onWorkspaceChanged={onWorkspaceChanged} />,
  };

  return <>
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
        disabled={saving || !dirty || readOnly || Object.keys(localIssues).length > 0} data-testid="save-draft">
        {saving ? "Saving…" : "Save draft"}
      </button>
    </div>
    {readOnly && <div className="notice-banner" role="status">Published and archived revisions are immutable. Clone one into a draft to make changes.</div>}
    {saveMessage && <div className="notice-banner" role="status" data-testid="save-message">{saveMessage}</div>}
    {conflict && <div className="conflict-banner" role="alert" data-testid="optimistic-conflict">
      <div><strong>A newer server draft exists.</strong><p>Choose which complete document should become your editing base.</p></div>
      <div className="conflict-actions">
        <button className="button secondary" type="button" onClick={loadServerCopy}>Load server draft</button>
        <button className="button primary" type="button" onClick={keepLocalCopy}>Keep local changes</button>
      </div>
    </div>}
    {diagnostics.length > 0 && <section className="diagnostic-summary" aria-label="Backend validation"
      data-testid="backend-diagnostics">
      <div><strong>Backend validation</strong><span>{diagnostics.length} diagnostic{diagnostics.length === 1 ? "" : "s"}</span></div>
      <ul>{diagnostics.map((diagnostic, index) => <li key={`${diagnostic.code}:${diagnostic.pointer}:${index}`}>
        <button type="button" onClick={() => setValidationTarget(targetForDiagnostic(diagnostic, document))}>
          <span className={`diagnostic-severity ${diagnostic.severity.toLowerCase()}`}>{diagnostic.severity}</span>
          {diagnostic.message}
        </button>
      </li>)}</ul>
    </section>}
    <DesignerStepper panels={panels} validationTarget={validationTarget} activeStepId={activeStep}
      onStepChange={setActiveStep} />
  </>;
}
