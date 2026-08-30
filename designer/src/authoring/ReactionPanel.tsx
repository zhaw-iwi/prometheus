import type { ComponentDefinition, DefinitionDiagnostic } from "../api/designerApi";
import {
  type AgentDefinitionV1,
  cloneJson,
  type ComponentEnvelope,
  isJsonObject,
  type TransitionDefinition,
} from "../model/agentDefinition";
import { ComponentEnvelopeEditor } from "./ComponentEnvelopeEditor";
import {
  addReaction,
  deleteTransition,
  envelopeFromComponent,
  missingCapabilities,
  moveTransition,
  reactionObservation,
  replaceTransition,
  setReactionObservation,
  synchronizeCapabilities,
} from "./graphModel";

interface ReactionPanelProps {
  definition: AgentDefinitionV1;
  components: ComponentDefinition[];
  diagnostics: DefinitionDiagnostic[];
  onChange: (definition: AgentDefinitionV1) => void;
}

export function ReactionPanel({ definition, components, diagnostics, onChange }: ReactionPanelProps) {
  const missing = missingCapabilities(definition, components);
  const transitionErrors = diagnosticIndexes(diagnostics, "/transitions/");
  return (
    <div className="authoring-panel reaction-panel" data-testid="reaction-authoring-panel">
      <section className="form-section">
        <div className="section-heading">
          <div><h3>When should something happen?</h3><p>Each card is one ordered move in the canonical state graph.</p></div>
          <button className="button primary" type="button" disabled={definition.interaction.supportedObservations.length === 0}
            onClick={() => onChange(addReaction(definition, definition.interaction.supportedObservations[0]))}
            data-testid="add-reaction">Add reaction</button>
        </div>
        {definition.interaction.supportedObservations.length === 0 && (
          <div className="empty-callout" role="status">Select at least one sensing input before adding a guided reaction.</div>
        )}
        {(missing.observations.length > 0 || missing.modalities.length > 0) && (
          <div className="capability-sync" role="status" data-testid="capability-sync-offer">
            <div><strong>Advanced components need undeclared capabilities.</strong>
              <p>{capabilitySummary(missing.observations, missing.modalities)}</p></div>
            <button className="button secondary" type="button"
              onClick={() => onChange(synchronizeCapabilities(definition, missing))}>Add to Sensing and Behaviour</button>
          </div>
        )}
        <div className="reaction-list">
          {definition.transitions.length === 0
            ? <div className="empty-callout"><strong>No reactions yet.</strong><p>The default situation can still respond through its main strategy.</p></div>
            : definition.transitions.map((transition, index) => (
              <ReactionCard key={transition.id} transition={transition} definition={definition} components={components}
                hasError={transitionErrors.has(index)} onChange={(replacement) => onChange(replaceTransition(definition, replacement))}
                onDelete={() => onChange(deleteTransition(definition, transition.id))}
                onMove={(direction) => onChange(moveTransition(definition, transition.id, direction))} />
            ))}
        </div>
      </section>
    </div>
  );
}

function ReactionCard({ transition, definition, components, hasError, onChange, onDelete, onMove }: {
  transition: TransitionDefinition;
  definition: AgentDefinitionV1;
  components: ComponentDefinition[];
  hasError: boolean;
  onChange: (transition: TransitionDefinition) => void;
  onDelete: () => void;
  onMove: (direction: -1 | 1) => void;
}) {
  const observation = reactionObservation(transition);
  const response = reactionResponse(transition);
  const additionalConditions = transition.decisions.filter((decision) => decision.kind !== "prometheus.decision.latest-event-type");
  return (
    <article className={`reaction-card${hasError ? " has-error" : ""}`} id={`reaction-${transition.id}`}
      data-testid={`reaction-${transition.id}`}>
      <div className="reaction-heading">
        <div><span className="eyebrow">Priority {transition.order}</span><h4>{transition.id}</h4></div>
        <div className="compact-actions">
          <button type="button" className="icon-button" aria-label={`Move ${transition.id} earlier`} onClick={() => onMove(-1)}>↑</button>
          <button type="button" className="icon-button" aria-label={`Move ${transition.id} later`} onClick={() => onMove(1)}>↓</button>
          <button type="button" className="icon-button danger" aria-label={`Delete ${transition.id}`} onClick={onDelete}>×</button>
        </div>
      </div>
      <div className="reaction-sentence">
        <label><span>When</span><select value={observation}
          onChange={(event) => onChange(setReactionObservation(transition, event.target.value))}>
          <option value="">Choose a declared observation</option>
          {definition.interaction.supportedObservations.map((value) => <option key={value}>{value}</option>)}
        </select></label>
        <label><span>in</span><select value={transition.sourceStateId}
          onChange={(event) => onChange({ ...transition, sourceStateId: event.target.value })}>
          {definition.states.filter((state) => state.kind !== "final").map((state) => (
            <option value={state.id} key={state.id}>{state.name}</option>
          ))}
        </select></label>
        <div className="reaction-clause"><span>If</span><strong>{additionalConditions.length === 0
          ? "always" : `${additionalConditions.length} additional condition${additionalConditions.length === 1 ? "" : "s"}`}</strong></div>
        <label className="reaction-response"><span>Then respond</span>
          <textarea rows={3} value={response} onChange={(event) => onChange(setReactionResponse(
            transition, event.target.value, observation, definition.interaction.supportedBehaviourModalities,
          ))} placeholder="Optional response instructions" />
        </label>
        <label><span>And</span><select value={transition.targetStateId}
          onChange={(event) => onChange({ ...transition, targetStateId: event.target.value })}>
          {definition.states.map((state) => <option value={state.id} key={state.id}>
            {state.id === transition.sourceStateId ? `Stay in ${state.name}` : `Move to ${state.name}`}
          </option>)}
        </select></label>
      </div>
      <details className="reaction-advanced">
        <summary>Conditions, actions, and advanced configuration</summary>
        <div className="advanced-columns">
          <EnvelopeListEditor label="Ordered conditions" category="DECISION" envelopes={transition.decisions}
            components={components} onChange={(decisions) => onChange({ ...transition, decisions })} />
          <EnvelopeListEditor label="Ordered actions" category="ACTION" envelopes={transition.actions}
            components={components} onChange={(actions) => onChange({ ...transition, actions })} />
        </div>
      </details>
    </article>
  );
}

