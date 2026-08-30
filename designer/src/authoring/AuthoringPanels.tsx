import type { ComponentDefinition } from "../api/designerApi";
import { MODALITY_OPTIONS, OBSERVATION_OPTIONS, PROMPT_FIELDS, type PromptFieldDefinition } from "./authoringCatalog";
import {
  adoptStrategy,
  type AuthoringForm,
  isStrategyCompatible,
  policyStrategies,
  promptContent,
  suggestDefinitionKey,
  updatePromptSection,
} from "./editorModel";

interface PanelProps {
  form: AuthoringForm;
  onChange: (form: AuthoringForm) => void;
}

interface PurposePanelProps extends PanelProps {
  isNew: boolean;
  keyConfirmed: boolean;
  onKeyConfirmed: (confirmed: boolean) => void;
}

export function PurposePanel({ form, onChange, isNew, keyConfirmed, onKeyConfirmed }: PurposePanelProps) {
  const suggestion = suggestDefinitionKey(form.categoryPath, form.displayName);
  return (
    <div className="authoring-panel" data-testid="purpose-authoring-panel">
      <section className="form-section">
        <div className="section-heading">
          <div><h3>Identity and goal</h3><p>Use ordinary catalog language. Technical lifecycle fields stay separate.</p></div>
          <span className="schema-chip">Schema v1</span>
        </div>
        <div className="field-grid two-columns">
          <TextField id="purpose-display-name" label="What should this agent be called?" value={form.displayName}
            required onValue={(displayName) => onChange({ ...form, displayName })} />
          <TextField id="purpose-category" label="Catalog category" value={form.categoryPath} required
            help="A stable dotted path such as designer.coaching."
            onValue={(categoryPath) => onChange({ ...form, categoryPath })} />
          <TextField id="purpose-description" label="What is it intended to accomplish?" value={form.description}
            required multiline className="wide-field"
            onValue={(description) => onChange({ ...form, description })} />
          <TextField id="purpose-language" label="Interaction language" value={form.languageCode}
            help="ISO-style code such as en, de, or de-CH. Empty means deliberately multilingual."
            onValue={(languageCode) => onChange({ ...form, languageCode })} />
          <TextField id="purpose-tags" label="Catalog tags" value={form.tags}
            help="Comma-separated stable tags."
            onValue={(tags) => onChange({ ...form, tags })} />
          <TextField id="purpose-profile-tags" label="Interaction profile tags" value={form.profileTags}
            help="Comma-separated tags exposed to compatible clients."
            onValue={(profileTags) => onChange({ ...form, profileTags })} />
        </div>
        <div className="stable-key-box">
          <div className="field-control">
            <label htmlFor="purpose-key">Stable definition key <span aria-hidden="true">*</span></label>
            <input id="purpose-key" value={form.key} disabled={!isNew}
              onChange={(event) => {
                onKeyConfirmed(false);
                onChange({ ...form, key: event.target.value });
              }} data-testid="purpose-key" />
            <small>{isNew ? "Published keys remain stable." : "The saved revision owns this key."}</small>
          </div>
          {isNew && (
            <div className="key-suggestion">
              <span>Suggestion</span><code>{suggestion}</code>
              <button className="button secondary compact-button" type="button"
                onClick={() => { onKeyConfirmed(false); onChange({ ...form, key: suggestion }); }}
                data-testid="use-key-suggestion">Use suggestion</button>
            </div>
          )}
          {isNew && (
            <label className="confirm-row" htmlFor="purpose-key-confirmed">
              <input id="purpose-key-confirmed" type="checkbox" checked={keyConfirmed}
                onChange={(event) => onKeyConfirmed(event.target.checked)} />
              I confirm this stable key for the first save.
            </label>
          )}
        </div>
      </section>
      <PromptFields stepId="purpose" form={form} onChange={onChange} />
    </div>
  );
}

export function SensingPanel({ form, onChange }: PanelProps) {
  const consumed = form.strategyKind === "prometheus.policy.prompt"
    ? form.supportedObservations
    : stringArray(form.strategyConfig.consumedObservations);
  return (
    <div className="authoring-panel" data-testid="sensing-authoring-panel">
      <section className="form-section">
        <div className="section-heading">
          <div><h3>What can this agent perceive?</h3><p>Select only signals that are meaningful for this design.</p></div>
          <span className="selection-count">{form.supportedObservations.length} selected</span>
        </div>
        <div className="capability-grid">
          {OBSERVATION_OPTIONS.map((option) => {
            const selected = form.supportedObservations.includes(option.id);
            return (
              <label className={`capability-card${selected ? " selected" : ""}`} key={option.id}>
                <input type="checkbox" checked={selected} data-testid={`observation-${option.id}`}
                  onChange={() => onChange({
                    ...form,
                    supportedObservations: toggle(form.supportedObservations, option.id),
                  })} />
                <span className="capability-family">{option.family}</span>
                <strong>{option.label}</strong>
                <span>{option.description}</span>
                {selected && <small>{consumed.includes(option.id) ? "Used by the main strategy" : "Selected; no current strategy use"}</small>}
              </label>
            );
          })}
        </div>
      </section>
      <PromptFields stepId="sensing" form={form} onChange={onChange} />
    </div>
  );
}

interface BehaviourPanelProps extends PanelProps {
  components: ComponentDefinition[];
}

