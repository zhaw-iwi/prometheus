import { useState } from "react";
import type { ComponentDefinition } from "../api/designerApi";
import type { AgentDefinitionV1, ComponentEnvelope, JsonObject } from "../model/agentDefinition";
import {
  GUIDANCE_INTENTS,
  OBSERVATION_CAPABILITIES,
  capabilityOption,
  guidanceIntent,
  humanizeCapabilityGroup,
} from "./authoringCatalog";
import {
  LATEST_EVENT_KIND,
  PROMPT_CONDITION_KIND,
  PROMPT_EFFECT_KIND,
  addRule,
  addRuleComponent,
  addRulePromptSection,
  addSituation,
  addSituationGuidance,
  availableRuleConditions,
  availableRuleEffects,
  createSituationForRule,
  deleteRule,
  deleteSituation,
  globalRuleSource,
  moveRule,
  moveRuleComponent,
  moveSituationGuidance,
  promptSections,
  removeRuleComponent,
  removeRulePromptSection,
  removeSituationGuidance,
  renameSituation,
  setRuleContinuation,
  setRuleEvent,
  situationDeletion,
  updateRuleComponentConfig,
  updateRulePromptSection,
  updateSituationGuidance,
} from "./interactionModel";
import type { DesignerV2Projection, InteractionRuleProjection, SituationProjection } from "./projection";

interface InteractionPanelProps {
  projection: DesignerV2Projection;
  components: ComponentDefinition[];
  readOnly: boolean;
  onChange: (definition: AgentDefinitionV1) => void;
  onGoToCapabilities: () => void;
}

export function InteractionPanel({ projection, components, readOnly, onChange, onGoToCapabilities }: InteractionPanelProps) {
  const [newSituationName, setNewSituationName] = useState("");
  const globalSource = globalRuleSource(projection);
  const globals = projection.rules.filter((rule) => rule.scope === "global")
    .sort((left, right) => left.order - right.order);
  const promptPolicy = components.find((component) => component.kind === "prometheus.policy.prompt");
  const change = (next: DesignerV2Projection) => onChange(next.source);
  const createSituation = () => {
    change(addSituation(projection, newSituationName, promptPolicy ? envelope(promptPolicy) : undefined));
    setNewSituationName("");
  };

  return <div className="v2-authoring interaction-authoring" data-testid="interaction-authoring">
    <section className="authoring-section interaction-intro">
      <div className="section-heading"><div><h3>Interaction storyboard</h3>
        <p>Main handles the ordinary conversation. Add a situation only when a durable phase changes how later events are handled.</p></div>
        <button className="button secondary" type="button" onClick={onGoToCapabilities}>Review capabilities</button></div>
      <div className="interaction-legend" aria-label="Rule sentence">
        <span>When an event happens</span><span>and conditions fit</span><span>then effects run in order</span><span>and the interaction stays, moves, or finishes</span>
      </div>
    </section>

    {globalSource && <section className="authoring-section always-section" id="interaction-always" tabIndex={-1}>
      <div className="section-heading"><div><span className="eyebrow">Always</span><h3>Rules that apply throughout the interaction</h3>
        <p>These rules are inherited by situations inside this agent context.</p></div>
        <span className="item-count">{count(globals.length, "rule")}</span></div>
      <RuleList projection={projection} rules={globals} components={components} readOnly={readOnly} onChange={change} />
      <AddRuleRow projection={projection} sourceStateId={globalSource} readOnly={readOnly} onChange={change} />
    </section>}

    <div className="situation-storyboard">
      {projection.situations.map((situation, index) => <SituationCard key={situation.id} projection={projection}
        situation={situation} position={index} components={components} readOnly={readOnly} onChange={change} />)}
    </div>

    {!readOnly && <section className="authoring-section add-situation-section">
      <div><h3>Add a durable situation</h3><p>Use this for a phase such as intake, follow-up, or a game round—not for every response.</p></div>
      <div className="inline-create-row">
        <label className="field-stack" htmlFor="new-situation-name"><span>Situation name</span>
          <input id="new-situation-name" value={newSituationName} placeholder="Follow-up"
            onChange={(event) => setNewSituationName(event.target.value)} /></label>
        <button className="button primary" type="button" disabled={!newSituationName.trim()} onClick={createSituation}>Add situation</button>
      </div>
    </section>}

    <FlowOverview projection={projection} />
    <AdvancedTopology projection={projection} />
  </div>;
}

