import { useEffect, useMemo, useRef, useState } from "react";
import {
  activateDefinitionRevision,
  archiveDefinitionRevision,
  cloneDefinitionRevision,
  closeDefinitionPreview,
  createDefinitionPreview,
  DesignerApiError,
  exportDefinitionRevision,
  fetchPromptPreviews,
  generatePreviewBehaviour,
  publishDefinitionRevision,
  resetDefinitionPreview,
  submitPreviewEvent,
  type DefinitionDiagnostic,
  type DefinitionRevisionView,
  type DefinitionSummary,
  type PreviewEvent,
  type PreviewSnapshot,
  type PromptPreview,
  type RequestFunction,
} from "../api/designerApi";
import type { AgentDefinitionV1 } from "../model/agentDefinition";
import { groupDiagnostics, parseDefinitionJson, plainSummary, prettyDefinition } from "./reviewModel";

interface ApplyResult {
  applied: boolean;
  message: string;
}

interface ReviewPanelProps {
  definition: AgentDefinitionV1;
  persisted: DefinitionRevisionView | null;
  definitionSummary?: DefinitionSummary;
  diagnostics: DefinitionDiagnostic[];
  active: boolean;
  dirty: boolean;
  validationCurrent: boolean;
  adminToken: string;
  request: RequestFunction;
  onValidate: () => Promise<boolean>;
  onApplyDefinition: (definition: AgentDefinitionV1) => Promise<ApplyResult>;
  onDiagnosticSelect: (diagnostic: DefinitionDiagnostic) => void;
  onRevisionChange: (revision: DefinitionRevisionView) => void;
  onWorkspaceChanged: (target?: { key: string; revision: number }) => void;
}

const EMPTY_EVENT: PreviewEvent = { type: "", actor: "user", kind: "observation", payload: "" };