export function BehaviourPanel({ form, onChange, components }: BehaviourPanelProps) {
  const strategies = policyStrategies(components);
  const selectedStrategy = strategies.find((component) => component.kind === form.strategyKind
    && component.version === form.strategyVersion);
  const visibleStrategies = strategies.filter((component) => isStrategyCompatible(
    component,
    form.supportedBehaviourModalities,
  ) || component.kind === form.strategyKind);
  return (
    <div className="authoring-panel" data-testid="behaviour-authoring-panel">
      <section className="form-section">
        <div className="section-heading">
          <div><h3>What can this agent emit?</h3><p>The interaction profile tells clients which outputs to expect.</p></div>
          <span className="selection-count">{form.supportedBehaviourModalities.length} selected</span>
        </div>
        <div className="capability-grid compact-grid">
          {MODALITY_OPTIONS.map((option) => {
            const selected = form.supportedBehaviourModalities.includes(option.id);
            return (
              <label className={`capability-card${selected ? " selected" : ""}`} key={option.id}>
                <input type="checkbox" checked={selected} data-testid={`modality-${option.id}`}
                  onChange={() => onChange({
                    ...form,
                    supportedBehaviourModalities: toggle(form.supportedBehaviourModalities, option.id),
                  })} />
                <span className="capability-family">{option.family}</span>
                <strong>{option.label}</strong><span>{option.description}</span>
              </label>
            );
          })}
        </div>
      </section>

      <section className="form-section">
        <div className="section-heading">
          <div><h3>Main response strategy</h3><p>Labels, help, defaults, and examples come from the registered backend component catalog.</p></div>
        </div>
        <div className="strategy-grid" data-testid="strategy-catalog">
          {visibleStrategies.map((component) => {
            const compatible = isStrategyCompatible(component, form.supportedBehaviourModalities);
            const selected = component.kind === form.strategyKind && component.version === form.strategyVersion;
            return (
              <article className={`strategy-card${selected ? " selected" : ""}${compatible ? "" : " incompatible"}`}
                key={`${component.kind}:${component.version}`}>
                <label>
                  <input type="radio" name="response-strategy" checked={selected} disabled={!compatible}
                    onChange={() => onChange(adoptStrategy(form, component))}
                    data-testid={`strategy-${component.kind}`} />
                  <span><strong>{component.label}</strong><small>{component.kind} · v{component.version}</small></span>
                </label>
                <p>{component.description}</p>
                {!compatible && <span className="compatibility-note">Does not emit every selected modality.</span>}
                {component.examples.length > 0 && (
                  <details><summary>View typed example</summary><pre>{JSON.stringify(component.examples[0], null, 2)}</pre></details>
                )}
              </article>
            );
          })}
        </div>
      </section>

      {form.strategyKind === "prometheus.policy.prompt" && (
        <PromptFields stepId="behaviour" form={form} onChange={onChange} />
      )}

      <aside className="behaviour-summary" aria-label="Behaviour summary" data-testid="behaviour-summary">
        <span className="eyebrow">Read-only summary</span>
        <strong>{selectedStrategy?.label ?? form.strategyKind}</strong>
        <p>{form.supportedBehaviourModalities.length > 0
          ? `May emit ${form.supportedBehaviourModalities.map(modalityLabel).join(", ")}.`
          : "No behaviour modality is declared yet."}</p>
      </aside>
    </div>
  );
}

function PromptFields({ stepId, form, onChange }: PanelProps & { stepId: "purpose" | "sensing" | "behaviour" }) {
  const fields = PROMPT_FIELDS.filter((field) => field.stepId === stepId);
  return (
    <section className="form-section prompt-guidance" data-testid={`${stepId}-prompt-guidance`}>
      <div className="section-heading">
        <div><h3>Prompt guidance</h3><p>Each answer remains an ordered typed section. Examples are inert until adopted.</p></div>
      </div>
      <div className="prompt-field-grid">
        {fields.map((field) => <PromptField key={field.id} field={field} form={form} onChange={onChange} />)}
      </div>
    </section>
  );
}

function PromptField({ field, form, onChange }: PanelProps & { field: PromptFieldDefinition }) {
  const value = promptContent(form, field.id);
  const inputId = `prompt-${field.id.replaceAll(".", "-")}`;
  const setValue = (content: string) => onChange({
    ...form,
    promptSections: updatePromptSection(form.promptSections, field.id, field.kind, content),
  });
  return (
    <article className="prompt-field">
      <label htmlFor={inputId}>{field.label}</label>
      <p>{field.help}</p>
      <textarea id={inputId} value={value} onChange={(event) => setValue(event.target.value)} rows={4}
        data-testid={inputId} />
      <div className="field-meta"><span>{value.length.toLocaleString()} characters</span><span>{field.kind}</span></div>
      <details data-testid={`example-${field.id}`}>
        <summary>View example</summary>
        <p>{field.example}</p>
        <button className="button secondary compact-button" type="button" onClick={() => setValue(field.example)}
          data-testid={`adopt-example-${field.id}`}>Use as starting point</button>
      </details>
    </article>
  );
}

function TextField({ id, label, value, onValue, help, required, multiline, className = "" }: {
  id: string;
  label: string;
  value: string;
  onValue: (value: string) => void;
  help?: string;
  required?: boolean;
  multiline?: boolean;
  className?: string;
}) {
  return (
    <div className={`field-control ${className}`}>
      <label htmlFor={id}>{label}{required && <span aria-hidden="true"> *</span>}</label>
      {multiline
        ? <textarea id={id} value={value} onChange={(event) => onValue(event.target.value)} rows={3} />
        : <input id={id} value={value} onChange={(event) => onValue(event.target.value)} />}
      {help && <small>{help}</small>}
    </div>
  );
}

function toggle(values: string[], value: string): string[] {
  return values.includes(value) ? values.filter((candidate) => candidate !== value) : [...values, value];
}

function stringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : [];
}

function modalityLabel(id: string): string {
  return MODALITY_OPTIONS.find((option) => option.id === id)?.label ?? id;
}
