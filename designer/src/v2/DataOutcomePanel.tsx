import { useState } from "react";
import type { AgentDefinitionV1, JsonObject, JsonValue } from "../model/agentDefinition";
import {
  addDataItem,
  addGuidedOutcome,
  attachOutcomeExtraction,
  convertOutcomeToGuided,
  dataReferences,
  defaultDataField,
  defaultOutcomeField,
  deleteDataItem,
  detachOutcomeExtraction,
  fixedInitialValue,
  formatEditorValue,
  guidedOutcomeFields,
  guidedFieldsFromSchema,
  operationOwnedData,
  outcomeAttachments,
  outcomeConversionPreview,
  outcomeMode,
  parseEditorValue,
  removeDataInitialization,
  renameDataItem,
  replaceGuidedOutcomeFields,
  replaceStructuredDataFields,
  replaceTypedChoices,
  schemaType,
  setDataEnumValues,
  setDataType,
  setFixedInitialValue,
  typedChoiceSetup,
  updateCustomExtractionSection,
  updateDataDescription,
  updateDataRequirements,
  useTypedChoices,
  type GuidedField,
  type GuidedFieldType,
  type DataValueType,
} from "./dataModel";
import type { DataItemProjection, DataRole, DesignerV2Projection } from "./projection";

interface DataOutcomePanelProps {
  projection: DesignerV2Projection;
  readOnly: boolean;
  onChange: (definition: AgentDefinitionV1) => void;
  onGoToInteraction: () => void;
}

const ROLE_COPY: Record<DataRole, { title: string; description: string }> = {
  "starting-context": {
    title: "Starting context",
    description: "Fixed or randomly selected information available when an interaction begins.",
  },
  "working-data": {
    title: "Working data",
    description: "Internal values used by registered operations while the agent runs.",
  },
  "learned-information": {
    title: "Learned information",
    description: "Values recorded or updated by interaction effects.",
  },
  "outcome-report": {
    title: "Outcome report",
    description: "Caller-visible structured results extracted when the interaction finishes.",
  },
};

export function DataOutcomePanel({ projection, readOnly, onChange, onGoToInteraction }: DataOutcomePanelProps) {
  const change = (next: DesignerV2Projection) => onChange(next.source);
  const owned = operationOwnedData(projection);
  const ownedKeys = new Set(owned.flatMap((group) => group.itemKeys));

  return <div className="v2-authoring data-outcome-authoring" data-testid="data-outcome-authoring">
    <section className="authoring-section data-intro">
      <div className="section-heading"><div><h3>Data through the interaction lifecycle</h3>
        <p>Define context and results by their purpose. Complete schemas and registered bindings remain inspectable under Advanced.</p></div></div>
      <div className="data-role-legend">{Object.entries(ROLE_COPY).map(([role, copy]) =>
        <div key={role}><strong>{copy.title}</strong><span>{copy.description}</span></div>)}</div>
    </section>

    {(["starting-context", "working-data", "learned-information"] as const).map((role) => {
      const items = projection.data.items.filter((item) => item.role === role && !ownedKeys.has(item.key));
      return <section className="authoring-section data-role-section" key={role} data-testid={`data-role-${role}`}>
        <div className="section-heading"><div><span className="eyebrow">{ROLE_COPY[role].title}</span>
          <h3>{ROLE_COPY[role].title}</h3><p>{ROLE_COPY[role].description}</p></div>
          <span className="item-count">{count(items.length, "value")}</span></div>
        {role === "working-data" && owned.map((group) => <OperationDataCard key={group.group}
          projection={projection} itemKeys={group.itemKeys} />)}
        {items.length === 0 && (role !== "working-data" || owned.length === 0)
          && <p className="empty-copy">No {ROLE_COPY[role].title.toLowerCase()} defined.</p>}
        <div className="data-card-list">{items.map((item) => <DataItemCard key={item.pointer} projection={projection}
          item={item} readOnly={readOnly} onChange={change} />)}</div>
        <AddDataRow projection={projection} role={role} readOnly={readOnly} onChange={change} />
      </section>;
    })}

    <section className="authoring-section outcome-section" data-testid="data-role-outcome-report">
      <div className="section-heading"><div><span className="eyebrow">Outcome report</span><h3>Outcome reports</h3>
        <p>Build caller-visible fields or keep an imported report losslessly as Custom until you explicitly convert it.</p></div>
        <span className="item-count">{count(projection.outcomes.items.length, "report")}</span></div>
      {projection.outcomes.items.length === 0 && <p className="empty-copy">No outcome report defined.</p>}
      <div className="data-card-list">{projection.outcomes.items.map((item) => <OutcomeCard key={item.pointer}
        projection={projection} item={item} readOnly={readOnly} onChange={change}
        onGoToInteraction={onGoToInteraction} />)}</div>
      <AddOutcomeRow projection={projection} readOnly={readOnly} onChange={change} />
    </section>

    <details className="authoring-section technical-details data-document-details"><summary>Advanced data document</summary>
      <p>The canonical storage, initializer, and resource arrays remain the authoritative representation.</p>
      <dl className="technical-facts"><div><dt>Storage declarations</dt><dd>{projection.source.storage.length}</dd></div>
        <div><dt>Initializers</dt><dd>{projection.source.lifecycle.initializers.length}</dd></div>
        <div><dt>Resources</dt><dd>{projection.source.resources.length}</dd></div></dl>
    </details>
  </div>;
}

