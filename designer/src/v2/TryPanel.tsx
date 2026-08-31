import { useEffect, useState } from "react";
import {
  DesignerApiError,
  executeVerificationScenario,
  type DefinitionDiagnostic,
  type RequestFunction,
  type ScenarioExecutionResult,
} from "../api/designerApi";
import { isJsonObject, type AgentDefinitionV1, type JsonValue } from "../model/agentDefinition";
import { capabilityOption, OBSERVATION_CAPABILITIES } from "./authoringCatalog";
import {
  addBehaviourFragment,
  addScenario,
  addScenarioEvent,
  deleteBehaviourFragment,
  deleteScenario,
  deleteScenarioEvent,
  expectedSituationId,
  moveScenario,
  moveScenarioEvent,
  parseJsonValue,
  removeScenarioStorage,
  replaceScenarioEvent,
  scenarioViews,
  setExpectedSituation,
  setScenarioStorage,
  updateBehaviourFragment,
  updateScenario,
  updateScenarioEvent,
  type ScenarioEventView,
  type ScenarioView,
} from "./scenarioModel";
import type { DataItemProjection, DesignerV2Projection } from "./projection";

interface TryPanelProps {
  projection: DesignerV2Projection;
  readOnly: boolean;
  adminToken: string;
  request: RequestFunction;
  onChange: (definition: AgentDefinitionV1) => void;
  onDiagnostics: (diagnostics: DefinitionDiagnostic[]) => void;
}

interface StoredResult {
  fingerprint: string;
  result: ScenarioExecutionResult;
}

export function TryPanel({ projection, readOnly, adminToken, request, onChange, onDiagnostics }: TryPanelProps) {
  const scenarios = scenarioViews(projection);
  const [results, setResults] = useState<Record<number, StoredResult>>({});
  const [running, setRunning] = useState<number | null>(null);
  const [message, setMessage] = useState("");
  const change = (next: DesignerV2Projection) => onChange(next.source);

  const run = async (scenario: ScenarioView) => {
    setRunning(scenario.scenarioIndex);
    setMessage("");
    try {
      const result = await executeVerificationScenario(projection.source, scenario.scenarioIndex, adminToken, request);
      setResults((current) => ({ ...current, [scenario.scenarioIndex]: {
        fingerprint: scenarioFingerprint(scenario), result,
      } }));
      setMessage(result.discarded
        ? "Scenario finished through the production runtime and its disposable session was discarded."
        : "Scenario finished.");
    } catch (error) {
      if (error instanceof DesignerApiError && error.diagnostics.length > 0) onDiagnostics(error.diagnostics);
      setMessage(error instanceof Error ? error.message : "The scenario could not run.");
    } finally {
      setRunning(null);
    }
  };

  return <div className="v2-authoring try-authoring" data-testid="try-authoring">
    <section className="authoring-section try-intro">
      <div className="section-heading"><div><h3>Try concrete examples</h3>
        <p>Describe Given / When / Expect examples, then run them through an isolated production compiler and runtime.</p></div>
        <span className="item-count">{count(scenarios.length, "scenario")}</span></div>
      <div className="try-contract-grid"><div><strong>Given</strong><span>Seed and starting data</span></div>
        <div><strong>When</strong><span>Ordered events</span></div><div><strong>Expect</strong><span>Situation, data, and behaviour</span></div></div>
      <p className="scope-note">Runs use deterministic inputs and return trace evidence only. They never publish a revision or create production history.</p>
    </section>

    {message && <p className="inline-message" role="status" data-testid="scenario-message">{message}</p>}
    {scenarios.length === 0 && <section className="authoring-section empty-scenarios">
      <h3>No verification scenarios yet</h3><p>Add one example to make an expected interaction executable and reviewable.</p>
    </section>}
    <div className="scenario-list">{scenarios.map((scenario) => {
      const stored = results[scenario.scenarioIndex];
      const stale = stored && stored.fingerprint !== scenarioFingerprint(scenario);
      return <ScenarioCard key={scenario.pointer} projection={projection} scenario={scenario} readOnly={readOnly}
        running={running === scenario.scenarioIndex} result={stored?.result ?? null} stale={Boolean(stale)}
        onRun={() => void run(scenario)} onClearResult={() => setResults((current) => {
          const next = { ...current }; delete next[scenario.scenarioIndex]; return next;
        })} onChange={change} />;
    })}</div>
    <button className="button primary" type="button" disabled={readOnly}
      onClick={() => change(addScenario(projection))}>Add scenario</button>
  </div>;
}