function SituationCard({ projection, situation, position, components, readOnly, onChange }: {
  projection: DesignerV2Projection;
  situation: SituationProjection;
  position: number;
  components: ComponentDefinition[];
  readOnly: boolean;
  onChange: (projection: DesignerV2Projection) => void;
}) {
  const [newGuidance, setNewGuidance] = useState("objective");
  const rules = projection.rules.filter((rule) => rule.sourceStateId === situation.id)
    .sort((left, right) => left.order - right.order);
  const policyComponent = situation.ordinaryPolicy
    ? componentFor(components, situation.ordinaryPolicy.envelope) : undefined;
  const deletion = situationDeletion(projection, situation.id);
  const inherited = projection.guidance.filter((item) => item.scope === "agent"
    && situation.parentStateIds.includes(item.stateId));
  const localGroups = groupGuidance(situation.guidance);
  const supportsGuidance = situation.ordinaryPolicy?.envelope.kind === "prometheus.policy.prompt";

  return <section className={`authoring-section situation-card${situation.main ? " main-situation" : ""}`}
    id={`interaction-situation-${situation.id}`} tabIndex={-1} data-testid={`situation-card-${situation.id}`}>
    <div className="section-heading"><div><span className="eyebrow">{situation.main ? "Main interaction" : `Situation ${position + 1}`}</span>
      <input className="situation-name-input" aria-label={`${situation.main ? "Main" : "Situation"} name`}
        value={situation.name} disabled={readOnly}
        onChange={(event) => onChange(renameSituation(projection, situation.id, event.target.value))} />
      <p>Ordinary response: <strong>{policyComponent?.label ?? (situation.ordinaryPolicy ? "Registered strategy" : "No ordinary response")}</strong></p></div>
      {!situation.main && <div className="delete-with-reason">
        <button className="button danger" type="button" disabled={readOnly || !deletion.allowed}
          onClick={() => onChange(deleteSituation(projection, situation.id))}>Remove situation</button>
        {!deletion.allowed && <small>{deletion.reason}</small>}
      </div>}</div>

    {inherited.length > 0 && <details className="effective-guidance">
      <summary>{count(inherited.length, "inherited agent guidance card")}</summary>
      <ol>{inherited.map((section) => <li key={section.pointer}><strong>{guidanceIntent(section.kind)?.label ?? "Additional guidance"}</strong>
        <span>{section.content}</span></li>)}</ol>
    </details>}

    <div className="situation-guidance-block">
      <div className="minor-heading"><div><h4>Situation guidance and entry behavior</h4>
        <p>Local ordered guidance adds to the inherited agent guidance. “When this situation begins” is entry behavior.</p></div>
        <span className="item-count">{count(situation.guidance.length, "card")}</span></div>
      {situation.guidance.length === 0 && <p className="empty-copy">No local guidance or entry behavior.</p>}
      {localGroups.map(([promptField, sections]) => <div className="guidance-list" key={promptField}>
        {sections.map((section, localIndex) => {
          const intent = guidanceIntent(section.kind);
          return <article className="guidance-card compact-guidance" key={section.pointer}>
            <div className="card-title-row"><div><span className="eyebrow">{promptField === "starterPrompt" ? "When this situation begins" : "Situation guidance"}</span>
              <h5>{intent?.label ?? "Additional guidance"}</h5></div>
              <div className="order-actions" aria-label={`Order ${intent?.label ?? section.id}`}>
                <button type="button" aria-label="Move situation guidance earlier" disabled={readOnly || localIndex === 0}
                  onClick={() => onChange(moveSituationGuidance(projection, situation.id, promptField, localIndex, -1))}>↑</button>
                <button type="button" aria-label="Move situation guidance later" disabled={readOnly || localIndex === sections.length - 1}
                  onClick={() => onChange(moveSituationGuidance(projection, situation.id, promptField, localIndex, 1))}>↓</button>
              </div></div>
            <textarea aria-label={`${intent?.label ?? "Additional situation guidance"} content`} value={section.content}
              disabled={readOnly} onChange={(event) => onChange(updateSituationGuidance(
                projection, situation.id, promptField, localIndex, { content: event.target.value },
              ))} />
            <div className="card-footer-actions"><details className="technical-details"><summary>Technical details</summary>
              <div className="authoring-fields two-columns compact-fields"><label className="field-stack"><span>Section ID</span>
                <input value={section.id} disabled={readOnly} onChange={(event) => onChange(updateSituationGuidance(
                  projection, situation.id, promptField, localIndex, { id: event.target.value },
                ))} /></label><label className="field-stack"><span>Section kind</span>
                <input value={section.kind} disabled={readOnly} onChange={(event) => onChange(updateSituationGuidance(
                  projection, situation.id, promptField, localIndex, { kind: event.target.value },
                ))} /></label></div></details>
              <button className="button danger" type="button" disabled={readOnly}
                onClick={() => onChange(removeSituationGuidance(projection, situation.id, promptField, localIndex))}>Remove</button></div>
          </article>;
        })}
      </div>)}
      {supportsGuidance ? <div className="add-guidance-row">
        <label htmlFor={`situation-guidance-${situation.id}`}>Add local guidance</label>
        <select id={`situation-guidance-${situation.id}`} value={newGuidance} disabled={readOnly}
          onChange={(event) => setNewGuidance(event.target.value)}>
          {GUIDANCE_INTENTS.map((intent) => <option value={intent.kind} key={intent.kind}>{intent.label}</option>)}
        </select>
        <button className="button secondary" type="button" disabled={readOnly}
          onClick={() => onChange(addSituationGuidance(projection, situation.id, newGuidance))}>Add card</button>
      </div> : <p className="scope-note">This registered response strategy has no prompt guidance cards. Its typed settings remain available in Capabilities.</p>}
    </div>

    <div className="situation-rules">
      <div className="minor-heading"><div><h4>Interaction rules</h4><p>Rules are checked in the shown order; all conditions in one rule must fit.</p></div>
        <span className="item-count">{count(rules.length, "rule")}</span></div>
      <RuleList projection={projection} rules={rules} components={components} readOnly={readOnly} onChange={onChange} />
      <AddRuleRow projection={projection} sourceStateId={situation.id} readOnly={readOnly} onChange={onChange} />
    </div>
  </section>;
}

