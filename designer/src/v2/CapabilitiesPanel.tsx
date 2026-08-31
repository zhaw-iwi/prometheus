import type { ComponentDefinition } from "../api/designerApi";
import type { AgentDefinitionV1 } from "../model/agentDefinition";
import {
  capabilityOption,
  EXPRESSION_CAPABILITIES,
  humanizeCapabilityGroup,
  OBSERVATION_CAPABILITIES,
  type CapabilityOption,
} from "./authoringCatalog";
import {
  capabilityUses,
  deterministicOperationGroups,
  mainStrategy,
  responseStrategies,
  selectMainStrategy,
  strategyCompatibility,
  technicalEnvelope,
} from "./capabilityModel";
import type { DesignerV2Projection } from "./projection";
import { updateCapabilities, updateSituationPolicyConfig } from "./transforms";

interface CapabilitiesPanelProps {
  projection: DesignerV2Projection;
  components: ComponentDefinition[];
  readOnly: boolean;
  onChange: (definition: AgentDefinitionV1) => void;
  onGoToInteraction: () => void;
}

export function CapabilitiesPanel({ projection, components, readOnly, onChange, onGoToInteraction }: CapabilitiesPanelProps) {
  const change = (next: DesignerV2Projection) => onChange(next.source);
  const currentStrategy = mainStrategy(projection);
  const strategies = responseStrategies(components);
  const operationGroups = deterministicOperationGroups(projection, components);

  const toggle = (field: "observations" | "behaviourModalities", id: string, selected: boolean) => {
    const current = projection.capabilities[field];
    change(updateCapabilities(projection, { [field]: selected ? [...current, id] : current.filter((item) => item !== id) }));
  };

  return <div className="v2-authoring" data-testid="capabilities-authoring">
    <section className="authoring-section capability-section">
      <div className="section-heading"><div><h3>What can the agent notice?</h3><p>Choose available inputs. A choice does not create a rule, situation, or transition.</p></div></div>
      <CapabilityPalette options={OBSERVATION_CAPABILITIES} selected={projection.capabilities.observations}
        projection={projection} readOnly={readOnly} onToggle={(id, selected) => toggle("observations", id, selected)}
        onGoToInteraction={onGoToInteraction} />
      <UnknownCapabilities selected={projection.capabilities.observations} known={OBSERVATION_CAPABILITIES}
        projection={projection} readOnly={readOnly} onRemove={(id) => toggle("observations", id, false)}
        onGoToInteraction={onGoToInteraction} />
    </section>

    <section className="authoring-section capability-section">
      <div className="section-heading"><div><h3>How can it express itself?</h3><p>Declare output channels the connected experience may use. Rendering still depends on the client and embodiment.</p></div></div>
      <CapabilityPalette options={EXPRESSION_CAPABILITIES} selected={projection.capabilities.behaviourModalities}
        projection={projection} readOnly={readOnly} onToggle={(id, selected) => toggle("behaviourModalities", id, selected)}
        onGoToInteraction={onGoToInteraction} />
      <UnknownCapabilities selected={projection.capabilities.behaviourModalities} known={EXPRESSION_CAPABILITIES}
        projection={projection} readOnly={readOnly} onRemove={(id) => toggle("behaviourModalities", id, false)}
        onGoToInteraction={onGoToInteraction} />
    </section>

    <section className="authoring-section strategy-section" id="capability-response-strategies" tabIndex={-1}>
      <div className="section-heading"><div><h3>Main response strategy</h3><p>Choose how the Main interaction produces its ordinary response. Exceptional rules remain separate.</p></div></div>
      {strategies.length === 0 && <p className="empty-copy">No guided response strategies are registered.</p>}
      <div className="strategy-grid">
        {strategies.map((component) => {
          const selected = currentStrategy?.envelope.kind === component.kind && currentStrategy.envelope.version === component.version;
          const compatibility = strategyCompatibility(projection, component);
          const label = component.capabilityGroup === "exact-text-response" ? "Repeat exact text" : component.label;
          return <article className={`strategy-card${selected ? " selected" : ""}`} key={`${component.kind}@${component.version}`}
            data-testid={`strategy-card-${component.capabilityGroup ?? component.kind}`}>
            <div className="card-title-row"><div><span className="eyebrow">{selected ? "Used by Main" : "Available strategy"}</span>
              <h4>{label}</h4><p>{component.description}</p></div>
              <button className={selected ? "button secondary" : "button primary"} type="button"
                disabled={readOnly || selected || !compatibility.compatible}
                onClick={() => change(selectMainStrategy(projection, component))}>{selected ? "Selected" : "Use for Main"}</button></div>
            {!compatibility.compatible && <div className="compatibility-warning" role="status">
              Select {formatRequirements(compatibility.missingObservations, compatibility.missingModalities)} before using this strategy.
            </div>}
            {selected && component.capabilityGroup === "exact-text-response" && currentStrategy && <ExactTextSettings
              projection={projection} stateId={projection.situations.find((situation) => situation.main)!.id}
              config={currentStrategy.envelope.config} readOnly={readOnly} onChange={change} />}
            {selected && component.capabilityGroup === "prompt-response" && <p className="usage-note">Its ordered purpose and conduct guidance is edited in Brief; situation-specific guidance belongs in Interaction.</p>}
            <details className="technical-details"><summary>Technical details</summary>
              <pre>{technicalEnvelope(component.kind, component.version,
                selected && currentStrategy ? currentStrategy.envelope.config : component.defaultConfig)}</pre>
            </details>
          </article>;
        })}
      </div>
    </section>

    <section className="authoring-section operation-section">
      <div className="section-heading"><div><h3>Installed deterministic operations</h3><p>Registered operations run without arbitrary scripts. They become installed only through inspectable Interaction and Data content.</p></div></div>
      {operationGroups.length === 0 && <p className="empty-copy">No guided deterministic operations are registered.</p>}
      <div className="strategy-grid">
        {operationGroups.map((group) => <article className={`strategy-card${group.installedKinds.length ? " selected" : ""}`}
          key={group.id} data-testid={`operation-card-${group.id}`}>
          <span className="eyebrow">{group.installedKinds.length ? "Installed" : "Available operation"}</span>
          <h4>{humanizeCapabilityGroup(group.id)}</h4>
          <p>{group.id === "rock-scissor-paper"
            ? "Select a sign deterministically, evaluate the observed hand sign, reveal both choices, and report the round result."
            : group.components.map((component) => component.description).join(" ")}</p>
          {group.id === "rock-scissor-paper" && <dl className="operation-facts">
            <div><dt>Input</dt><dd>Rock, scissor, or paper hand sign</dd></div>
            <div><dt>Output</dt><dd>Speech, hand sign, and display result</dd></div>
            <div><dt>Working data</dt><dd>{group.ownedDataKeys.length ? group.ownedDataKeys.join(", ") : "Four round values when installed"}</dd></div>
          </dl>}
          {!group.installedKinds.length && <p className="usage-note">Choosing input or output capabilities alone does not install this operation. Its rules and owned data are added as explicit canonical content.</p>}
          <details className="technical-details"><summary>Technical details</summary>
            <ul>{group.components.map((component) => <li key={component.kind}><code>{component.kind}@{component.version}</code> — {component.label}</li>)}</ul>
          </details>
        </article>)}
      </div>
    </section>
  </div>;
}