function ScenarioCard({ projection, scenario, readOnly, running, result, stale, onRun, onClearResult, onChange }: {
  projection: DesignerV2Projection; scenario: ScenarioView; readOnly: boolean; running: boolean;
  result: ScenarioExecutionResult | null; stale: boolean; onRun: () => void; onClearResult: () => void;
  onChange: (projection: DesignerV2Projection) => void;
}) {
  const initialStorage = Object.entries(scenario.initialStorage);
  const expectedStorage = isJsonObject(scenario.expected.storage) ? Object.entries(scenario.expected.storage) : [];
  const fragments = Array.isArray(scenario.expected.behaviourFragments)
    ? scenario.expected.behaviourFragments as JsonValue[] : [];
  const expectedSituation = expectedSituationId(projection, scenario);
  return <article className="scenario-card" id={`try-scenario-${scenario.scenarioIndex}`} tabIndex={-1}
    data-testid={`scenario-card-${scenario.scenarioIndex}`}>
    <div className="card-title-row"><div><span className="eyebrow">Verification scenario {scenario.scenarioIndex + 1}</span>
      <input className="scenario-name-input" aria-label={`Scenario ${scenario.scenarioIndex + 1} name`}
        value={scenario.name} disabled={readOnly} onChange={(event) => onChange(updateScenario(
          projection, scenario.scenarioIndex, { name: event.target.value },
        ))} /></div><div className="priority-controls">
      <button type="button" aria-label="Move scenario earlier" disabled={readOnly || scenario.scenarioIndex === 0}
        onClick={() => onChange(moveScenario(projection, scenario.scenarioIndex, -1))}>↑</button>
      <button type="button" aria-label="Move scenario later" disabled={readOnly
        || scenario.scenarioIndex === projection.verification.scenarios.length - 1}
        onClick={() => onChange(moveScenario(projection, scenario.scenarioIndex, 1))}>↓</button></div></div>
    <label className="field-stack"><span>What this example verifies (optional)</span><textarea value={scenario.description}
      disabled={readOnly} onChange={(event) => onChange(updateScenario(projection, scenario.scenarioIndex,
        { description: event.target.value }))} /></label>

    <section className="scenario-phase given-phase" id={`try-scenario-${scenario.scenarioIndex}-given`} tabIndex={-1}>
      <div className="minor-heading"><div><span className="eyebrow">Given</span><h4>Starting conditions</h4>
        <p>A seed makes registered choice initialization repeatable. Starting data overrides the generated initial value for this run only.</p></div></div>
      <label className="field-stack seed-field"><span>Initializer seed (optional)</span><input type="number"
        value={scenario.initializerSeed ?? ""} disabled={readOnly} placeholder="0"
        onChange={(event) => onChange(updateScenario(projection, scenario.scenarioIndex, {
          initializerSeed: event.target.value === "" ? null : Number(event.target.value),
        }))} /></label>
      <StorageExpectationEditor projection={projection} scenarioIndex={scenario.scenarioIndex}
        entries={initialStorage} field="initialStorage" label="Starting data" readOnly={readOnly} onChange={onChange} />
    </section>

    <section className="scenario-phase when-phase">
      <div className="minor-heading"><div><span className="eyebrow">When</span><h4>Events happen in this order</h4>
        <p>Choose a declared observation template or inspect an event as Advanced JSON.</p></div>
        <span className="item-count">{count(scenario.events.length, "event")}</span></div>
      <div className="event-template-list" aria-label="Event templates">{projection.capabilities.observations.map((type) =>
        <button className="choice-chip" type="button" disabled={readOnly} key={type}
          onClick={() => onChange(addScenarioEvent(projection, scenario.scenarioIndex, type))}>
          {capabilityOption(type, OBSERVATION_CAPABILITIES)?.label ?? type}</button>)}</div>
      {projection.capabilities.observations.length === 0 && <p className="empty-copy">Declare an observation capability to use guided templates.</p>}
      <div className="scenario-event-list">{scenario.events.map((event) => <ScenarioEventCard key={event.eventIndex}
        projection={projection} scenario={scenario} event={event} readOnly={readOnly} onChange={onChange} />)}</div>
    </section>

    <section className="scenario-phase expect-phase" id={`try-scenario-${scenario.scenarioIndex}-expect`} tabIndex={-1}>
      <div className="minor-heading"><div><span className="eyebrow">Expect</span><h4>Observable result</h4>
        <p>Assertions compare only runtime state, data, and emitted behaviour—not hidden model reasoning.</p></div></div>
      <label className="field-stack"><span>Active situation after all events (optional)</span><select
        value={expectedSituation} disabled={readOnly} onChange={(event) => onChange(setExpectedSituation(
          projection, scenario.scenarioIndex, event.target.value || null,
        ))}><option value="">Do not assert a situation</option>{projection.source.states.filter((state) => state.kind !== "composite")
          .map((state) => <option value={state.id} key={state.id}>{state.name}{state.kind === "final" ? " (finished)" : ""}</option>)}</select></label>
      <StorageExpectationEditor projection={projection} scenarioIndex={scenario.scenarioIndex}
        entries={expectedStorage} field="expected" label="Expected data" readOnly={readOnly} onChange={onChange} />
      <div className="behaviour-expectations"><div className="minor-heading"><div><h5>Expected behaviour fragments</h5>
        <p>Each JSON fragment must occur in at least one emitted behaviour.</p></div></div>
        {fragments.map((fragment, index) => <JsonValueEditor key={index} label={`Behaviour fragment ${index + 1}`}
          value={fragment} readOnly={readOnly} onApply={(value) => onChange(updateBehaviourFragment(
            projection, scenario.scenarioIndex, index, value,
          ))} onRemove={() => onChange(deleteBehaviourFragment(projection, scenario.scenarioIndex, index))} />)}
        <button className="button secondary" type="button" disabled={readOnly} onClick={() => onChange(addBehaviourFragment(
          projection, scenario.scenarioIndex, defaultBehaviourFragment(projection),
        ))}>Add behaviour expectation</button></div>
    </section>

    <div className="scenario-actions"><button className="button primary" type="button" disabled={running || !scenario.name.trim()}
      onClick={onRun} data-testid={`run-scenario-${scenario.scenarioIndex}`}>{running ? "Running…" : "Run scenario"}</button>
      <button className="button danger" type="button" disabled={readOnly} onClick={() => onChange(deleteScenario(
        projection, scenario.scenarioIndex,
      ))}>Remove scenario</button></div>
    {result && <ScenarioResultView result={result} stale={stale} onClear={onClearResult} />}
  </article>;
}