function AddRuleRow({ projection, sourceStateId, readOnly, onChange }: {
  projection: DesignerV2Projection;
  sourceStateId: string;
  readOnly: boolean;
  onChange: (projection: DesignerV2Projection) => void;
}) {
  const [eventType, setEventType] = useState(projection.capabilities.observations[0] ?? "");
  const selectedEvent = projection.capabilities.observations.includes(eventType)
    ? eventType : projection.capabilities.observations[0] ?? "";
  if (projection.capabilities.observations.length === 0) return <div className="scope-note">
    Declare what the agent can notice in Capabilities before adding an event rule.
  </div>;
  return <div className="add-rule-row">
    <label className="field-stack"><span>When</span><select value={selectedEvent} disabled={readOnly}
      onChange={(event) => setEventType(event.target.value)}>{projection.capabilities.observations.map((id) =>
        <option value={id} key={id}>{observationLabel(id)}</option>)}</select></label>
    <button className="button primary" type="button" disabled={readOnly || !selectedEvent}
      onClick={() => onChange(addRule(projection, sourceStateId, selectedEvent))}>Add interaction rule</button>
  </div>;
}

function RuleList({ projection, rules, components, readOnly, onChange }: {
  projection: DesignerV2Projection;
  rules: InteractionRuleProjection[];
  components: ComponentDefinition[];
  readOnly: boolean;
  onChange: (projection: DesignerV2Projection) => void;
}) {
  if (rules.length === 0) return <p className="empty-copy">No rules in this scope.</p>;
  return <ol className="interaction-rule-list">{rules.map((rule, index) => <li key={rule.id}>
    <RuleCard projection={projection} rule={rule} position={index} total={rules.length} components={components}
      readOnly={readOnly} onChange={onChange} />
  </li>)}</ol>;
}