function DataItemCard({ projection, item, readOnly, onChange }: {
  projection: DesignerV2Projection;
  item: DataItemProjection;
  readOnly: boolean;
  onChange: (projection: DesignerV2Projection) => void;
}) {
  const [keyDraft, setKeyDraft] = useState(item.key);
  const declaration = item.declaration;
  const schema = isObject(declaration.valueSchema) ? declaration.valueSchema : { type: "string" };
  const type = schemaType(schema);
  const structuredFields = type === "object" ? guidedFieldsFromSchema(schema) : null;
  const references = dataReferences(projection.source, item.key);
  const choices = typedChoiceSetup(projection.source, item.key);
  const fixed = fixedInitialValue(projection.source, item);
  const starting = item.role === "starting-context";
  const description = typeof declaration.description === "string" ? declaration.description : "";
  const enumValues = Array.isArray(schema.enum) && schema.enum.every((value) => typeof value === "string")
    ? schema.enum as string[] : [];

  return <article className="data-item-card" id={`data-item-${item.key}`} tabIndex={-1} data-testid={`data-item-card-${item.key}`}>
    <div className="card-title-row"><div><span className="eyebrow">{ROLE_COPY[item.role].title}</span>
      <input className="data-name-input" aria-label={`${item.key} description`} value={description} disabled={readOnly}
        placeholder="Describe this value" onChange={(event) => onChange(updateDataDescription(
          projection, item.storageIndex, event.target.value,
        ))} /></div><span className="status-badge">{schemaLabel(schema)}</span></div>

    <div className="data-settings-grid">
      <label className="field-stack"><span>Data key</span><input value={keyDraft} disabled={readOnly || references.length > 0}
        onChange={(event) => setKeyDraft(event.target.value)}
        onBlur={() => onChange(renameDataItem(projection, item.storageIndex, keyDraft))} /></label>
      {type && <label className="field-stack"><span>Value type</span><select value={type} disabled={readOnly}
        onChange={(event) => onChange(setDataType(projection, item.storageIndex, event.target.value as DataValueType))}>
        <DataTypeOptions /></select></label>}
      <label className="check-row"><input type="checkbox" checked={declaration.required === true} disabled={readOnly}
        onChange={(event) => onChange(updateDataRequirements(projection, item.storageIndex, { required: event.target.checked }))} />
        <span>Required while the agent runs</span></label>
      <label className="field-stack"><span>On reset</span><select value={String(declaration.reset ?? "remove")} disabled={readOnly}
        onChange={(event) => onChange(updateDataRequirements(projection, item.storageIndex,
          { reset: event.target.value as "initial" | "preserve" | "remove" }))}>
        <option value="initial">Restore the starting value</option><option value="preserve">Keep the current value</option>
        <option value="remove">Remove the current value</option></select></label>
    </div>
    {type === "string" && <label className="field-stack enum-field"><span>Allowed choices (optional, comma-separated)</span>
      <input value={enumValues.join(", ")} disabled={readOnly}
        onChange={(event) => onChange(setDataEnumValues(projection, item.storageIndex, event.target.value.split(",")))} /></label>}
    {structuredFields && <section className="structured-schema-editor"><div className="minor-heading"><div>
      <h4>Structured fields</h4><p>These fields shape each value and any fixed or typed-choice starting values.</p></div>
      <span className="item-count">{count(structuredFields.length, "field")}</span></div>
      <div className="outcome-field-list">{structuredFields.map((field, index) => <OutcomeFieldRow key={field.key}
        field={field} index={index} total={structuredFields.length} readOnly={readOnly}
        requiredLabel="Required in every value" onCommit={(next) => onChange(replaceStructuredDataFields(
          projection, item.storageIndex, structuredFields.map((current, fieldIndex) => fieldIndex === index ? next : current),
        ))} onRemove={() => onChange(replaceStructuredDataFields(projection, item.storageIndex,
          structuredFields.filter((_, fieldIndex) => fieldIndex !== index)))} />)}</div>
      <button className="button secondary" type="button" disabled={readOnly}
        onClick={() => onChange(replaceStructuredDataFields(projection, item.storageIndex,
          [...structuredFields, defaultDataField(structuredFields.length)]))}>Add structured field</button>
    </section>}

    {starting && <section className="initialization-editor"><div className="minor-heading"><div><h4>Starting value</h4>
      <p>Use one fixed value or a deterministic selection from typed choices.</p></div></div>
      {choices ? <TypedChoicesEditor projection={projection} item={item} setup={choices} readOnly={readOnly} onChange={onChange} />
        : fixed !== undefined ? <ValueEditor label="Fixed starting value" value={fixed} schema={schema} readOnly={readOnly}
          onCommit={(value) => onChange(setFixedInitialValue(projection, item.storageIndex, value))} />
          : <p className="empty-copy">No starting-value setup.</p>}
      <div className="button-row data-mode-actions">
        {!choices && <button className="button secondary" type="button" disabled={readOnly}
          onClick={() => onChange(useTypedChoices(projection, item.storageIndex))}>Use typed choices</button>}
        {choices && <button className="button secondary" type="button" disabled={readOnly}
          onClick={() => onChange(setFixedInitialValue(projection, item.storageIndex, choices.values[0] ?? ""))}>Use first choice as fixed value</button>}
        <button className="button quiet" type="button" disabled={readOnly}
          onClick={() => onChange(removeDataInitialization(projection, item.storageIndex))}>Remove starting setup</button>
      </div>
    </section>}

    {references.length > 0 && <details className="reference-note"><summary>{count(references.length, "configured reference")}</summary>
      <ul>{references.map((reference) => <li key={reference.pointer}>{reference.label}</li>)}</ul>
      <p>Rename and delete are protected until these references are removed.</p></details>}
    <div className="card-footer-actions"><details className="technical-details"><summary>Advanced schema</summary>
      <pre>{JSON.stringify(schema, null, 2)}</pre></details>
      <button className="button danger" type="button" disabled={readOnly || references.length > 0}
        onClick={() => onChange(deleteDataItem(projection, item.storageIndex))}>Remove value</button></div>
  </article>;
}

