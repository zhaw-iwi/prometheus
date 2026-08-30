import { useEffect, useState } from "react";
import type { ComponentDefinition } from "../api/designerApi";
import {
  cloneJson,
  type ComponentEnvelope,
  isJsonObject,
  type JsonObject,
} from "../model/agentDefinition";
import { envelopeFromComponent } from "./graphModel";

interface ComponentEnvelopeEditorProps {
  envelope: ComponentEnvelope | null;
  category: ComponentDefinition["category"];
  components: ComponentDefinition[];
  label: string;
  nullable?: boolean;
  onChange: (envelope: ComponentEnvelope | null) => void;
}

export function ComponentEnvelopeEditor({
  envelope,
  category,
  components,
  label,
  nullable = false,
  onChange,
}: ComponentEnvelopeEditorProps) {
  const choices = components.filter((component) => component.category === category);
  const selected = envelope
    ? choices.find((component) => component.kind === envelope.kind && component.version === envelope.version)
    : undefined;
  return (
    <section className="component-editor">
      <label>
        <span>{label}</span>
        <select value={envelope ? `${envelope.kind}:${envelope.version}` : ""}
          onChange={(event) => {
            if (!event.target.value) return onChange(null);
            const choice = choices.find((component) => `${component.kind}:${component.version}` === event.target.value);
            if (choice) onChange(envelopeFromComponent(choice));
          }}>
          {nullable && <option value="">None</option>}
          {!nullable && !envelope && <option value="">Choose a component</option>}
          {choices.map((component) => (
            <option value={`${component.kind}:${component.version}`} key={`${component.kind}:${component.version}`}>
              {component.label} (v{component.version})
            </option>
          ))}
        </select>
      </label>
      {envelope && (
        <details className="advanced-config">
          <summary>Advanced configuration</summary>
          <p>{selected?.description ?? envelope.kind}</p>
          <SchemaFields schema={selected?.configSchema ?? {}} config={envelope.config}
            onChange={(config) => onChange({ ...envelope, config })} />
          {selected?.examples[0] && (
            <details>
              <summary>View typed example</summary>
              <pre>{JSON.stringify(selected.examples[0], null, 2)}</pre>
              <button className="button secondary compact-button" type="button"
                onClick={() => onChange({ ...envelope, config: cloneJson(selected.examples[0]) })}>
                Use as starting point
              </button>
            </details>
          )}
        </details>
      )}
    </section>
  );
}

function SchemaFields({ schema, config, onChange }: {
  schema: JsonObject;
  config: JsonObject;
  onChange: (config: JsonObject) => void;
}) {
  const properties = isJsonObject(schema.properties) ? schema.properties : {};
  const required = new Set(Array.isArray(schema.required)
    ? schema.required.filter((item): item is string => typeof item === "string") : []);
  const entries = Object.entries(properties).filter((entry): entry is [string, JsonObject] => isJsonObject(entry[1]));
  if (entries.length === 0) return <p className="empty-config">This component has no configuration fields.</p>;
  return (
    <div className="schema-fields">
      {entries.map(([key, fieldSchema]) => (
        <SchemaField key={key} name={key} schema={fieldSchema} value={config[key]} required={required.has(key)}
          onValue={(value) => {
            const next = cloneJson(config);
            if (value === undefined) delete next[key];
            else next[key] = value;
            onChange(next);
          }} />
      ))}
    </div>
  );
}

function SchemaField({ name, schema, value, required, onValue }: {
  name: string;
  schema: JsonObject;
  value: unknown;
  required: boolean;
  onValue: (value: unknown) => void;
}) {
  const type = typeof schema.type === "string" ? schema.type : "object";
  const description = typeof schema.description === "string" ? schema.description : `${type} configuration`;
  const enumValues = Array.isArray(schema.enum) ? schema.enum.filter((item): item is string => typeof item === "string") : [];
  if (type === "boolean") {
    return (
      <label className="schema-checkbox">
        <input type="checkbox" checked={value === true} onChange={(event) => onValue(event.target.checked)} />
        <span><strong>{humanize(name)}{required ? " *" : ""}</strong><small>{description}</small></span>
      </label>
    );
  }
  if (type === "array") {
    const itemSchema = isJsonObject(schema.items) ? schema.items : {};
    if (itemSchema.type !== "string") {
      return <JsonConfigField name={name} value={value ?? []} required={required} description={description} onValue={onValue} />;
    }
    return <StringArrayField name={name} value={value} required={required} description={description} onValue={onValue} />;
  }
  if (type === "string" && enumValues.length > 0) {
    return (
      <label><span>{humanize(name)}{required ? " *" : ""}</span>
        <select value={typeof value === "string" ? value : ""} onChange={(event) => onValue(event.target.value)}>
          {!required && <option value="">Not set</option>}
          {enumValues.map((option) => <option key={option}>{option}</option>)}
        </select><small>{description}</small>
      </label>
    );
  }
  if (type === "string" || type === "integer" || type === "number") {
    return (
      <label><span>{humanize(name)}{required ? " *" : ""}</span>
        <input type={type === "string" ? "text" : "number"} value={typeof value === "string" || typeof value === "number" ? value : ""}
          onChange={(event) => onValue(type === "string" ? event.target.value
            : event.target.value === "" ? undefined : Number(event.target.value))} />
        <small>{description}</small>
      </label>
    );
  }
  return <JsonConfigField name={name} value={value} required={required} description={description} onValue={onValue} />;
}

function StringArrayField({ name, value, required, description, onValue }: {
  name: string;
  value: unknown;
  required: boolean;
  description: string;
  onValue: (value: unknown) => void;
}) {
  const canonical = (Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : []).join(", ");
  const [draft, setDraft] = useState(canonical);
  useEffect(() => setDraft(canonical), [canonical]);
  return (
    <label><span>{humanize(name)}{required ? " *" : ""}</span>
      <input value={draft} onChange={(event) => setDraft(event.target.value)}
        onBlur={() => onValue(draft.split(",").map((item) => item.trim()).filter(Boolean))} />
      <small>{description}; comma-separated.</small>
    </label>
  );
}

function JsonConfigField({ name, value, required, description, onValue }: {
  name: string;
  value: unknown;
  required: boolean;
  description: string;
  onValue: (value: unknown) => void;
}) {
  const canonical = JSON.stringify(value ?? {}, null, 2);
  const [draft, setDraft] = useState(canonical);
  const [error, setError] = useState("");
  useEffect(() => setDraft(canonical), [canonical]);
  return (
    <label><span>{humanize(name)}{required ? " *" : ""}</span>
      <textarea value={draft} rows={5} onChange={(event) => setDraft(event.target.value)}
        onBlur={() => {
          try {
            onValue(JSON.parse(draft));
            setError("");
          } catch {
            setError("Enter valid JSON for this structured field.");
          }
        }} />
      <small className={error ? "field-error" : ""}>{error || description}</small>
    </label>
  );
}

function humanize(value: string): string {
  return value.replace(/([a-z])([A-Z])/g, "$1 $2").replaceAll("_", " ")
    .replace(/^./, (first) => first.toUpperCase());
}