function RuleCard({ projection, rule, position, total, components, readOnly, onChange }: {
  projection: DesignerV2Projection;
  rule: InteractionRuleProjection;
  position: number;
  total: number;
  components: ComponentDefinition[];
  readOnly: boolean;
  onChange: (projection: DesignerV2Projection) => void;
}) {
  const [newConditionKind, setNewConditionKind] = useState(availableRuleConditions(components)[0]?.kind ?? "");
  const [newEffectKind, setNewEffectKind] = useState(availableRuleEffects(components)[0]?.kind ?? "");
  const [newSituationName, setNewSituationName] = useState("");
  const conditionOptions = availableRuleConditions(components);
  const effectOptions = availableRuleEffects(components);
  const transition = projection.source.transitions[rule.transitionIndex];
  const nonTriggerConditions = rule.conditions.filter((condition) => condition.envelope.kind !== LATEST_EVENT_KIND);
  const triggerIndex = rule.conditions.findIndex((condition) => condition.envelope.kind === LATEST_EVENT_KIND);
  const firstEvent = rule.eventTypes[0] ?? "";
  const target = projection.source.states.find((state) => state.id === rule.targetStateId);
  const targetSituation = projection.situations.find((situation) => situation.id === rule.targetStateId);
  const continuationValue = rule.continuation === "stay" ? "stay"
    : rule.continuation === "finish" ? "finish"
      : targetSituation ? `move:${targetSituation.id}` : "advanced";
  const promptPolicy = components.find((component) => component.kind === "prometheus.policy.prompt");
  const addCondition = () => {
    const component = conditionOptions.find((candidate) => candidate.kind === newConditionKind);
    if (component) onChange(addRuleComponent(projection, rule.id, "condition", component));
  };
  const addEffect = () => {
    const component = effectOptions.find((candidate) => candidate.kind === newEffectKind);
    if (component) onChange(addRuleComponent(projection, rule.id, "effect", component));
  };
  const setContinuation = (value: string) => {
    if (value === "stay" || value === "finish") onChange(setRuleContinuation(projection, rule.id, value));
    if (value.startsWith("move:")) onChange(setRuleContinuation(projection, rule.id, "move", value.slice(5)));
  };

  return <article className="interaction-rule-card" id={`interaction-rule-${rule.id}`} tabIndex={-1}
    data-testid={`interaction-rule-${rule.id}`}>
    <div className="card-title-row"><div><span className="eyebrow">Rule {position + 1}</span>
      <h5>{rule.scope === "global" ? "Always consider this rule" : "In this situation"}</h5></div>
      <div className="order-actions" aria-label={`Order rule ${position + 1}`}>
        <button type="button" aria-label="Move rule earlier" disabled={readOnly || position === 0}
          onClick={() => onChange(moveRule(projection, rule.id, -1))}>↑</button>
        <button type="button" aria-label="Move rule later" disabled={readOnly || position === total - 1}
          onClick={() => onChange(moveRule(projection, rule.id, 1))}>↓</button>
      </div></div>

    <div className="rule-sentence">
      <label className="rule-clause" id={triggerIndex >= 0 ? `interaction-rule-${rule.id}-condition-${triggerIndex}` : undefined}
        tabIndex={triggerIndex >= 0 ? -1 : undefined}><span>When</span><select aria-label="Rule event" value={firstEvent} disabled={readOnly}
        onChange={(event) => onChange(setRuleEvent(projection, rule.id, event.target.value))}>
        {!firstEvent && <option value="">Imported condition only</option>}
        {projection.capabilities.observations.map((id) => <option value={id} key={id}>{observationLabel(id)}</option>)}
        {rule.eventTypes.filter((id) => !projection.capabilities.observations.includes(id)).map((id) =>
          <option value={id} key={id}>Additional imported observation</option>)}
      </select></label>
      <span className="sentence-join">and</span>
      <span className="rule-clause summary-clause">{nonTriggerConditions.length === 0 ? "no extra condition" : count(nonTriggerConditions.length, "condition")}</span>
      <span className="sentence-join">then</span>
      <span className="rule-clause summary-clause">{rule.effects.length === 0 ? "no extra effect" : count(rule.effects.length, "ordered effect")}</span>
      <span className="sentence-join">and</span>
      <label className="rule-clause"><span>Continue</span><select aria-label="Rule continuation" value={continuationValue}
        disabled={readOnly} onChange={(event) => setContinuation(event.target.value)}>
        <option value="stay">Stay here</option>
        {projection.situations.filter((situation) => situation.id !== rule.sourceStateId).map((situation) =>
          <option value={`move:${situation.id}`} key={situation.id}>Continue in {situation.name}</option>)}
        <option value="finish">Finish the interaction</option>
        {continuationValue === "advanced" && <option value="advanced">Advanced destination (preserved)</option>}
      </select></label>
    </div>

    <section className="rule-detail-section"><div className="minor-heading"><div><h6>Conditions</h6>
      <p>Every condition must fit. The selected event trigger is generated automatically.</p></div></div>
      {nonTriggerConditions.length === 0 && <p className="empty-copy">No extra condition.</p>}
      <div className="rule-component-list">{rule.conditions.map((condition, conditionIndex) => {
        if (condition.envelope.kind === LATEST_EVENT_KIND) return null;
        return <ConditionCard key={condition.pointer} projection={projection} rule={rule} condition={condition.envelope}
          conditionIndex={conditionIndex} components={components} readOnly={readOnly} onChange={onChange} />;
      })}</div>
      {conditionOptions.length > 0 && <div className="add-component-row"><select aria-label="Condition type" value={newConditionKind}
        disabled={readOnly} onChange={(event) => setNewConditionKind(event.target.value)}>
        {conditionOptions.map((component) => <option value={component.kind} key={component.kind}>{componentLabel(component)}</option>)}</select>
        <button className="button secondary" type="button" disabled={readOnly || !newConditionKind} onClick={addCondition}>Add condition</button></div>}
    </section>

    <section className="rule-detail-section"><div className="minor-heading"><div><h6>Effects</h6>
      <p>Effects run in the shown order after the conditions accept.</p></div></div>
      {rule.effects.length === 0 && <p className="empty-copy">No extra effect; the destination’s ordinary response still applies.</p>}
      <div className="rule-component-list">{rule.effects.map((effect, effectIndex) =>
        <EffectCard key={effect.pointer} projection={projection} rule={rule} effect={effect.envelope}
          effectIndex={effectIndex} components={components} readOnly={readOnly} onChange={onChange} />)}</div>
      {effectOptions.length > 0 && <div className="add-component-row"><select aria-label="Effect type" value={newEffectKind}
        disabled={readOnly} onChange={(event) => setNewEffectKind(event.target.value)}>
        {effectOptions.map((component) => <option value={component.kind} key={component.kind}>{componentLabel(component)}</option>)}</select>
        <button className="button secondary" type="button" disabled={readOnly || !newEffectKind} onClick={addEffect}>Add effect</button></div>}
    </section>

    {!readOnly && <div className="inline-create-rule-target"><span>Need a new phase?</span>
      <input aria-label="New destination situation" value={newSituationName} placeholder="New situation name"
        onChange={(event) => setNewSituationName(event.target.value)} />
      <button className="button secondary" type="button" disabled={!newSituationName.trim()}
        onClick={() => { onChange(createSituationForRule(projection, rule.id, newSituationName,
          promptPolicy ? envelope(promptPolicy) : undefined)); setNewSituationName(""); }}>Create and continue there</button></div>}

    <div className="card-footer-actions"><details className="technical-details"><summary>Technical details</summary>
      <dl className="technical-facts"><div><dt>Rule ID</dt><dd><code>{rule.id}</code></dd></div>
        <div><dt>Priority</dt><dd>{transition?.order}</dd></div><div><dt>Source</dt><dd><code>{rule.sourceStateId}</code></dd></div>
        <div><dt>Destination</dt><dd><code>{target?.id ?? rule.targetStateId}</code></dd></div></dl>
      {rule.eventTypes.length > 1 && <p>{count(rule.eventTypes.length, "event trigger")} are preserved; the first is guided above.</p>}
    </details><button className="button danger" type="button" disabled={readOnly}
      onClick={() => onChange(deleteRule(projection, rule.id))}>Remove rule</button></div>
  </article>;
}