function CapabilityPalette({ options, selected, projection, readOnly, onToggle, onGoToInteraction }: {
  options: CapabilityOption[];
  selected: string[];
  projection: DesignerV2Projection;
  readOnly: boolean;
  onToggle: (id: string, selected: boolean) => void;
  onGoToInteraction: () => void;
}) {
  const groups = [...new Set(options.map((option) => option.group))];
  return <div className="capability-groups">{groups.map((group) => <section key={group}>
    <h4>{group}</h4><div className="capability-grid">{options.filter((option) => option.group === group).map((option) => {
      const checked = selected.includes(option.id);
      const uses = capabilityUses(projection, option.id);
      return <article className={`capability-card${checked ? " selected" : ""}`} key={option.id}>
        <label><input type="checkbox" checked={checked} disabled={readOnly}
          onChange={(event) => onToggle(option.id, event.target.checked)} /><span><strong>{option.label}</strong><small>{option.description}</small></span></label>
        <p><strong>Example:</strong> {option.example}</p>
        <p className="uncertainty-copy"><strong>Keep in mind:</strong> {option.uncertainty}</p>
        {checked && (uses.length ? <span className="use-indicator">Used in {uses.length} configured place{uses.length === 1 ? "" : "s"}</span>
          : <UnusedWarning onGoToInteraction={onGoToInteraction} />)}
        <details className="technical-details"><summary>Technical details</summary><code>{option.id}</code></details>
      </article>;
    })}</div>
  </section>)}</div>;
}