export function EnvelopeListEditor({ label, category, envelopes, components, onChange }: {
  label: string;
  category: "DECISION" | "ACTION";
  envelopes: ComponentEnvelope[];
  components: ComponentDefinition[];
  onChange: (envelopes: ComponentEnvelope[]) => void;
}) {
  const choices = components.filter((component) => component.category === category);
  return (
    <section className="envelope-list">
      <div className="list-heading"><strong>{label}</strong>
        <button className="button secondary compact-button" type="button" disabled={choices.length === 0}
          onClick={() => choices[0] && onChange([...envelopes, envelopeFromComponent(choices[0])])}>Add</button>
      </div>
      {envelopes.length === 0 && <p>None.</p>}
      {envelopes.map((envelope, index) => (
        <div className="envelope-row" key={`${envelope.kind}:${index}`}>
          <ComponentEnvelopeEditor envelope={envelope} category={category} components={components} label={`${category === "ACTION" ? "Action" : "Condition"} ${index + 1}`}
            onChange={(replacement) => replacement && onChange(replaceAt(envelopes, index, replacement))} />
          <div className="compact-actions">
            <button className="icon-button" type="button" aria-label={`Move ${label} item ${index + 1} earlier`}
              onClick={() => onChange(moveAt(envelopes, index, -1))}>↑</button>
            <button className="icon-button" type="button" aria-label={`Move ${label} item ${index + 1} later`}
              onClick={() => onChange(moveAt(envelopes, index, 1))}>↓</button>
            <button className="icon-button danger" type="button" aria-label={`Delete ${label} item ${index + 1}`}
              onClick={() => onChange(envelopes.filter((_, candidate) => candidate !== index))}>×</button>
          </div>
        </div>
      ))}
    </section>
  );
}

function reactionResponse(transition: TransitionDefinition): string {
  const action = transition.actions.find((candidate) => candidate.kind === "prometheus.action.prompt-behaviour");
  const prompt = action?.config.responsePrompt;
  if (!isJsonObject(prompt) || !Array.isArray(prompt.sections)) return "";
  return prompt.sections.filter(isJsonObject).map((section) => section.content)
    .filter((content): content is string => typeof content === "string").join("\n\n");
}

function setReactionResponse(
  transition: TransitionDefinition,
  content: string,
  observation: string,
  modalities: string[],
): TransitionDefinition {
  const next = cloneJson(transition);
  const index = next.actions.findIndex((action) => action.kind === "prometheus.action.prompt-behaviour");
  if (!content.trim()) {
    if (index >= 0) next.actions.splice(index, 1);
    return next;
  }
  const existing = index >= 0 ? next.actions[index] : null;
  const config = existing ? cloneJson(existing.config) : {};
  config.responsePrompt = { sections: [{ id: `${transition.id}.response`, kind: "completion", content }] };
  config.consumedObservations = observation ? [observation] : [];
  config.emittedModalities = [...modalities];
  const replacement: ComponentEnvelope = {
    kind: "prometheus.action.prompt-behaviour", version: 1, config,
  };
  if (index < 0) next.actions.push(replacement);
  else next.actions[index] = replacement;
  return next;
}

function diagnosticIndexes(diagnostics: DefinitionDiagnostic[], prefix: string): Set<number> {
  return new Set(diagnostics.map((diagnostic) => diagnostic.pointer.match(new RegExp(`^${prefix}(\\d+)`))?.[1])
    .filter((value): value is string => value !== undefined).map(Number));
}

function replaceAt<T>(values: T[], index: number, value: T): T[] {
  const next = [...values];
  next[index] = value;
  return next;
}

function moveAt<T>(values: T[], index: number, direction: -1 | 1): T[] {
  const next = [...values];
  const target = index + direction;
  if (target < 0 || target >= next.length) return next;
  [next[index], next[target]] = [next[target], next[index]];
  return next;
}

function capabilitySummary(observations: string[], modalities: string[]): string {
  const parts = [];
  if (observations.length) parts.push(`Sensing: ${observations.join(", ")}`);
  if (modalities.length) parts.push(`Behaviour: ${modalities.join(", ")}`);
  return parts.join(" · ");
}