function ConditionCard({ projection, rule, condition, conditionIndex, components, readOnly, onChange }: {
  projection: DesignerV2Projection; rule: InteractionRuleProjection; condition: ComponentEnvelope; conditionIndex: number;
  components: ComponentDefinition[]; readOnly: boolean; onChange: (projection: DesignerV2Projection) => void;
}) {
  const component = componentFor(components, condition);
  const sections = condition.kind === PROMPT_CONDITION_KIND ? promptSections(condition, "decisionPrompt") : [];
  return <article className="rule-component-card" id={`interaction-rule-${rule.id}-condition-${conditionIndex}`} tabIndex={-1}>
    <div className="card-title-row"><div><span className="eyebrow">{component ? "Condition" : "Additional registered condition"}</span>
      <h6>{component ? componentLabel(component) : "Imported condition"}</h6><p>{component?.description ?? "This condition is preserved without reinterpretation."}</p></div>
      <ComponentOrder role="condition" index={conditionIndex} total={rule.conditions.length} projection={projection}
        rule={rule} readOnly={readOnly} onChange={onChange} /></div>
    {condition.kind === PROMPT_CONDITION_KIND && <>
      {sections.map((section, sectionIndex) => <div className="prompt-section-editor" key={`${section.id}:${sectionIndex}`}>
        <label className="field-stack"><span>{conditionSectionLabel(section.kind)}</span><textarea value={section.content} disabled={readOnly}
          placeholder="Describe the evidence in ordinary language."
          onChange={(event) => onChange(updateRulePromptSection(projection, rule.id, "condition", conditionIndex,
            "decisionPrompt", sectionIndex, { content: event.target.value }))} /></label>
        <button className="button danger compact-button" type="button" disabled={readOnly || sections.length === 1}
          onClick={() => onChange(removeRulePromptSection(projection, rule.id, "condition", conditionIndex,
            "decisionPrompt", sectionIndex))}>Remove example</button>
      </div>)}
      <div className="example-actions"><button className="button secondary" type="button" disabled={readOnly}
        onClick={() => onChange(addRulePromptSection(projection, rule.id, "condition", conditionIndex,
          "decisionPrompt", "positive-example"))}>Add positive example</button>
      <button className="button secondary" type="button" disabled={readOnly}
        onClick={() => onChange(addRulePromptSection(projection, rule.id, "condition", conditionIndex,
          "decisionPrompt", "negative-example"))}>Add negative example</button></div>
    </>}
    <ComponentFooter projection={projection} rule={rule} role="condition" index={conditionIndex}
      envelope={condition} component={component} readOnly={readOnly} onChange={onChange} />
  </article>;
}