export function ReviewPanel({
  definition,
  persisted,
  definitionSummary,
  diagnostics,
  active,
  dirty,
  validationCurrent,
  adminToken,
  request,
  onValidate,
  onApplyDefinition,
  onDiagnosticSelect,
  onRevisionChange,
  onWorkspaceChanged,
}: ReviewPanelProps) {
  const [jsonDraft, setJsonDraft] = useState(() => prettyDefinition(definition));
  const [jsonMessage, setJsonMessage] = useState("");
  const [jsonApplying, setJsonApplying] = useState(false);
  const [prompts, setPrompts] = useState<PromptPreview[]>([]);
  const [promptState, setPromptState] = useState<"idle" | "loading" | "ready" | "error">("idle");
  const [promptMessage, setPromptMessage] = useState("");
  const [preview, setPreview] = useState<PreviewSnapshot | null>(null);
  const previewId = useRef<string | null>(null);
  const [previewBusy, setPreviewBusy] = useState(false);
  const [previewMessage, setPreviewMessage] = useState("");
  const [event, setEvent] = useState<PreviewEvent>(EMPTY_EVENT);
  const [advancedEvent, setAdvancedEvent] = useState(false);
  const [eventJson, setEventJson] = useState(() => JSON.stringify(EMPTY_EVENT, null, 2));
  const [lifecycleBusy, setLifecycleBusy] = useState(false);
  const [lifecycleMessage, setLifecycleMessage] = useState("");
  const [cloneKey, setCloneKey] = useState(definition.key);
  const nextRevision = Math.max(0, ...(definitionSummary?.revisions.map((revision) => revision.revision) ?? [definition.revision])) + 1;
  const [cloneRevision, setCloneRevision] = useState(nextRevision);
  const groups = useMemo(() => groupDiagnostics(diagnostics), [diagnostics]);
  const summary = useMemo(() => plainSummary(definition), [definition]);

  useEffect(() => {
    setJsonDraft(prettyDefinition(definition));
    setCloneKey(definition.key);
  }, [definition]);

  useEffect(() => {
    setCloneRevision(nextRevision);
  }, [nextRevision]);

  useEffect(() => {
    if (!active) return;
    let current = true;
    setPromptState("loading");
    setPromptMessage("");
    void fetchPromptPreviews(definition, adminToken, request)
      .then((result) => {
        if (!current) return;
        setPrompts(result);
        setPromptState("ready");
      })
      .catch((error) => {
        if (!current) return;
        setPromptState("error");
        setPromptMessage(messageFor(error, "Composed prompts could not be loaded."));
      });
    return () => { current = false; };
  }, [active, adminToken, definition, request]);

  useEffect(() => () => {
    const id = previewId.current;
    if (id) void closeDefinitionPreview(id, adminToken, request).catch(() => undefined);
  }, [adminToken, request]);

  if (!active) {
    return <div className="review-panel" data-testid="review-panel-inactive" aria-hidden="true"></div>;
  }

  const applyJson = async () => {
    const parsed = parseDefinitionJson(jsonDraft);
    if (!parsed.ok) {
      const location = parsed.failure.line === null ? "" : ` at line ${parsed.failure.line}, column ${parsed.failure.column}`;
      setJsonMessage(`JSON parse error${location}. The projected document was not changed. ${parsed.failure.message}`);
      return;
    }
    setJsonApplying(true);
    try {
      const result = await onApplyDefinition(parsed.definition);
      setJsonMessage(result.message);
      if (result.applied) setJsonDraft(prettyDefinition(parsed.definition));
    } finally {
      setJsonApplying(false);
    }
  };

  const startPreview = async () => {
    await previewOperation(async () => {
      if (previewId.current) await closeDefinitionPreview(previewId.current, adminToken, request);
      const created = await createDefinitionPreview(definition, adminToken, request);
      previewId.current = created.id;
      setPreview(created);
      setPreviewMessage("Disposable preview started. Nothing in this session is persisted.");
    });
  };

  const sendEvent = async () => {
    if (!preview) return;
    let payload = event;
    if (advancedEvent) {
      try {
        payload = JSON.parse(eventJson) as PreviewEvent;
      } catch (error) {
        setPreviewMessage(`Event JSON was not sent: ${error instanceof Error ? error.message : "invalid JSON"}`);
        return;
      }
    }
    if (!payload.type?.trim()) {
      setPreviewMessage("Choose or enter an event type before sending it.");
      return;
    }
    await previewOperation(async () => updatePreview(await submitPreviewEvent(preview.id, payload, adminToken, request)));
  };

  const previewOperation = async (operation: () => Promise<void>) => {
    setPreviewBusy(true);
    setPreviewMessage("");
    try {
      await operation();
    } catch (error) {
      if (error instanceof DesignerApiError && error.code === "PREVIEW_NOT_FOUND") {
        previewId.current = null;
        setPreview(null);
      }
      setPreviewMessage(messageFor(error, "The preview operation could not be completed."));
    } finally {
      setPreviewBusy(false);
    }
  };

  const updatePreview = (snapshot: PreviewSnapshot) => {
    previewId.current = snapshot.id;
    setPreview(snapshot);
  };

  const closePreview = async () => {
    if (!preview) return;
    await previewOperation(async () => {
      await closeDefinitionPreview(preview.id, adminToken, request);
      previewId.current = null;
      setPreview(null);
      setPreviewMessage("Disposable preview closed and its in-memory state was removed.");
    });
  };

  const publish = async () => {
    if (!persisted || !window.confirm(
      `Publish ${persisted.key} revision ${persisted.revision}? Its canonical JSON becomes immutable. This does not activate it.`,
    )) return;
    await lifecycleOperation(async () => {
      const published = await publishDefinitionRevision(persisted.key, persisted.revision,
        persisted.optimisticVersion, adminToken, request);
      onRevisionChange(published);
      setLifecycleMessage(`Revision ${published.revision} is published and immutable. It is not active until you activate it.`);
      onWorkspaceChanged();
    });
  };

  const activate = async () => {
    if (!persisted || !definitionSummary || !window.confirm(
      `Activate ${persisted.key} revision ${persisted.revision} for newly created instances? Existing instances remain pinned to their current revision.`,
    )) return;
    await lifecycleOperation(async () => {
      await activateDefinitionRevision(persisted.key, persisted.revision, definitionSummary.optimisticVersion,
        adminToken, request);
      setLifecycleMessage(`Revision ${persisted.revision} is active for new instances only. Existing instances are unchanged.`);
      onWorkspaceChanged();
    });
  };

  const archive = async () => {
    if (!persisted || !window.confirm(
      `Archive ${persisted.key} revision ${persisted.revision}? It will remain immutable and cannot be activated or used for new instances.`,
    )) return;
    await lifecycleOperation(async () => {
      const archived = await archiveDefinitionRevision(persisted.key, persisted.revision,
        persisted.optimisticVersion, adminToken, request);
      onRevisionChange(archived);
      setLifecycleMessage(`Revision ${archived.revision} is archived.`);
      onWorkspaceChanged();
    });
  };

  const clone = async () => {
    if (!persisted || !cloneKey.trim() || cloneRevision < 1) return;
    await lifecycleOperation(async () => {
      const cloned = await cloneDefinitionRevision(persisted.key, persisted.revision, cloneKey.trim(),
        cloneRevision, adminToken, request);
      setLifecycleMessage(`Created editable draft ${cloned.key} revision ${cloned.revision}.`);
      onWorkspaceChanged({ key: cloned.key, revision: cloned.revision });
    });
  };

  const exportRevision = async () => {
    if (!persisted) return;
    await lifecycleOperation(async () => {
      const exported = await exportDefinitionRevision(persisted.key, persisted.revision, adminToken, request);
      const url = URL.createObjectURL(new Blob([prettyDefinition(exported)], { type: "application/json" }));
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `${persisted.key}-revision-${persisted.revision}.json`;
      anchor.click();
      URL.revokeObjectURL(url);
      setLifecycleMessage(`Exported canonical revision ${persisted.revision} as JSON.`);
    });
  };

  const lifecycleOperation = async (operation: () => Promise<void>) => {
    setLifecycleBusy(true);
    setLifecycleMessage("");
    try {
      await operation();
    } catch (error) {
      setLifecycleMessage(messageFor(error, "The lifecycle operation could not be completed."));
    } finally {
      setLifecycleBusy(false);
    }
  };

  const canPublish = persisted?.status === "DRAFT" && !dirty && validationCurrent
    && !diagnostics.some((diagnostic) => diagnostic.severity === "ERROR");
  const isActive = persisted !== null && definitionSummary?.activeRevision === persisted.revision;
  const canActivate = persisted?.status === "PUBLISHED" && !isActive;
  const canArchive = persisted?.status === "PUBLISHED" && !isActive;

  return (
    <div className="review-panel" data-testid="review-panel">
      <section className="review-section" aria-labelledby="review-summary-title">
        <div className="section-heading"><div><span className="eyebrow">At a glance</span><h3 id="review-summary-title">Agent summary</h3></div></div>
        <dl className="review-summary">{summary.map((item) => <div key={item.label}><dt>{item.label}</dt><dd>{item.value}</dd></div>)}</dl>
      </section>

      <section className="review-section" aria-labelledby="review-validation-title">
        <div className="section-heading">
          <div><span className="eyebrow">Backend authority</span><h3 id="review-validation-title">Validation</h3></div>
          <button className="button secondary" type="button" onClick={() => void onValidate()} data-testid="validate-review">Validate now</button>
        </div>
        <p className={`validation-state ${validationCurrent ? "valid" : "stale"}`} data-testid="review-validation-state">
          {validationCurrent ? "Backend validation is current for this exact document." : "Validate this exact document before publication."}
        </p>
        {groups.length === 0 ? <p className="empty-copy">No backend diagnostics to show.</p> : groups.map((group) => (
          <div className="diagnostic-group" key={group.stepId} data-testid={`diagnostic-group-${group.stepId}`}>
            <h4>{group.title}</h4>
            <ul>{group.diagnostics.map((diagnostic, index) => <li key={`${diagnostic.code}:${diagnostic.pointer}:${index}`}>
              <button type="button" onClick={() => onDiagnosticSelect(diagnostic)}>
                <span className={`diagnostic-severity ${diagnostic.severity.toLowerCase()}`}>{diagnostic.severity}</span>
                <span><strong>{diagnostic.message}</strong>{diagnostic.hint && <small>{diagnostic.hint}</small>}</span>
              </button>
            </li>)}</ul>
          </div>
        ))}
      </section>

      <section className="review-section" aria-labelledby="preview-title">
        <div className="section-heading">
          <div><span className="eyebrow">Disposable Preview</span><h3 id="preview-title">Try events without persisting state</h3></div>
          {!preview ? <button className="button primary" type="button" disabled={previewBusy}
            onClick={() => void startPreview()} data-testid="start-preview">Start preview</button>
            : <button className="button quiet" type="button" disabled={previewBusy}
              onClick={() => void closePreview()} data-testid="close-preview">Close and discard</button>}
        </div>
        {previewMessage && <p className="inline-message" role="status" data-testid="preview-message">{previewMessage}</p>}
        {preview && <div className="preview-workspace" data-testid="preview-workspace">
          <div className="preview-status">
            <span><strong>Active situation</strong>{preview.activeStatePath.join(" → ") || "None"}</span>
            <span><strong>Expires</strong>{new Date(preview.expiresAt).toLocaleTimeString()}</span>
          </div>
          <div className="event-templates" aria-label="Event templates">
            {definition.interaction.supportedObservations.map((type) => <button className="choice-chip" type="button" key={type}
              onClick={() => { const next = { ...event, type }; setEvent(next); setEventJson(JSON.stringify(next, null, 2)); }}>{type}</button>)}
          </div>
          <label className="advanced-toggle"><input type="checkbox" checked={advancedEvent}
            onChange={(change) => setAdvancedEvent(change.target.checked)} /> Advanced event JSON</label>
          {advancedEvent ? <label className="field-stack">Event JSON<textarea value={eventJson}
            onChange={(change) => setEventJson(change.target.value)} data-testid="preview-event-json" /></label> : <>
            <label className="field-stack">Event type<input value={event.type}
              onChange={(change) => setEvent({ ...event, type: change.target.value })} data-testid="preview-event-type" /></label>
            <label className="field-stack">Payload<textarea value={event.payload ?? ""}
              onChange={(change) => setEvent({ ...event, payload: change.target.value })} data-testid="preview-event-payload" /></label>
          </>}
          <div className="button-row">
            <button className="button secondary" type="button" disabled={previewBusy} onClick={() => void sendEvent()}
              data-testid="send-preview-event">Send event</button>
            <button className="button secondary" type="button" disabled={previewBusy}
              onClick={() => void previewOperation(async () => updatePreview(await generatePreviewBehaviour(preview.id, adminToken, request)))}
              data-testid="generate-preview">Generate behaviour</button>
            <button className="button quiet" type="button" disabled={previewBusy}
              onClick={() => void previewOperation(async () => updatePreview(await resetDefinitionPreview(preview.id, adminToken, request)))}
              data-testid="reset-preview">Reset preview</button>
          </div>
          <PreviewTranscript preview={preview} />
        </div>}
      </section>

      <details className="review-section technical-details" open>
        <summary>Technical details</summary>
        <p>Canonical JSON edits replace the same V2 projection only after local parsing and backend structural validation.</p>
        <label className="field-stack">Canonical definition JSON<textarea className="json-editor" value={jsonDraft}
          onChange={(change) => setJsonDraft(change.target.value)} spellCheck={false} data-testid="canonical-json-editor" /></label>
        <div className="button-row"><button className="button secondary" type="button" disabled={jsonApplying}
          onClick={() => void applyJson()} data-testid="apply-canonical-json">Apply JSON to V2 projection</button></div>
        {jsonMessage && <p className="inline-message" role="status" data-testid="json-message">{jsonMessage}</p>}
        <h4>Composed prompt previews</h4>
        {promptState === "loading" && <p aria-busy="true">Composing prompts…</p>}
        {promptState === "error" && <p role="alert">{promptMessage}</p>}
        {promptState === "ready" && prompts.length === 0 && <p className="empty-copy">No typed prompts are present.</p>}
        {prompts.map((prompt) => <article className="prompt-preview" key={prompt.pointer}>
          <div><strong>{prompt.label}</strong><code>{prompt.pointer}</code></div>
          <pre>{prompt.composed}</pre>
        </article>)}
      </details>

      <section className="review-section" aria-labelledby="publication-title">
        <div className="section-heading"><div><span className="eyebrow">Revision lifecycle</span><h3 id="publication-title">Publication and reuse</h3></div></div>
        <p>Saving stays a draft action. Publishing makes this revision immutable; activation separately changes only which revision new instances receive.</p>
        <div className="button-row lifecycle-buttons">
          <button className="button primary" type="button" disabled={!canPublish || lifecycleBusy}
            onClick={() => void publish()} data-testid="publish-revision">Publish revision</button>
          <button className="button secondary" type="button" disabled={!canActivate || lifecycleBusy}
            onClick={() => void activate()} data-testid="activate-revision">Activate for new instances</button>
          <button className="button secondary" type="button" disabled={!persisted || lifecycleBusy}
            onClick={() => void exportRevision()} data-testid="export-revision">Export canonical JSON</button>
          <button className="button danger" type="button" disabled={!canArchive || lifecycleBusy}
            onClick={() => void archive()} data-testid="archive-revision">Archive revision</button>
        </div>
        <div className="clone-form">
          <label>Clone target key<input value={cloneKey} onChange={(change) => setCloneKey(change.target.value)} /></label>
          <label>Target revision<input type="number" min="1" value={cloneRevision}
            onChange={(change) => setCloneRevision(Number(change.target.value))} /></label>
          <button className="button secondary" type="button" disabled={!persisted || lifecycleBusy || !cloneKey.trim() || cloneRevision < 1}
            onClick={() => void clone()} data-testid="clone-revision">Clone to editable draft</button>
        </div>
        {lifecycleMessage && <p className="inline-message" role="status" data-testid="lifecycle-message">{lifecycleMessage}</p>}
      </section>
    </div>
  );
}