function TypedChoicesEditor({ projection, item, setup, readOnly, onChange }: {
  projection: DesignerV2Projection;
  item: DataItemProjection;
  setup: NonNullable<ReturnType<typeof typedChoiceSetup>>;
  readOnly: boolean;
  onChange: (projection: DesignerV2Projection) => void;
}) {
  const schema = isObject(item.declaration.valueSchema) ? item.declaration.valueSchema : { type: "string" };
  const updateChoice = (index: number, value: JsonValue) => {
    const values = [...setup.values];
    values[index] = value;
    onChange(replaceTypedChoices(projection, item.key, values));
  };
  return <div className="typed-choice-editor" data-testid={`typed-choices-${item.key}`}>
    <div className="minor-heading"><div><h5>Typed choices</h5><p>One choice is selected through the registered deterministic initializer.</p></div>
      <span className="item-count">{count(setup.values.length, "choice")}</span></div>
    <div className="choice-value-list">{setup.values.map((value, index) => <article className="choice-value-card" key={index}>
      <div className="card-title-row"><strong>Choice {index + 1}</strong><button className="button danger compact-button"
        type="button" disabled={readOnly || setup.values.length <= 1} onClick={() => onChange(replaceTypedChoices(
          projection, item.key, setup.values.filter((_, valueIndex) => valueIndex !== index),
        ))}>Remove</button></div>
      <StructuredValueEditor value={value} schema={schema} readOnly={readOnly}
        onCommit={(next) => updateChoice(index, next)} />
    </article>)}</div>
    <button className="button secondary" type="button" disabled={readOnly}
      onClick={() => onChange(replaceTypedChoices(projection, item.key, [...setup.values, defaultForSchema(schema)]))}>Add choice</button>
    <details className="technical-details"><summary>Initialization details</summary>
      <p>{setup.source === "resource" ? "Registered typed-choice resource" : "Inline registered choices"}</p></details>
  </div>;
}