function UnknownCapabilities({ selected, known, projection, readOnly, onRemove, onGoToInteraction }: {
  selected: string[];
  known: CapabilityOption[];
  projection: DesignerV2Projection;
  readOnly: boolean;
  onRemove: (id: string) => void;
  onGoToInteraction: () => void;
}) {
  const unknown = selected.filter((id) => !capabilityOption(id, known));
  if (unknown.length === 0) return null;
  return <section className="additional-capabilities"><h4>Additional imported capabilities</h4>
    <p>These registered identifiers are not in this Designer palette. They remain selected without conversion.</p>
    <ul>{unknown.map((id) => {
      const uses = capabilityUses(projection, id);
      return <li key={id}><code>{id}</code>{uses.length ? <span className="use-indicator">Used</span> : <UnusedWarning onGoToInteraction={onGoToInteraction} />}
        <button className="button danger" type="button" disabled={readOnly} onClick={() => onRemove(id)}>Remove</button></li>;
    })}</ul>
  </section>;
}

function UnusedWarning({ onGoToInteraction }: { onGoToInteraction: () => void }) {
  return <span className="unused-warning">Declared but not used. <button type="button" onClick={onGoToInteraction}>Use in Interaction</button></span>;
}

function ExactTextSettings({ projection, stateId, config, readOnly, onChange }: {
  projection: DesignerV2Projection;
  stateId: string;
  config: Record<string, unknown>;
  readOnly: boolean;
  onChange: (projection: DesignerV2Projection) => void;
}) {
  const eventType = typeof config.eventType === "string" ? config.eventType : "obs.user_utterance";
  const maxTextCodePoints = typeof config.maxTextCodePoints === "number" ? config.maxTextCodePoints : 2000;
  return <div className="strategy-settings" data-testid="exact-text-settings">
    <label className="field-stack"><span>Text to repeat</span><select value={eventType} disabled={readOnly}
      onChange={(event) => onChange(updateSituationPolicyConfig(projection, stateId, { eventType: event.target.value }))}>
      {projection.capabilities.observations.map((id) => <option key={id} value={id}>{capabilityOption(id, OBSERVATION_CAPABILITIES)?.label ?? id}</option>)}
    </select></label>
    <label className="field-stack"><span>Maximum characters</span><input type="number" min={1} max={100000}
      value={maxTextCodePoints} disabled={readOnly}
      onChange={(event) => onChange(updateSituationPolicyConfig(projection, stateId,
        { maxTextCodePoints: Number(event.target.value) }))} /></label>
    <p>Only the latest matching user observation is repeated; no model generates or rewrites it.</p>
  </div>;
}

function formatRequirements(observations: string[], modalities: string[]): string {
  return [...observations.map((id) => capabilityOption(id, OBSERVATION_CAPABILITIES)?.label ?? id),
    ...modalities.map((id) => capabilityOption(id, EXPRESSION_CAPABILITIES)?.label ?? id)].join(" and ");
}