function EffectCard({ projection, rule, effect, effectIndex, components, readOnly, onChange }: {
  projection: DesignerV2Projection; rule: InteractionRuleProjection; effect: ComponentEnvelope; effectIndex: number;
  components: ComponentDefinition[]; readOnly: boolean; onChange: (projection: DesignerV2Projection) => void;
}) {
  const component = componentFor(components, effect);
  const sections = effect.kind === PROMPT_EFFECT_KIND ? promptSections(effect, "responsePrompt") : [];
  const simpleFields = schemaStringFields(component, effect.config);
  return <article className="rule-component-card" id={`interaction-rule-${rule.id}-effect-${effectIndex}`} tabIndex={-1}>
    <div className="card-title-row"><div><span className="eyebrow">{component ? "Effect" : "Additional registered effect"}</span>
      <h6>{component ? componentLabel(component) : "Imported effect"}</h6><p>{component?.description ?? "This effect is preserved without reinterpretation."}</p></div>
      <ComponentOrder role="effect" index={effectIndex} total={rule.effects.length} projection={projection}
        rule={rule} readOnly={readOnly} onChange={onChange} /></div>
    {effect.kind === PROMPT_EFFECT_KIND && <div className="prompt-effect-sections">{sections.map((section, sectionIndex) =>
      <label className="field-stack" key={`${section.id}:${sectionIndex}`}><span>Response guidance</span>
        <textarea value={section.content} disabled={readOnly} onChange={(event) => onChange(updateRulePromptSection(
          projection, rule.id, "effect", effectIndex, "responsePrompt", sectionIndex, { content: event.target.value },
        ))} /></label>)}</div>}
    {effect.kind !== PROMPT_EFFECT_KIND && component?.capabilityGroup && <p className="operation-summary">
      Registered operation: <strong>{humanizeCapabilityGroup(component.capabilityGroup)}</strong>
    </p>}
    {simpleFields.length > 0 && <details className="technical-details component-settings"><summary>Operation settings</summary>
      <div className="authoring-fields two-columns compact-fields">{simpleFields.map((field) =>
        <label className="field-stack" key={field.key}><span>{field.label}</span><input value={field.value} disabled={readOnly}
          onChange={(event) => onChange(updateRuleComponentConfig(projection, rule.id, "effect", effectIndex,
            { [field.key]: event.target.value }))} /></label>)}</div>
    </details>}
    <ComponentFooter projection={projection} rule={rule} role="effect" index={effectIndex}
      envelope={effect} component={component} readOnly={readOnly} onChange={onChange} />
  </article>;
}