function OutcomeCard({ projection, item, readOnly, onChange, onGoToInteraction }: {
  projection: DesignerV2Projection;
  item: DataItemProjection;
  readOnly: boolean;
  onChange: (projection: DesignerV2Projection) => void;
  onGoToInteraction: () => void;
}) {
  const mode = outcomeMode(projection.source, item);
  return mode === "guided"
    ? <GuidedOutcomeCard projection={projection} item={item} readOnly={readOnly} onChange={onChange}
      onGoToInteraction={onGoToInteraction} />
    : <CustomOutcomeCard projection={projection} item={item} readOnly={readOnly} onChange={onChange}
      onGoToInteraction={onGoToInteraction} />;
}

function GuidedOutcomeCard({ projection, item, readOnly, onChange, onGoToInteraction }: {
  projection: DesignerV2Projection; item: DataItemProjection; readOnly: boolean;
  onChange: (projection: DesignerV2Projection) => void; onGoToInteraction: () => void;
}) {
  const fields = guidedOutcomeFields(item);
  const attachments = outcomeAttachments(projection.source, item.key);
  const references = dataReferences(projection.source, item.key);
  const updateField = (index: number, next: GuidedField) => {
    const updated = [...fields]; updated[index] = next;
    onChange(replaceGuidedOutcomeFields(projection, item.storageIndex, updated));
  };
  return <article className="data-item-card outcome-card" id={`data-item-${item.key}`} tabIndex={-1}
    data-testid={`guided-outcome-${item.key}`}>
    <div className="card-title-row"><div><span className="eyebrow">Guided outcome report</span><h4>{itemDescription(item)}</h4>
      <p>{count(fields.length, "caller-visible field")} · attached to {count(attachments.length, "finish rule")}</p></div>
      <span className="status-badge">Guided fields</span></div>
    <div className="outcome-field-list">{fields.map((field, index) => <OutcomeFieldRow key={field.key}
      field={field} index={index} total={fields.length} readOnly={readOnly} onCommit={(next) => updateField(index, next)}
      onRemove={() => onChange(replaceGuidedOutcomeFields(projection, item.storageIndex,
        fields.filter((_, fieldIndex) => fieldIndex !== index)))} />)}</div>
    <button className="button secondary" type="button" disabled={readOnly}
      onClick={() => onChange(replaceGuidedOutcomeFields(projection, item.storageIndex,
        [...fields, defaultOutcomeField(fields.length)]))}>Add outcome field</button>
    <OutcomeAttachmentActions projection={projection} item={item} attachments={attachments} readOnly={readOnly}
      onChange={onChange} onGoToInteraction={onGoToInteraction} />
    <div className="card-footer-actions"><details className="technical-details"><summary>Advanced generated schema</summary>
      <pre>{JSON.stringify(item.declaration.valueSchema, null, 2)}</pre></details>
      <button className="button danger" type="button" disabled={readOnly || references.length > 0}
        onClick={() => onChange(deleteDataItem(projection, item.storageIndex))}>Remove report</button></div>
  </article>;
}