function ScenarioEventCard({ projection, scenario, event, readOnly, onChange }: {
  projection: DesignerV2Projection; scenario: ScenarioView; event: ScenarioEventView; readOnly: boolean;
  onChange: (projection: DesignerV2Projection) => void;
}) {
  return <article className="scenario-event-card" id={`try-scenario-${scenario.scenarioIndex}-event-${event.eventIndex}`}
    tabIndex={-1}><div className="card-title-row"><strong>Event {event.eventIndex + 1}</strong><div className="priority-controls">
      <button type="button" aria-label="Move event earlier" disabled={readOnly || event.eventIndex === 0}
        onClick={() => onChange(moveScenarioEvent(projection, scenario.scenarioIndex, event.eventIndex, -1))}>↑</button>
      <button type="button" aria-label="Move event later" disabled={readOnly || event.eventIndex === scenario.events.length - 1}
        onClick={() => onChange(moveScenarioEvent(projection, scenario.scenarioIndex, event.eventIndex, 1))}>↓</button>
      <button className="button danger compact-button" type="button" disabled={readOnly}
        onClick={() => onChange(deleteScenarioEvent(projection, scenario.scenarioIndex, event.eventIndex))}>Remove</button></div></div>
    <div className="scenario-event-fields"><label className="field-stack"><span>Event type</span><select value={event.type}
      disabled={readOnly} onChange={(change) => onChange(updateScenarioEvent(projection, scenario.scenarioIndex,
        event.eventIndex, { type: change.target.value }))}>
      {!projection.capabilities.observations.includes(event.type) && <option value={event.type}>{event.type || "Missing type"}</option>}
      {projection.capabilities.observations.map((type) => <option value={type} key={type}>
        {capabilityOption(type, OBSERVATION_CAPABILITIES)?.label ?? type}</option>)}</select></label>
      <label className="field-stack wide-field"><span>Event payload</span><textarea value={event.payload} disabled={readOnly}
        onChange={(change) => onChange(updateScenarioEvent(projection, scenario.scenarioIndex,
          event.eventIndex, { payload: change.target.value }))} /></label></div>
    <AdvancedEventEditor projection={projection} scenario={scenario} event={event} readOnly={readOnly} onChange={onChange} />
  </article>;
}

