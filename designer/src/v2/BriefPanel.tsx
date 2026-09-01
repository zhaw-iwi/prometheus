import { useState } from "react";
import { fetchPromptPreviews, type PromptPreview, type RequestFunction } from "../api/designerApi";
import type { AgentDefinitionV1 } from "../model/agentDefinition";
import { GUIDANCE_EXAMPLES, GUIDANCE_INTENTS, guidanceIntent } from "./authoringCatalog";
import {
  addGuidanceIntent,
  adoptGuidanceExample,
  briefIssues,
  defaultAgentGuidanceTarget,
  moveGuidanceSection,
  parseStableIds,
  removeGuidanceSection,
  suggestDefinitionKey,
  updateGuidanceSection,
} from "./briefModel";
import { serializedDefinition, type DesignerV2Projection } from "./projection";
import { updateCapabilities, updateIdentity } from "./transforms";

interface BriefPanelProps {
  projection: DesignerV2Projection;
  persisted: boolean;
  keyConfirmed: boolean;
  readOnly: boolean;
  adminToken: string;
  request: RequestFunction;
  onKeyConfirmedChange: (confirmed: boolean) => void;
  onChange: (definition: AgentDefinitionV1) => void;
}

export function BriefPanel({ projection, persisted, keyConfirmed, readOnly, adminToken, request,
  onKeyConfirmedChange, onChange }: BriefPanelProps) {
  const [newIntent, setNewIntent] = useState("objective");
  const [previews, setPreviews] = useState<PromptPreview[] | null>(null);
  const [previewFingerprint, setPreviewFingerprint] = useState("");
  const [previewMessage, setPreviewMessage] = useState("");
  const issues = briefIssues(projection, persisted || keyConfirmed);
  const agentGuidance = projection.guidance.filter((item) => item.scope === "agent");
  const situationGuidanceCount = projection.guidance.length - agentGuidance.length;

  const change = (next: DesignerV2Projection) => onChange(next.source);
  const metadata = projection.identity.metadata;
  const updateMetadata = (patch: Partial<typeof metadata>) => change(updateIdentity(projection, { metadata: patch }));
  const updateKey = (key: string) => {
    onKeyConfirmedChange(false);
    change(updateIdentity(projection, { key }));
  };
  const loadPreviews = async () => {
    setPreviewMessage("Loading composed prompt…");
    try {
      setPreviews(await fetchPromptPreviews(projection.source, adminToken, request));
      setPreviewFingerprint(serializedDefinition(projection.source));
      setPreviewMessage("");
    } catch (error) {
      setPreviewMessage(error instanceof Error ? error.message : "The composed prompt could not be loaded.");
    }
  };
  const previewStale = previewFingerprint !== "" && previewFingerprint !== serializedDefinition(projection.source);

  return <div className="v2-authoring" data-testid="brief-authoring">
    <section className="authoring-section identity-section">
      <div className="section-heading"><div><h3>Name and purpose</h3><p>Describe the agent in language its future authors and operators will understand.</p></div></div>
      <div className="authoring-fields">
        <Field label="Agent name" error={issues.displayName} id="brief-display-name">
          <input id="brief-display-name" value={metadata.displayName} disabled={readOnly}
            onChange={(event) => updateMetadata({ displayName: event.target.value })} />
        </Field>
        <Field label="Primary language" error={issues.languageCode} id="brief-language">
          <input id="brief-language" value={metadata.languageCode ?? ""} disabled={readOnly}
            placeholder="en, de, de-CH, or blank" onChange={(event) => updateMetadata({ languageCode: event.target.value || null })} />
        </Field>
        <Field label="Purpose, audience, and setting" error={issues.description} id="brief-description" wide>
          <textarea id="brief-description" value={metadata.description} disabled={readOnly}
            placeholder="Why does this agent exist, whom does it help, and where will it be used?"
            onChange={(event) => updateMetadata({ description: event.target.value })} />
        </Field>
      </div>
    </section>

    <section className="authoring-section key-section">
      <div className="section-heading"><div><h3>Stable key</h3><p>This permanent identifier is used by API clients and stored revisions.</p></div>
        {!persisted && !readOnly && <button className="button secondary" type="button"
          onClick={() => updateKey(suggestDefinitionKey(metadata.displayName))}>Suggest from name</button>}</div>
      <div className="key-confirmation-row">
        <Field label="Stable key" error={issues.key} id="brief-key">
          <input id="brief-key" value={projection.identity.key} disabled={readOnly || persisted}
            placeholder="designer.welcome_guide" onChange={(event) => updateKey(event.target.value)} />
        </Field>
        {!persisted && <button className="button primary" type="button" data-testid="confirm-stable-key"
          disabled={readOnly || keyConfirmed || Boolean(issues.key && issues.key !== "Confirm the stable key before the first save.")}
          onClick={() => onKeyConfirmedChange(true)}>{keyConfirmed ? "Key confirmed" : "Confirm stable key"}</button>}
        {persisted && <span className="status-badge">Saved key</span>}
      </div>
    </section>

    <section className="authoring-section guidance-section" id="brief-guidance" tabIndex={-1}>
      <div className="section-heading"><div><h3>Agent-wide guidance</h3><p>Ordered guidance applies throughout the agent. Boundaries guide prompt-based behavior; they are not guaranteed enforcement.</p></div>
        <span className="item-count">{agentGuidance.length} guidance card{agentGuidance.length === 1 ? "" : "s"}</span></div>
      {situationGuidanceCount > 0 && <div className="scope-note" data-testid="situation-guidance-preserved">
        {situationGuidanceCount} situation guidance card{situationGuidanceCount === 1 ? " remains" : "s remain"} separate and unchanged for Interaction.
      </div>}
      {agentGuidance.length === 0 && <p className="empty-copy">No agent-wide guidance yet.</p>}
      <div className="guidance-list">
        {agentGuidance.map((section) => {
          const target = { stateId: section.stateId, promptField: section.promptField };
          const siblings = agentGuidance.filter((item) => item.stateId === section.stateId && item.promptField === section.promptField);
          const localIndex = siblings.findIndex((item) => item.sectionIndex === section.sectionIndex);
          const intent = guidanceIntent(section.kind);
          return <article className="guidance-card" key={`${section.pointer}:${section.id}`} data-testid={`guidance-card-${section.id}`}>
            <div className="card-title-row"><div><span className="eyebrow">{intent ? "Guidance" : "Additional guidance"}</span>
              <h4>{intent?.label ?? "Additional guidance"}</h4><p>{intent?.description ?? "Imported guidance with an unrecognized intent; its identity and order are preserved."}</p></div>
              <div className="order-actions" aria-label={`Order ${intent?.label ?? section.id}`}>
                <button type="button" disabled={readOnly || localIndex === 0} aria-label="Move guidance earlier"
                  onClick={() => change(moveGuidanceSection(projection, target, section.sectionIndex, -1))}>↑</button>
                <button type="button" disabled={readOnly || localIndex === siblings.length - 1} aria-label="Move guidance later"
                  onClick={() => change(moveGuidanceSection(projection, target, section.sectionIndex, 1))}>↓</button>
              </div></div>
            <textarea aria-label={`${intent?.label ?? "Additional guidance"} content`} value={section.content} disabled={readOnly}
              onChange={(event) => change(updateGuidanceSection(projection, target, section.sectionIndex, { content: event.target.value }))} />
            <div className="card-footer-actions">
              <details className="technical-details"><summary>Technical details</summary>
                <div className="authoring-fields two-columns compact-fields">
                  <Field label="Section ID" id={`guidance-id-${section.id}`}><input id={`guidance-id-${section.id}`}
                    value={section.id} disabled={readOnly}
                    onChange={(event) => change(updateGuidanceSection(projection, target, section.sectionIndex, { id: event.target.value }))} /></Field>
                  <Field label="Section kind" id={`guidance-kind-${section.id}`}><input id={`guidance-kind-${section.id}`}
                    value={section.kind} disabled={readOnly}
                    onChange={(event) => change(updateGuidanceSection(projection, target, section.sectionIndex, { kind: event.target.value }))} /></Field>
                </div><code>{section.promptField} · agent scope</code>
              </details>
              <button className="button danger" type="button" disabled={readOnly}
                onClick={() => change(removeGuidanceSection(projection, target, section.sectionIndex))}>Remove</button>
            </div>
          </article>;
        })}
      </div>
      <div className="add-guidance-row">
        <label htmlFor="new-guidance-intent">Add guidance</label>
        <select id="new-guidance-intent" value={newIntent} disabled={readOnly}
          onChange={(event) => setNewIntent(event.target.value)}>
          {GUIDANCE_INTENTS.map((intent) => <option value={intent.kind} key={intent.kind}>{intent.label}</option>)}
        </select>
        <button className="button primary" type="button" disabled={readOnly || !defaultAgentGuidanceTarget(projection, guidanceIntent(newIntent)?.promptField)}
          onClick={() => change(addGuidanceIntent(projection, newIntent))}>Add card</button>
      </div>
      {!defaultAgentGuidanceTarget(projection) && <p className="inline-message">This imported definition has no agent-wide prompt scope. Its situation behavior remains editable in Interaction without changing its topology.</p>}
    </section>

    <section className="authoring-section example-section">
      <div className="section-heading"><div><h3>Examples</h3><p>Viewing an example does not change the draft. Adoption is always explicit.</p></div></div>
      {GUIDANCE_EXAMPLES.map((example) => <details className="example-card" key={example.id} data-testid={`guidance-example-${example.id}`}>
        <summary>{example.title}</summary><p>{example.description}</p>
        <ol>{example.sections.map((section) => <li key={section.kind}><strong>{guidanceIntent(section.kind)?.label}</strong><span>{section.content}</span></li>)}</ol>
        <button className="button secondary" type="button" disabled={readOnly || !defaultAgentGuidanceTarget(projection)}
          onClick={() => change(adoptGuidanceExample(projection, example.id))}>Use as starting point</button>
      </details>)}
    </section>

    <section className="authoring-section">
      <details className="technical-details"><summary>Classification and delivery details</summary>
        <div className="authoring-fields two-columns compact-fields">
          <Field label="Category" error={issues.categoryPath} id="brief-category"><input id="brief-category"
            value={metadata.categoryPath} disabled={readOnly} onChange={(event) => updateMetadata({ categoryPath: event.target.value })} /></Field>
          <Field label="Search tags" error={issues.tags} id="brief-tags"><input id="brief-tags"
            value={metadata.tags.join(", ")} disabled={readOnly} placeholder="guide, public_space"
            onChange={(event) => updateMetadata({ tags: parseStableIds(event.target.value) })} /></Field>
          <Field label="Deployment profiles" id="brief-profiles" wide><input id="brief-profiles"
            value={projection.capabilities.profileTags.join(", ")} disabled={readOnly}
            onChange={(event) => change(updateCapabilities(projection, { profileTags: parseStableIds(event.target.value) }))} /></Field>
        </div>
      </details>
    </section>

    <section className="authoring-section prompt-preview-section">
      <div className="section-heading"><div><h3>Composed prompt preview</h3><p>The backend composes this read-only view through the production prompt boundary.</p></div>
        <button className="button secondary" type="button" onClick={() => void loadPreviews()}>Refresh preview</button></div>
      {previewStale && <p className="validation-state stale">The shown preview predates the latest edits. Refresh it before relying on the text.</p>}
      {previewMessage && <p className="inline-message" role="status">{previewMessage}</p>}
      {previews?.length === 0 && <p className="empty-copy">This definition has no composed prompt roles.</p>}
      {previews?.map((preview) => <article className="prompt-preview" key={preview.pointer}><div><strong>{preview.label}</strong><code>{preview.pointer}</code></div><pre>{preview.composed}</pre></article>)}
    </section>
  </div>;
}

function Field({ label, error, id, wide = false, children }: {
  label: string; error?: string; id: string; wide?: boolean; children: React.ReactNode;
}) {
  return <label className={`field-stack${wide ? " wide-field" : ""}`} htmlFor={id}><span>{label}</span>{children}
    {error && <small className="field-error" role="alert">{error}</small>}</label>;
}