function CustomOutcomeCard({ projection, item, readOnly, onChange, onGoToInteraction }: {
  projection: DesignerV2Projection; item: DataItemProjection; readOnly: boolean;
  onChange: (projection: DesignerV2Projection) => void; onGoToInteraction: () => void;
}) {
  const [converting, setConverting] = useState(false);
  const [previewing, setPreviewing] = useState(false);
  const [fields, setFields] = useState<GuidedField[]>([defaultOutcomeField()]);
  const attachments = outcomeAttachments(projection.source, item.key);
  const references = dataReferences(projection.source, item.key);
  const preview = outcomeConversionPreview(projection, item.storageIndex, fields);
  return <article className="data-item-card outcome-card custom-outcome" id={`data-item-${item.key}`} tabIndex={-1}
    data-testid={`custom-outcome-${item.key}`}>
    <div className="card-title-row"><div><span className="eyebrow">Custom outcome report</span><h4>{itemDescription(item)}</h4>
      <p>Its imported schema and extraction guidance remain unchanged until conversion is explicitly applied.</p></div>
      <span className="status-badge">Custom</span></div>
    {attachments.length === 0 && <p className="scope-note">This report is not attached to a finish rule.</p>}
    <div className="custom-extraction-list">{attachments.map((attachment) => {
      const sections = extractionSections(attachment.envelope);
      return <section className="custom-extraction-card" key={`${attachment.transitionIndex}:${attachment.actionIndex}`}>
        <div className="minor-heading"><div><h5>Extraction on finish</h5><p>Rule {attachment.ruleId}</p></div></div>
        {sections.map((section, sectionIndex) => <label className="field-stack" key={`${section.id}:${sectionIndex}`}>
          <span>{customSectionLabel(sectionIndex, sections.length)}</span><textarea value={section.content} disabled={readOnly}
            onChange={(event) => onChange(updateCustomExtractionSection(projection, attachment.transitionIndex,
              attachment.actionIndex, sectionIndex, event.target.value))} /></label>)}
      </section>;
    })}</div>
    {!converting && <button className="button secondary" type="button" disabled={readOnly}
      onClick={() => setConverting(true)}>Convert to guided fields…</button>}
    {converting && <section className="conversion-panel" data-testid={`outcome-conversion-${item.key}`}>
      <h5>Prepare explicit conversion</h5><p>Define the replacement fields. Nothing changes until you review and apply the canonical fragment diff.</p>
      {fields.map((field, index) => <OutcomeFieldRow key={`${field.key}:${index}`} field={field} index={index}
        total={fields.length} readOnly={false} onCommit={(next) => setFields(fields.map((current, currentIndex) =>
          currentIndex === index ? next : current))} onRemove={() => setFields(fields.filter((_, currentIndex) => currentIndex !== index))} />)}
      <div className="button-row"><button className="button secondary" type="button"
        onClick={() => setFields([...fields, defaultOutcomeField(fields.length)])}>Add field</button>
        <button className="button secondary" type="button" onClick={() => setPreviewing(true)}>Preview canonical change</button>
        <button className="button quiet" type="button" onClick={() => { setConverting(false); setPreviewing(false); }}>Cancel</button></div>
      {previewing && preview && <div className="conversion-diff" data-testid="outcome-conversion-diff">
        <div><strong>Before</strong><pre>{JSON.stringify(preview.before, null, 2)}</pre></div>
        <div><strong>After</strong><pre>{JSON.stringify(preview.after, null, 2)}</pre></div>
        <p>{count(preview.changedRuleIds.length, "finish rule")} will receive the generated extraction contract.</p>
        <button className="button primary" type="button" onClick={() => onChange(convertOutcomeToGuided(
          projection, item.storageIndex, fields,
        ))}>Apply conversion</button></div>}
    </section>}
    <OutcomeAttachmentActions projection={projection} item={item} attachments={attachments} readOnly={readOnly}
      onChange={onChange} onGoToInteraction={onGoToInteraction} />
    <div className="card-footer-actions"><details className="technical-details"><summary>Advanced imported schema</summary>
      <pre>{JSON.stringify(item.declaration.valueSchema, null, 2)}</pre></details>
      <button className="button danger" type="button" disabled={readOnly || references.length > 0}
        onClick={() => onChange(deleteDataItem(projection, item.storageIndex))}>Remove report</button></div>
  </article>;
}