function ComponentOrder({ role, index, total, projection, rule, readOnly, onChange }: {
  role: "condition" | "effect"; index: number; total: number; projection: DesignerV2Projection;
  rule: InteractionRuleProjection; readOnly: boolean; onChange: (projection: DesignerV2Projection) => void;
}) {
  const minimum = role === "condition" && rule.conditions[0]?.envelope.kind === LATEST_EVENT_KIND ? 1 : 0;
  return <div className="order-actions" aria-label={`Order ${role} ${index + 1}`}>
    <button type="button" aria-label={`Move ${role} earlier`} disabled={readOnly || index <= minimum}
      onClick={() => onChange(moveRuleComponent(projection, rule.id, role, index, -1))}>↑</button>
    <button type="button" aria-label={`Move ${role} later`} disabled={readOnly || index === total - 1}
      onClick={() => onChange(moveRuleComponent(projection, rule.id, role, index, 1))}>↓</button>
  </div>;
}

function ComponentFooter({ projection, rule, role, index, envelope, component, readOnly, onChange }: {
  projection: DesignerV2Projection; rule: InteractionRuleProjection; role: "condition" | "effect"; index: number;
  envelope: ComponentEnvelope; component?: ComponentDefinition; readOnly: boolean;
  onChange: (projection: DesignerV2Projection) => void;
}) {
  return <div className="card-footer-actions"><details className="technical-details"><summary>Technical details</summary>
    <code>{envelope.kind}@{envelope.version}</code>
    {!component && <p>The installed catalog does not describe this imported component. Canonical configuration remains unchanged.</p>}
  </details><button className="button danger" type="button" disabled={readOnly}
    onClick={() => onChange(removeRuleComponent(projection, rule.id, role, index))}>Remove {role}</button></div>;
}

