import type { AgentDefinitionV1, ComponentEnvelope } from "../model/agentDefinition";
import { advancedDefinitionAudit } from "./reviewModel";

interface AdvancedDefinitionViewsProps {
  definition: AgentDefinitionV1;
}

export function AdvancedDefinitionViews({ definition }: AdvancedDefinitionViewsProps) {
  const audit = advancedDefinitionAudit(definition);
  const stateNames = new Map(audit.states.map((state) => [state.id, state.name]));

  return <div className="advanced-definition-views" data-testid="advanced-definition-views">
    <section className="advanced-audit-section" aria-labelledby="advanced-flow-title">
      <div className="minor-heading"><div><h4 id="advanced-flow-title">Complete derived flow</h4>
        <p>The diagram and accessible rule list are two views of the same canonical states and transitions.</p></div></div>
      <div className="advanced-flow-graph" role="img"
        aria-label={`${audit.states.length} states and ${audit.rules.length} transitions`} data-testid="advanced-flow-graph">
        {audit.states.map((state) => {
          const outgoing = audit.rules.filter((rule) => rule.sourceStateId === state.id);
          return <article className={`advanced-flow-node ${state.kind}`} key={state.id} data-state-id={state.id}>
            <div><strong>{state.name}</strong><span>{state.kind}</span></div>
            <code>{state.id}</code>
            {state.initialFor.length > 0 && <small>Initial for {state.initialFor.join(", ")}</small>}
            {outgoing.length === 0 ? <small>No outgoing rule</small> : <ul>{outgoing.map((rule) => <li key={rule.id}>
              <span className={`continuation-badge ${rule.continuation}`}>{rule.continuation}</span>
              <span>to {stateNames.get(rule.targetStateId) ?? rule.targetStateId}</span>
            </li>)}</ul>}
          </article>;
        })}
      </div>
      <div className="advanced-table-wrap">
        <table className="advanced-flow-list" data-testid="advanced-flow-list">
          <caption>Canonical transition order and trigger summary</caption>
          <thead><tr><th scope="col">Rule ID</th><th scope="col">From → to</th><th scope="col">Trigger</th>
            <th scope="col">Decision / action envelopes</th><th scope="col">Then</th></tr></thead>
          <tbody>{audit.rules.map((rule) => <tr key={rule.id} data-rule-id={rule.id}>
            <th scope="row" data-label="Rule"><code>{rule.id}</code><small>order {rule.order}</small></th>
            <td data-label="From → to">{stateNames.get(rule.sourceStateId) ?? rule.sourceStateId} → {stateNames.get(rule.targetStateId) ?? rule.targetStateId}</td>
            <td data-label="Trigger">{rule.eventTypes.length === 0 ? "Any acknowledged or registered trigger" : rule.eventTypes.join(", ")}</td>
            <td data-label="Decision / action envelopes">{rule.decisions.length} / {rule.actions.length}</td>
            <td data-label="Then"><span className={`continuation-badge ${rule.continuation}`}>{rule.continuation}</span></td>
          </tr>)}</tbody>
        </table>
      </div>
      {audit.rules.length === 0 && <p className="empty-copy">No interaction rules are present.</p>}
    </section>

    <details className="advanced-audit-section" open>
      <summary>State IDs, containment, entry, history, and selectors</summary>
      <p>“Clear this state’s events” is the canonical oblivious-history setting. Entry mode determines whether entry starts normally or reprocesses its triggering event.</p>
      <div className="advanced-state-list" data-testid="advanced-state-audit">{audit.states.map((state) => <article key={state.id}>
        <div className="advanced-audit-heading"><div><strong>{state.name}</strong><code>{state.id}</code></div><span>{state.kind}</span></div>
        <dl className="advanced-facts">
          <div><dt>Contained by</dt><dd>{state.parentStateIds.join(" → ") || "Top level"}</dd></div>
          <div><dt>Initial for</dt><dd>{state.initialFor.join(", ") || "—"}</dd></div>
          <div><dt>Children</dt><dd>{state.childStateIds.join(", ") || "—"}</dd></div>
          <div><dt>Initial child</dt><dd>{state.initialChildStateId ?? "—"}</dd></div>
          <div><dt>Entry mode</dt><dd>{state.entryMode ?? "—"}</dd></div>
          <div><dt>History on entry</dt><dd>{state.oblivious === null ? "—" : state.oblivious ? "Clear this state’s events" : "Keep prior events"}</dd></div>
        </dl>
        {state.eventSelector && <Envelope title="Event selector" envelope={state.eventSelector} />}
        {state.policy && <Envelope title="Ordinary policy" envelope={state.policy} />}
      </article>)}</div>
    </details>

    <details className="advanced-audit-section">
      <summary>Registered component envelopes and pointers</summary>
      {audit.components.length === 0 ? <p className="empty-copy">No registered component envelopes are present.</p>
        : <div className="advanced-component-list" data-testid="advanced-component-audit">{audit.components.map((component) => <article key={`${component.kind}@${component.version}`}>
          <div className="advanced-audit-heading"><div><strong>{component.kind}</strong><code>version {component.version}</code></div>
            <span>{component.uses.length} use{component.uses.length === 1 ? "" : "s"}</span></div>
          <ol>{component.uses.map((use, index) => <li key={`${use.pointer}:${index}`}>
            <div><span>{use.role}</span><code>{use.pointer}</code></div>
            <pre>{JSON.stringify(use.envelope, null, 2)}</pre>
          </li>)}</ol>
        </article>)}</div>}
    </details>

    <details className="advanced-audit-section">
      <summary>Raw data schemas, resources, and lifecycle</summary>
      <h5>Lifecycle</h5><pre data-testid="advanced-lifecycle-json">{JSON.stringify(audit.lifecycle, null, 2)}</pre>
      <h5>Storage declarations and schemas</h5>
      {audit.storage.length === 0 ? <p className="empty-copy">No storage declarations are present.</p>
        : audit.storage.map((item) => <EnvelopeDocument key={item.pointer} pointer={item.pointer} value={item.declaration} />)}
      <h5>Resources</h5>
      {audit.resources.length === 0 ? <p className="empty-copy">No resources are present.</p>
        : audit.resources.map((item) => <EnvelopeDocument key={item.pointer} pointer={item.pointer} value={item.declaration} />)}
    </details>
  </div>;
}

function Envelope({ title, envelope }: { title: string; envelope: ComponentEnvelope }) {
  return <details className="advanced-envelope"><summary>{title}: <code>{envelope.kind}@{envelope.version}</code></summary>
    <pre>{JSON.stringify(envelope, null, 2)}</pre></details>;
}

function EnvelopeDocument({ pointer, value }: { pointer: string; value: Record<string, unknown> }) {
  return <details className="advanced-envelope"><summary><code>{pointer}</code></summary>
    <pre>{JSON.stringify(value, null, 2)}</pre></details>;
}