function OutcomeAttachmentActions({ projection, item, attachments, readOnly, onChange, onGoToInteraction }: {
  projection: DesignerV2Projection; item: DataItemProjection; attachments: ReturnType<typeof outcomeAttachments>;
  readOnly: boolean; onChange: (projection: DesignerV2Projection) => void; onGoToInteraction: () => void;
}) {
  const finishRules = projection.rules.filter((rule) => rule.continuation === "finish");
  return <div className="attachment-actions">
    {finishRules.length === 0 && <div className="scope-note">Add a Finish rule in Interaction before attaching extraction.
      <button type="button" onClick={onGoToInteraction}>Open Interaction</button></div>}
    {attachments.length === 0 && finishRules.length > 0 && <button className="button secondary" type="button" disabled={readOnly}
      onClick={() => onChange(attachOutcomeExtraction(projection, item.storageIndex))}>Attach to finish rules</button>}
    {attachments.length > 0 && <button className="button quiet" type="button" disabled={readOnly}
      onClick={() => onChange(detachOutcomeExtraction(projection, item.key))}>Detach extraction from finish rules</button>}
  </div>;
}

function OutcomeFieldRow({ field, index, total, readOnly, onCommit, onRemove, requiredLabel = "Required in every report" }: {
  field: GuidedField; index: number; total: number; readOnly: boolean;
  onCommit: (field: GuidedField) => void; onRemove: () => void; requiredLabel?: string;
}) {
  const [draft, setDraft] = useState(field);
  return <article className="outcome-field-card"><div className="card-title-row"><strong>Field {index + 1}</strong>
    <button className="button danger compact-button" type="button" disabled={readOnly || total <= 1} onClick={onRemove}>Remove</button></div>
    <div className="outcome-field-grid">
      <label className="field-stack"><span>Field label</span><input value={draft.label} disabled={readOnly}
        onChange={(event) => setDraft({ ...draft, label: event.target.value })} onBlur={() => onCommit(draft)} /></label>
      <label className="field-stack"><span>Field key</span><input value={draft.key} disabled={readOnly}
        onChange={(event) => setDraft({ ...draft, key: event.target.value })} onBlur={() => onCommit(draft)} /></label>
      <label className="field-stack"><span>Type</span><select value={draft.type} disabled={readOnly}
        onChange={(event) => { const next = { ...draft, type: event.target.value as GuidedFieldType }; setDraft(next); onCommit(next); }}>
        <GuidedTypeOptions /></select></label>
      <label className="check-row"><input type="checkbox" checked={draft.required} disabled={readOnly}
        onChange={(event) => { const next = { ...draft, required: event.target.checked }; setDraft(next); onCommit(next); }} />
        <span>{requiredLabel}</span></label>
      {draft.type === "string" && <label className="field-stack wide-field"><span>Allowed values (optional)</span>
        <input value={draft.enumValues.join(", ")} disabled={readOnly}
          onChange={(event) => setDraft({ ...draft, enumValues: event.target.value.split(",").map((value) => value.trim()).filter(Boolean) })}
          onBlur={() => onCommit(draft)} /></label>}
    </div>
  </article>;
}