function FlowOverview({ projection }: { projection: DesignerV2Projection }) {
  if (projection.situations.length <= 1 && projection.rules.length === 0) return null;
  const stateName = (id: string) => projection.source.states.find((state) => state.id === id)?.name ?? "Advanced state";
  return <section className="authoring-section flow-overview" data-testid="derived-flow-overview">
    <div className="section-heading"><div><h3>Flow overview</h3><p>A read-only overview derived from the situation and rule cards above.</p></div></div>
    <div className="flow-node-list">{projection.situations.map((situation) => <span key={situation.id}
      className={situation.main ? "main" : ""}>{situation.name}</span>)}</div>
    <ol>{[...projection.rules].sort((left, right) => left.order - right.order).map((rule) => <li key={rule.id}>
      <strong>{rule.scope === "global" ? "Always" : stateName(rule.sourceStateId)}</strong>
      <span>— {rule.eventTypes[0] ? observationLabel(rule.eventTypes[0]) : "registered condition"} → </span>
      <strong>{rule.continuation === "stay" ? "Stay" : rule.continuation === "finish" ? "Finish" : stateName(rule.targetStateId)}</strong>
    </li>)}</ol>
  </section>;
}

function AdvancedTopology({ projection }: { projection: DesignerV2Projection }) {
  const advanced = projection.source.states.filter((state) => state.kind !== "atomic");
  if (advanced.length === 0) return null;
  return <details className="authoring-section technical-details advanced-topology"><summary>Advanced derived topology</summary>
    <p>Composite context and final nodes are preserved by the storyboard and remain directly inspectable in canonical JSON.</p>
    <ul>{advanced.map((state) => <li key={state.id}><strong>{state.name}</strong> <span>{state.kind}</span></li>)}</ul>
  </details>;
}

function groupGuidance(guidance: SituationProjection["guidance"]) {
  const groups = new Map<string, SituationProjection["guidance"]>();
  guidance.forEach((section) => groups.set(section.promptField, [...(groups.get(section.promptField) ?? []), section]));
  return [...groups].map(([field, sections]) => [field, sections.sort((left, right) => left.sectionIndex - right.sectionIndex)] as const);
}

function componentFor(components: ComponentDefinition[], envelope: ComponentEnvelope) {
  return components.find((component) => component.kind === envelope.kind && component.version === envelope.version);
}

function envelope(component: ComponentDefinition): ComponentEnvelope {
  return { kind: component.kind, version: component.version, config: structuredClone(component.defaultConfig) };
}

function observationLabel(id: string): string {
  return capabilityOption(id, OBSERVATION_CAPABILITIES)?.label ?? "Additional imported observation";
}

function componentLabel(component: ComponentDefinition): string {
  if (component.capabilityGroup === "semantic-condition") return "Meaning-based condition";
  if (component.authoringRole === "RULE_RESPONSE" && component.capabilityGroup === "prompt-response") return "Guided response";
  if (component.capabilityGroup === "increment-value") return "Increase a working value";
  if (component.capabilityGroup === "rock-scissor-paper") return component.label.replace(/^RPS /, "Rock, scissor, paper: ");
  return component.label;
}

function conditionSectionLabel(kind: string): string {
  if (kind === "positive-example") return "Positive example";
  if (kind === "negative-example") return "Negative example";
  if (kind === "transition-criterion") return "Decision criterion";
  return "Additional criterion guidance";
}

function schemaStringFields(component: ComponentDefinition | undefined, config: JsonObject) {
  if (!component) return [];
  const properties = component.configSchema.properties;
  if (!properties || typeof properties !== "object" || Array.isArray(properties)) return [];
  return Object.entries(properties).flatMap(([key, raw]) => {
    if (!raw || typeof raw !== "object" || Array.isArray(raw) || (raw as JsonObject).type !== "string") return [];
    const value = config[key];
    return typeof value === "string" ? [{ key, value, label: typeof (raw as JsonObject).title === "string"
      ? String((raw as JsonObject).title) : humanizeCapabilityGroup(key) }] : [];
  });
}

function count(value: number, singular: string): string {
  return `${value} ${value === 1 ? singular : `${singular}s`}`;
}