function AdvancedEventEditor({ projection, scenario, event, readOnly, onChange }: {
  projection: DesignerV2Projection; scenario: ScenarioView; event: ScenarioEventView; readOnly: boolean;
  onChange: (projection: DesignerV2Projection) => void;
}) {
  const [draft, setDraft] = useState(() => JSON.stringify(event.source, null, 2));
  const [message, setMessage] = useState("");
  useEffect(() => setDraft(JSON.stringify(event.source, null, 2)), [event.source]);
  const apply = () => {
    const parsed = parseJsonValue(draft);
    if (!parsed.ok || !isJsonObject(parsed.value)) {
      setMessage(parsed.ok ? "Event JSON must be an object." : parsed.message);
      return;
    }
    setMessage("");
    onChange(replaceScenarioEvent(projection, scenario.scenarioIndex, event.eventIndex, parsed.value));
  };
  return <details className="technical-details advanced-event"><summary>Advanced event JSON</summary>
    <textarea aria-label={`Event ${event.eventIndex + 1} JSON`} value={draft} disabled={readOnly}
      onChange={(change) => setDraft(change.target.value)} />
    <button className="button secondary" type="button" disabled={readOnly} onClick={apply}>Apply event JSON</button>
    {message && <p className="operation-error" role="alert">{message}</p>}
  </details>;
}

function StorageExpectationEditor({ projection, scenarioIndex, entries, field, label, readOnly, onChange }: {
  projection: DesignerV2Projection; scenarioIndex: number; entries: Array<[string, unknown]>;
  field: "initialStorage" | "expected"; label: string; readOnly: boolean;
  onChange: (projection: DesignerV2Projection) => void;
}) {
  const used = new Set(entries.map(([key]) => key));
  const available = projection.data.items.filter((item) => !used.has(item.key));
  return <div className="scenario-storage"><div className="minor-heading"><div><h5>{label}</h5></div>
    <span className="item-count">{count(entries.length, "value")}</span></div>
    {entries.map(([key, value]) => <JsonValueEditor key={key} label={dataLabel(projection, key)}
      value={value as JsonValue} readOnly={readOnly} onApply={(next) => onChange(setScenarioStorage(
        projection, scenarioIndex, field, key, next,
      ))} onRemove={() => onChange(removeScenarioStorage(projection, scenarioIndex, field, key))} />)}
    {available.length > 0 && <AddStorageValue projection={projection} scenarioIndex={scenarioIndex}
      field={field} available={available} readOnly={readOnly} onChange={onChange} />}
  </div>;
}

function AddStorageValue({ projection, scenarioIndex, field, available, readOnly, onChange }: {
  projection: DesignerV2Projection; scenarioIndex: number; field: "initialStorage" | "expected";
  available: DataItemProjection[]; readOnly: boolean; onChange: (projection: DesignerV2Projection) => void;
}) {
  const [key, setKey] = useState(available[0]?.key ?? "");
  const selected = available.find((item) => item.key === key) ?? available[0];
  return <div className="inline-create-row scenario-storage-add"><label className="field-stack"><span>Add data assertion</span>
    <select value={selected?.key ?? ""} disabled={readOnly} onChange={(event) => setKey(event.target.value)}>
      {available.map((item) => <option value={item.key} key={item.key}>{dataLabel(projection, item.key)}</option>)}</select></label>
    <button className="button secondary" type="button" disabled={readOnly || !selected}
      onClick={() => selected && onChange(setScenarioStorage(projection, scenarioIndex, field,
        selected.key, defaultStorageValue(selected)))}>Add data value</button></div>;
}

function JsonValueEditor({ label, value, readOnly, onApply, onRemove }: {
  label: string; value: JsonValue; readOnly: boolean; onApply: (value: JsonValue) => void; onRemove: () => void;
}) {
  const [draft, setDraft] = useState(() => JSON.stringify(value, null, 2));
  const [message, setMessage] = useState("");
  useEffect(() => setDraft(JSON.stringify(value, null, 2)), [value]);
  const apply = () => {
    const parsed = parseJsonValue(draft);
    if (!parsed.ok) { setMessage(parsed.message); return; }
    setMessage(""); onApply(parsed.value);
  };
  return <article className="json-value-card"><div className="card-title-row"><strong>{label}</strong>
    <button className="button danger compact-button" type="button" disabled={readOnly} onClick={onRemove}>Remove</button></div>
    <textarea aria-label={`${label} JSON value`} value={draft} disabled={readOnly} onChange={(event) => setDraft(event.target.value)} />
    <button className="button secondary compact-button" type="button" disabled={readOnly} onClick={apply}>Apply JSON value</button>
    {message && <p className="operation-error" role="alert">{message}</p>}
  </article>;
}