function AddDataRow({ projection, role, readOnly, onChange }: {
  projection: DesignerV2Projection; role: Exclude<DataRole, "outcome-report">; readOnly: boolean;
  onChange: (projection: DesignerV2Projection) => void;
}) {
  const [key, setKey] = useState("");
  return <div className="inline-create-row data-add-row"><label className="field-stack"><span>New {ROLE_COPY[role].title.toLowerCase()} key</span>
    <input value={key} disabled={readOnly} placeholder={role === "starting-context" ? "visitorContext" : "progressCount"}
      onChange={(event) => setKey(event.target.value)} /></label>
    <button className="button primary" type="button" disabled={readOnly || !key.trim()}
      onClick={() => { onChange(addDataItem(projection, role, key)); setKey(""); }}>Add value</button></div>;
}

function AddOutcomeRow({ projection, readOnly, onChange }: {
  projection: DesignerV2Projection; readOnly: boolean; onChange: (projection: DesignerV2Projection) => void;
}) {
  const [key, setKey] = useState("outcome");
  return <div className="inline-create-row data-add-row"><label className="field-stack"><span>New outcome report key</span>
    <input value={key} disabled={readOnly} onChange={(event) => setKey(event.target.value)} /></label>
    <button className="button primary" type="button" disabled={readOnly || !key.trim()}
      onClick={() => { onChange(addGuidedOutcome(projection, key)); setKey("outcome"); }}>Add outcome report</button></div>;
}

function OperationDataCard({ projection, itemKeys }: { projection: DesignerV2Projection; itemKeys: string[] }) {
  return <article className="operation-data-card" data-testid="operation-data-rock-scissor-paper">
    <div className="card-title-row"><div><span className="eyebrow">Registered operation data</span><h4>Rock, scissor, paper round data</h4>
      <p>The installed operation owns these typed values and their bindings. They are preserved as one inspectable pack.</p></div>
      <span className="status-badge">{count(itemKeys.length, "owned value")}</span></div>
    <ul>{itemKeys.map((key) => <li key={key}><strong>{itemDescription(projection.data.items.find((item) => item.key === key)!)}</strong></li>)}</ul>
    <details className="technical-details"><summary>Advanced owned schemas</summary>{itemKeys.map((key) => {
      const item = projection.data.items.find((candidate) => candidate.key === key)!;
      return <div key={key}><code>{key}</code><pre>{JSON.stringify(item.declaration.valueSchema, null, 2)}</pre></div>;
    })}</details>
  </article>;
}

function ValueEditor({ label, value, schema, readOnly, onCommit }: {
  label: string; value: JsonValue; schema: JsonObject; readOnly: boolean; onCommit: (value: JsonValue) => void;
}) {
  if (schema.type === "object") return <StructuredValueEditor value={value} schema={schema} readOnly={readOnly} onCommit={onCommit} />;
  return <ScalarValueEditor label={label} value={value} schema={schema} readOnly={readOnly} onCommit={onCommit} />;
}

function StructuredValueEditor({ value, schema, readOnly, onCommit }: {
  value: JsonValue; schema: JsonObject; readOnly: boolean; onCommit: (value: JsonValue) => void;
}) {
  if (schema.type !== "object" || !isObject(schema.properties) || !isObject(value)) {
    return <ScalarValueEditor label="Value" value={value} schema={schema} readOnly={readOnly} onCommit={onCommit} />;
  }
  return <div className="structured-value-grid">{Object.entries(schema.properties).map(([key, childSchema]) => {
    const normalizedSchema = isObject(childSchema) ? childSchema : { type: "string" };
    return <ScalarValueEditor key={key} label={schemaTitle(normalizedSchema, key)} value={(value[key] ?? "") as JsonValue}
      schema={normalizedSchema} readOnly={readOnly} onCommit={(next) => onCommit({ ...value, [key]: next } as JsonValue)} />;
  })}</div>;
}