function PreviewTranscript({ preview }: { preview: PreviewSnapshot }) {
  return <div className="preview-transcript" data-testid="preview-transcript">
    <h4>Transcript</h4>
    <ol>{preview.transcript.map((operation) => <li key={operation.sequence}>
      <div><span className="operation-kind">{operation.kind}</span><span>#{operation.sequence}</span></div>
      {operation.input && <p><strong>{operation.input.type}</strong>{operation.input.payload ? ` · ${operation.input.payload}` : ""}</p>}
      <p>Active: {operation.activeStatePath.join(" → ") || "none"}</p>
      {operation.acceptedTransitionIds.length > 0 && <p>Accepted moves: {operation.acceptedTransitionIds.join(", ")}</p>}
      {Object.keys(operation.storageChanges).length > 0 && <pre>{JSON.stringify(operation.storageChanges, null, 2)}</pre>}
      {operation.behaviour && <pre>{JSON.stringify(operation.behaviour, null, 2)}</pre>}
      {operation.diagnostics.map((diagnostic) => <p className="operation-error" key={diagnostic.code}>{diagnostic.message}</p>)}
    </li>)}</ol>
    <details><summary>Current storage</summary><pre>{JSON.stringify(preview.storage, null, 2)}</pre></details>
  </div>;
}

function messageFor(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback;
}

export type { ApplyResult };