function ScenarioResultView({ result, stale, onClear }: {
  result: ScenarioExecutionResult; stale: boolean; onClear: () => void;
}) {
  return <section className={`scenario-result ${result.passed ? "passed" : "failed"}`}
    data-testid={`scenario-result-${result.scenarioIndex}`} aria-live="polite">
    <div className="card-title-row"><div><span className="eyebrow">Last isolated run</span>
      <h4>{result.passed ? "All expectations passed" : "Some expectations failed"}</h4>
      <p>{result.discarded ? "The disposable runtime session has been discarded." : "The run is complete."}</p></div>
      <button className="button quiet" type="button" onClick={onClear}>Clear result</button></div>
    {stale && <p className="scope-note">This result predates the latest scenario edits. Run it again for current evidence.</p>}
    {result.expectations.length === 0 && <p className="empty-copy">No explicit expectations were defined.</p>}
    <ul className="expectation-results">{result.expectations.map((expectation) => <li className={expectation.passed ? "passed" : "failed"}
      key={expectation.id}><span aria-hidden="true">{expectation.passed ? "✓" : "×"}</span><div><strong>{expectation.label}</strong>
        <small>{expectation.passed ? "Matched" : "Did not match"}</small></div></li>)}</ul>
    <dl className="scenario-trace-summary"><div><dt>Active path</dt><dd>{result.activeStatePath.join(" → ") || "None"}</dd></div>
      <div><dt>Accepted rules</dt><dd>{result.acceptedTransitionIds.join(", ") || "None"}</dd></div>
      <div><dt>Emitted modalities</dt><dd>{result.emittedModalities.join(", ") || "None"}</dd></div>
      <div><dt>Data changes</dt><dd>{count(result.storageChanges.length, "change")}</dd></div></dl>
    <details className="scenario-why"><summary>{result.passed ? "Why did this happen?" : "Why did this not happen?"}</summary>
      <ol>{result.expectations.map((expectation) => <li key={expectation.id}><strong>{expectation.label}</strong>
        <p>{expectation.explanation}</p>{!expectation.passed && <details><summary>Expected and observed values</summary>
          <pre>{JSON.stringify({ expected: expectation.expected, actual: expectation.actual }, null, 2)}</pre></details>}</li>)}</ol>
      {result.diagnostics.map((diagnostic) => <p className="operation-error" key={diagnostic.code}>{diagnostic.message}</p>)}</details>
    <details className="technical-details"><summary>Advanced deterministic trace</summary>
      <pre>{JSON.stringify(result.transcript, null, 2)}</pre></details>
  </section>;
}

function defaultStorageValue(item: DataItemProjection): JsonValue {
  if (Object.hasOwn(item.declaration, "initialValue")) return item.declaration.initialValue as JsonValue;
  const schema = isJsonObject(item.declaration.valueSchema) ? item.declaration.valueSchema : {};
  if (Array.isArray(schema.enum) && schema.enum.length) return schema.enum[0] as JsonValue;
  if (schema.type === "integer" || schema.type === "number") return 0;
  if (schema.type === "boolean") return false;
  if (schema.type === "array") return [];
  if (schema.type === "object") return {};
  return "";
}

function defaultBehaviourFragment(projection: DesignerV2Projection): JsonValue {
  const modality = projection.capabilities.behaviourModalities[0] ?? "speech";
  if (modality === "speech") return { speech: "Expected phrase" };
  const [group, child] = modality.split(".");
  return child ? { [group]: { [child]: {} } } : { [group]: {} };
}

function dataLabel(projection: DesignerV2Projection, key: string): string {
  const item = projection.data.items.find((candidate) => candidate.key === key);
  const description = item && typeof item.declaration.description === "string" ? item.declaration.description : key;
  return `${description} (${key})`;
}

function scenarioFingerprint(scenario: ScenarioView) {
  return JSON.stringify(scenario.source);
}

function count(value: number, singular: string): string {
  return `${value} ${value === 1 ? singular : `${singular}s`}`;
}