function ScalarValueEditor({ label, value, schema, readOnly, onCommit }: {
  label: string; value: JsonValue; schema: JsonObject; readOnly: boolean; onCommit: (value: JsonValue) => void;
}) {
  const [draft, setDraft] = useState(formatEditorValue(value, schema));
  if (schema.type === "boolean") return <label className="check-row"><input type="checkbox" checked={value === true} disabled={readOnly}
    onChange={(event) => onCommit(event.target.checked)} /><span>{label}</span></label>;
  if (Array.isArray(schema.enum)) return <label className="field-stack"><span>{label}</span><select value={String(value ?? "")}
    disabled={readOnly} onChange={(event) => onCommit(event.target.value)}>{schema.enum.map((option) =>
      <option value={String(option)} key={String(option)}>{String(option)}</option>)}</select></label>;
  return <label className="field-stack"><span>{label}</span><input value={draft} disabled={readOnly}
    inputMode={schema.type === "integer" || schema.type === "number" ? "numeric" : undefined}
    onChange={(event) => {
      setDraft(event.target.value);
      if (schema.type === "string") onCommit(event.target.value);
    }} onBlur={() => { if (schema.type !== "string") onCommit(parseEditorValue(draft, schema)); }} /></label>;
}

function GuidedTypeOptions() {
  return <><option value="string">Text</option><option value="integer">Whole number</option>
    <option value="number">Number</option><option value="boolean">Yes / no</option>
    <option value="string-list">List of text</option></>;
}

function DataTypeOptions() {
  return <><GuidedTypeOptions /><option value="object">Structured record</option></>;
}

function extractionSections(envelope: { config: JsonObject }) {
  const prompt = envelope.config.extractionPrompt;
  if (!isObject(prompt) || !Array.isArray(prompt.sections)) return [];
  return prompt.sections.flatMap((section) => isObject(section) && typeof section.id === "string"
    && typeof section.kind === "string" && typeof section.content === "string"
    ? [{ id: section.id, kind: section.kind, content: section.content }] : []);
}

function customSectionLabel(index: number, total: number) {
  if (total === 1) return "Extraction guidance";
  if (index === 0) return "Instruction";
  if (index === 1) return "Structure or example";
  return "Rules and constraints";
}

function schemaLabel(schema: JsonObject): string {
  if (schema.type === "object") return "Structured object";
  if (schema.type === "array") return "List";
  if (Array.isArray(schema.enum)) return "Choice";
  return schema.type === "integer" ? "Whole number" : schema.type === "number" ? "Number"
    : schema.type === "boolean" ? "Yes / no" : "Text";
}

function schemaTitle(schema: JsonObject, key: string) {
  return typeof schema.title === "string" ? schema.title : humanize(key);
}

function itemDescription(item: DataItemProjection) {
  return typeof item.declaration.description === "string" ? item.declaration.description : humanize(item.key);
}

function defaultForSchema(schema: JsonObject): JsonValue {
  if (schema.type === "object" && isObject(schema.properties)) return Object.fromEntries(Object.entries(schema.properties)
    .map(([key, value]) => [key, isObject(value) ? defaultForSchema(value) : ""]));
  if (schema.type === "array") return [];
  if (schema.type === "boolean") return false;
  if (schema.type === "integer" || schema.type === "number") return 0;
  if (Array.isArray(schema.enum) && schema.enum.length) return schema.enum[0] as JsonValue;
  return "";
}

function isObject(value: unknown): value is JsonObject {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function humanize(value: string) {
  return value.replace(/([a-z])([A-Z])/g, "$1 $2").split(/[._-]+/).filter(Boolean)
    .map((word) => `${word[0]?.toUpperCase() ?? ""}${word.slice(1)}`).join(" ");
}

function count(value: number, singular: string): string {
  return `${value} ${value === 1 ? singular : `${singular}s`}`;
}
