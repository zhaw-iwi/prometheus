import { useEffect, useMemo, useRef, useState } from "react";
import {
  Background,
  Controls,
  MarkerType,
  MiniMap,
  ReactFlow,
  type Connection,
  type Edge,
  type Node,
  type ReactFlowInstance,
} from "@xyflow/react";
import type { ComponentDefinition, DefinitionDiagnostic } from "../api/designerApi";
import {
  type AgentDefinitionV1,
  type CompositeStateDefinition,
  type StateDefinition,
  type TransitionDefinition,
} from "../model/agentDefinition";
import { ComponentEnvelopeEditor } from "./ComponentEnvelopeEditor";
import {
  addState,
  addTransition,
  assignParent,
  deleteState,
  deleteTransition,
  expandDefaultState,
  moveCompositeChild,
  moveState,
  moveTransition,
  parentId,
  renameState,
  replaceState,
  replaceTransition,
  setInitialChild,
  setInitialState,
} from "./graphModel";
import { EnvelopeListEditor } from "./ReactionPanel";

interface StateFlowPanelProps {
  definition: AgentDefinitionV1;
  components: ComponentDefinition[];
  diagnostics: DefinitionDiagnostic[];
  onChange: (definition: AgentDefinitionV1) => void;
}

export function StateFlowPanel({ definition, components, diagnostics, onChange }: StateFlowPanelProps) {
  const [view, setView] = useState<"graph" | "list">("graph");
  const [selectedStateId, setSelectedStateId] = useState<string | null>(definition.lifecycle.initialStateId);
  const [selectedTransitionId, setSelectedTransitionId] = useState<string | null>(null);
  const [flowInstance, setFlowInstance] = useState<ReactFlowInstance | null>(null);
  const graphContainer = useRef<HTMLDivElement>(null);
  const errors = diagnosticGraphTargets(definition, diagnostics);
  const elements = useMemo(() => graphElements(definition, errors.stateIds, errors.transitionIds), [definition, errors]);
  const layoutSignature = definition.states.map((state) => `${state.id}:${state.kind}:${parentId(definition, state.id) ?? "top"}:${state.kind === "composite" ? state.childStateIds.join(",") : ""}`)
    .join("|");
  const selectedState = definition.states.find((state) => state.id === selectedStateId) ?? null;
  const selectedTransition = definition.transitions.find((transition) => transition.id === selectedTransitionId) ?? null;

  useEffect(() => {
    if (!flowInstance || !graphContainer.current || view !== "graph") return;
    const fitVisibleGraph = () => {
      const container = graphContainer.current;
      if (!container || container.clientWidth < 100 || container.clientHeight < 100) return;
      requestAnimationFrame(() => void flowInstance.fitView({ padding: 0.16, maxZoom: 1.15 }));
    };
    const observer = new ResizeObserver(fitVisibleGraph);
    observer.observe(graphContainer.current);
    fitVisibleGraph();
    return () => observer.disconnect();
  }, [flowInstance, layoutSignature, view]);

  const selectState = (stateId: string) => {
    setSelectedStateId(stateId);
    setSelectedTransitionId(null);
  };
  const selectTransition = (transitionId: string) => {
    setSelectedTransitionId(transitionId);
    setSelectedStateId(null);
  };
  const addSituation = (kind: StateDefinition["kind"]) => {
    const before = new Set(definition.states.map((state) => state.id));
    const next = addState(definition, kind);
    onChange(next);
    const added = next.states.find((state) => !before.has(state.id));
    if (added) selectState(added.id);
  };
  const addMove = (sourceId?: string, targetId?: string) => {
    const source = sourceId ?? (selectedState && selectedState.kind !== "final" ? selectedState.id : undefined);
    const fallbackSource = definition.states.find((state) => state.kind !== "final")?.id;
    const actualSource = source || fallbackSource;
    const actualTarget = targetId || actualSource;
    if (!actualSource || !actualTarget) return;
    const next = addTransition(definition, actualSource, actualTarget);
    onChange(next);
    const added = next.transitions.at(-1);
    if (added) selectTransition(added.id);
  };
  const connect = (connection: Connection) => {
    if (connection.source && connection.target) addMove(connection.source, connection.target);
  };

  return (
    <div className="authoring-panel state-flow-panel" data-testid="state-flow-authoring-panel">
      <section className="form-section graph-section">
        <div className="section-heading">
          <div><h3>Does behaviour change across situations?</h3>
            <p>{definition.states.length === 1
              ? "The explicit main state is shown as one default situation until you add another."
              : `${definition.states.length} situations share one canonical state graph.`}</p></div>
          <div className="graph-toolbar">
            {definition.states.length === 1 && <button className="button secondary" type="button"
              onClick={() => onChange(expandDefaultState(definition))} data-testid="expand-default-state">Add another situation</button>}
            <button className="button secondary" type="button" onClick={() => addSituation("atomic")}>Add situation</button>
            <button className="button secondary" type="button" onClick={() => addSituation("composite")}>Add group</button>
            <button className="button secondary" type="button" onClick={() => addSituation("final")}>Add finished</button>
            <button className="button primary" type="button" onClick={() => addMove()}>Add move</button>
          </div>
        </div>

        {errors.targets.length > 0 && (
          <div className="graph-diagnostic-targets" aria-label="State-flow validation targets">
            {errors.targets.map((target) => (
              <button id={target.fieldId} type="button" key={target.fieldId}
                onFocus={() => { setView("list"); target.kind === "state" ? selectState(target.id) : selectTransition(target.id); }}
                onClick={() => { setView("list"); target.kind === "state" ? selectState(target.id) : selectTransition(target.id); }}>
                {target.kind === "state" ? "Situation" : "Move"} {target.id} has a validation issue
              </button>
            ))}
          </div>
        )}

        <div className="view-switch" role="group" aria-label="State flow representation">
          <button className={view === "graph" ? "active" : ""} type="button" aria-pressed={view === "graph"}
            onClick={() => setView("graph")} data-testid="show-graph-view">Visual graph</button>
          <button className={view === "list" ? "active" : ""} type="button" aria-pressed={view === "list"}
            onClick={() => setView("list")} data-testid="show-list-view">Keyboard list</button>
        </div>

        {view === "graph" ? (
          <div className="state-graph" data-testid="state-graph" ref={graphContainer}>
            <ReactFlow nodes={elements.nodes} edges={elements.edges} fitView minZoom={0.25} maxZoom={1.5}
              onInit={(instance) => setFlowInstance(instance)}
              nodesDraggable={false} onConnect={connect}
              onNodeClick={(_, node) => selectState(node.id)} onEdgeClick={(_, edge) => selectTransition(edge.id)}>
              <MiniMap pannable zoomable nodeColor={(node) => node.data.kind === "final" ? "#64748b"
                : node.data.kind === "composite" ? "#0f766e" : "#256b73"} />
              <Controls showInteractive={false} />
              <Background gap={22} size={1} />
            </ReactFlow>
          </div>
        ) : (
          <StateFlowList definition={definition} errorStateIds={errors.stateIds} errorTransitionIds={errors.transitionIds}
            onChange={onChange} onSelectState={selectState} onSelectTransition={selectTransition} />
        )}
      </section>

      {(selectedState || selectedTransition) && (
        <section className="form-section graph-inspector" data-testid="graph-inspector">
          <div className="section-heading"><div><h3>Inspector</h3><p>Edit the selected canonical graph element.</p></div></div>
          {selectedState && <StateInspector state={selectedState} definition={definition} components={components}
            onChange={onChange} />}
          {selectedTransition && <TransitionInspector transition={selectedTransition} definition={definition}
            components={components} onChange={onChange} />}
        </section>
      )}
    </div>
  );
}

function StateFlowList({ definition, errorStateIds, errorTransitionIds, onChange, onSelectState, onSelectTransition }: {
  definition: AgentDefinitionV1;
  errorStateIds: Set<string>;
  errorTransitionIds: Set<string>;
  onChange: (definition: AgentDefinitionV1) => void;
  onSelectState: (stateId: string) => void;
  onSelectTransition: (transitionId: string) => void;
}) {
  return (
    <div className="state-flow-list" data-testid="state-flow-list">
      <table><caption>Situations in document order</caption><thead><tr><th>Situation</th><th>Kind</th><th>Contained by</th><th>Operations</th></tr></thead>
        <tbody>{definition.states.map((state) => (
          <tr key={state.id} className={errorStateIds.has(state.id) ? "has-error" : ""}>
            <th scope="row"><button type="button" onClick={() => onSelectState(state.id)}>{state.name}<small>{state.id}</small></button></th>
            <td>{plainKind(state.kind)}{definition.lifecycle.initialStateId === state.id ? " · Initial" : ""}</td>
            <td>{parentId(definition, state.id) ?? "—"}</td>
            <td><div className="compact-actions">
              <button type="button" aria-label={`Edit situation ${state.id}`} onClick={() => onSelectState(state.id)}>Edit</button>
              <button type="button" aria-label={`Move situation ${state.id} earlier`} onClick={() => onChange(moveState(definition, state.id, -1))}>↑</button>
              <button type="button" aria-label={`Move situation ${state.id} later`} onClick={() => onChange(moveState(definition, state.id, 1))}>↓</button>
              <button type="button" aria-label={`Delete situation ${state.id}`} disabled={definition.states.length <= 1}
                onClick={() => onChange(deleteState(definition, state.id))}>Delete</button>
            </div></td>
          </tr>
        ))}</tbody>
      </table>
      <table><caption>Moves in source priority order</caption><thead><tr><th>Move</th><th>From</th><th>To</th><th>Priority</th><th>Operations</th></tr></thead>
        <tbody>{definition.transitions.map((transition) => (
          <tr key={transition.id} className={errorTransitionIds.has(transition.id) ? "has-error" : ""}>
            <th scope="row"><button type="button" onClick={() => onSelectTransition(transition.id)}>{transition.id}</button></th>
            <td>{transition.sourceStateId}</td><td>{transition.targetStateId}</td><td>{transition.order}</td>
            <td><div className="compact-actions">
              <button type="button" aria-label={`Edit move ${transition.id}`} onClick={() => onSelectTransition(transition.id)}>Edit</button>
              <button type="button" aria-label={`Move ${transition.id} earlier`} onClick={() => onChange(moveTransition(definition, transition.id, -1))}>↑</button>
              <button type="button" aria-label={`Move ${transition.id} later`} onClick={() => onChange(moveTransition(definition, transition.id, 1))}>↓</button>
              <button type="button" aria-label={`Delete move ${transition.id}`} onClick={() => onChange(deleteTransition(definition, transition.id))}>Delete</button>
            </div></td>
          </tr>
        ))}</tbody>
      </table>
    </div>
  );
}

function StateInspector({ state, definition, components, onChange }: {
  state: StateDefinition;
  definition: AgentDefinitionV1;
  components: ComponentDefinition[];
  onChange: (definition: AgentDefinitionV1) => void;
}) {
  const currentParent = parentId(definition, state.id) ?? "";
  const update = (replacement: StateDefinition) => onChange(replaceState(definition, replacement));
  const availableParents = definition.states.filter((candidate) => candidate.kind === "composite" && candidate.id !== state.id);
  return (
    <div className="inspector-fields">
      <div className="field-grid two-columns">
        <label><span>Stable ID</span><input value={state.id} readOnly /><small>References use this ID, not the display name.</small></label>
        <label><span>Situation name</span><input value={state.name}
          onChange={(event) => onChange(renameState(definition, state.id, event.target.value))} /></label>
        <label><span>Contained by</span><select value={currentParent}
          onChange={(event) => onChange(assignParent(definition, state.id, event.target.value || null))}>
          <option value="">Top level</option>
          {availableParents.map((candidate) => <option value={candidate.id} key={candidate.id}>{candidate.name}</option>)}
        </select></label>
        <label className="schema-checkbox"><input type="checkbox" checked={definition.lifecycle.initialStateId === state.id}
          disabled={Boolean(currentParent)} onChange={() => onChange(setInitialState(definition, state.id))} />
          <span><strong>Initial top-level situation</strong><small>Only top-level situations can start the flow.</small></span></label>
      </div>
      {state.kind !== "final" && (
        <>
          <div className="field-grid two-columns">
            <label><span>On entry</span><select value={state.entryMode}
              onChange={(event) => update({ ...state, entryMode: event.target.value as "start" | "reprocess-event" })}>
              <option value="start">Generate starter behaviour</option><option value="reprocess-event">Reprocess triggering event</option>
            </select></label>
            <label className="schema-checkbox"><input type="checkbox" checked={state.oblivious}
              onChange={(event) => update({ ...state, oblivious: event.target.checked })} />
              <span><strong>Clear prior state events</strong><small>Oblivious situations do not retain previous state history.</small></span></label>
          </div>
          <div className="advanced-columns">
            <ComponentEnvelopeEditor envelope={state.eventSelector} category="SELECTOR" components={components}
              label="Event/history selection" nullable onChange={(eventSelector) => update({ ...state, eventSelector })} />
            <ComponentEnvelopeEditor envelope={state.policy} category="POLICY" components={components}
              label="Situation response strategy" nullable onChange={(policy) => update({ ...state, policy })} />
          </div>
        </>
      )}
      {state.kind === "composite" && <CompositeChildren state={state} definition={definition} onChange={onChange} />}
      {state.kind === "final" && <div className="empty-callout">Finished situations have no policy or outgoing moves.</div>}
    </div>
  );
}

function CompositeChildren({ state, definition, onChange }: {
  state: CompositeStateDefinition;
  definition: AgentDefinitionV1;
  onChange: (definition: AgentDefinitionV1) => void;
}) {
  return (
    <section className="composite-children"><div className="list-heading"><strong>Contained situations</strong>
      <label>Initial child <select value={state.initialChildStateId}
        onChange={(event) => onChange(setInitialChild(definition, state.id, event.target.value))}>
        {state.childStateIds.map((childId) => <option key={childId}>{childId}</option>)}
      </select></label></div>
      <ol>{state.childStateIds.map((childId, index) => <li key={childId}>
        <span>{definition.states.find((candidate) => candidate.id === childId)?.name ?? childId}
          {state.initialChildStateId === childId && <small>Initial child</small>}</span>
        <div className="compact-actions">
          <button type="button" aria-label={`Move child ${childId} earlier`}
            onClick={() => onChange(moveCompositeChild(definition, state.id, childId, -1))}>↑</button>
          <button type="button" aria-label={`Move child ${childId} later`}
            onClick={() => onChange(moveCompositeChild(definition, state.id, childId, 1))}>↓</button>
          <button type="button" onClick={() => onChange(assignParent(definition, childId, null))}>Remove</button>
        </div></li>)}</ol>
      {state.childStateIds.length === 0 && <p>Assign at least one situation to this group before saving.</p>}
    </section>
  );
}

function TransitionInspector({ transition, definition, components, onChange }: {
  transition: TransitionDefinition;
  definition: AgentDefinitionV1;
  components: ComponentDefinition[];
  onChange: (definition: AgentDefinitionV1) => void;
}) {
  const update = (replacement: TransitionDefinition) => onChange(replaceTransition(definition, replacement));
  return (
    <div className="inspector-fields">
      <div className="field-grid three-columns">
        <label><span>Stable move ID</span><input value={transition.id} readOnly /></label>
        <label><span>From situation</span><select value={transition.sourceStateId}
          onChange={(event) => update({ ...transition, sourceStateId: event.target.value })}>
          {definition.states.filter((state) => state.kind !== "final").map((state) => <option value={state.id} key={state.id}>{state.name}</option>)}
        </select></label>
        <label><span>To situation</span><select value={transition.targetStateId}
          onChange={(event) => update({ ...transition, targetStateId: event.target.value })}>
          {definition.states.map((state) => <option value={state.id} key={state.id}>{state.name}</option>)}
        </select></label>
      </div>
      <p className="priority-help">Priority {transition.order} within {transition.sourceStateId}; use the arrow operations to change first-match order.</p>
      <div className="advanced-columns">
        <EnvelopeListEditor label="Ordered conditions" category="DECISION" envelopes={transition.decisions}
          components={components} onChange={(decisions) => update({ ...transition, decisions })} />
        <EnvelopeListEditor label="Ordered actions" category="ACTION" envelopes={transition.actions}
          components={components} onChange={(actions) => update({ ...transition, actions })} />
      </div>
    </div>
  );
}

export function graphElements(
  definition: AgentDefinitionV1,
  errorStateIds = new Set<string>(),
  errorTransitionIds = new Set<string>(),
): { nodes: Node[]; edges: Edge[] } {
  const topLevel = definition.states.filter((state) => !parentId(definition, state.id));
  const sizes = new Map<string, { width: number; height: number }>();
  const sizeOf = (state: StateDefinition, visiting = new Set<string>()): { width: number; height: number } => {
    const cached = sizes.get(state.id);
    if (cached) return cached;
    if (state.kind !== "composite" || visiting.has(state.id)) return { width: 220, height: 70 };
    const nested = new Set(visiting).add(state.id);
    const childSizes = state.childStateIds.map((childId) => definition.states.find((candidate) => candidate.id === childId))
      .filter((child): child is StateDefinition => child !== undefined).map((child) => sizeOf(child, nested));
    const size = {
      width: Math.max(340, 48 + Math.max(220, ...childSizes.map((child) => child.width))),
      height: Math.max(180, 92 + childSizes.reduce((total, child) => total + child.height + 18, 0)),
    };
    sizes.set(state.id, size);
    return size;
  };
  definition.states.forEach((state) => sizeOf(state));
  const positions = new Map<string, { x: number; y: number }>();
  let topLevelX = 50;
  topLevel.forEach((state) => {
    positions.set(state.id, { x: topLevelX, y: 50 });
    topLevelX += (sizes.get(state.id)?.width ?? 220) + 100;
  });
  const orderedStates = [...definition.states].sort((left, right) => Number(Boolean(parentId(definition, left.id)))
    - Number(Boolean(parentId(definition, right.id))));
  const nodes: Node[] = orderedStates.map((state, index) => {
    const parent = parentId(definition, state.id);
    const parentState = parent ? definition.states.find((candidate) => candidate.id === parent) : null;
    const childIndex = parentState?.kind === "composite" ? parentState.childStateIds.indexOf(state.id) : 0;
    const precedingChildHeight = parentState?.kind === "composite"
      ? parentState.childStateIds.slice(0, Math.max(0, childIndex)).reduce((total, childId) => {
          const child = definition.states.find((candidate) => candidate.id === childId);
          return total + (child ? sizeOf(child).height : 70) + 18;
        }, 0) : 0;
    const isComposite = state.kind === "composite";
    const size = sizeOf(state);
    const initial = definition.lifecycle.initialStateId === state.id
      || (parentState?.kind === "composite" && parentState.initialChildStateId === state.id);
    return {
      id: state.id,
      parentId: parent ?? undefined,
      extent: parent ? "parent" as const : undefined,
      position: parent ? { x: 24, y: 76 + precedingChildHeight }
        : positions.get(state.id) ?? { x: 50 + index * 240, y: 50 },
      data: {
        kind: state.kind,
        label: <div className="graph-node-label"><strong>{state.name}</strong><small>{state.id} · {plainKind(state.kind)}</small>
          {initial && <span>Initial{parent ? " child" : ""}</span>}</div>,
      },
      className: `situation-node ${state.kind}${errorStateIds.has(state.id) ? " has-error" : ""}`,
      style: isComposite ? { width: size.width, height: size.height }
        : { width: size.width, minHeight: size.height },
    };
  });
  const edges: Edge[] = definition.transitions.map((transition) => ({
    id: transition.id,
    source: transition.sourceStateId,
    target: transition.targetStateId,
    label: `P${transition.order}`,
    ariaLabel: `Move ${transition.id}, priority ${transition.order}`,
    type: "smoothstep",
    markerEnd: { type: MarkerType.ArrowClosed },
    labelStyle: { fontSize: 10, fontWeight: 700 },
    labelBgPadding: [4, 2],
    labelBgBorderRadius: 4,
    labelBgStyle: { fill: "#ffffff", fillOpacity: 0.88 },
    className: errorTransitionIds.has(transition.id) ? "has-error" : "",
    style: errorTransitionIds.has(transition.id) ? { stroke: "#b42318", strokeWidth: 3 } : undefined,
  }));
  return { nodes, edges };
}

function diagnosticGraphTargets(definition: AgentDefinitionV1, diagnostics: DefinitionDiagnostic[]) {
  const stateIds = new Set<string>();
  const transitionIds = new Set<string>();
  const targets: Array<{ fieldId: string; kind: "state" | "transition"; id: string }> = [];
  for (const diagnostic of diagnostics) {
    const stateMatch = diagnostic.pointer.match(/^\/states\/(\d+)/);
    const transitionMatch = diagnostic.pointer.match(/^\/transitions\/(\d+)/);
    if (stateMatch) {
      const id = definition.states[Number(stateMatch[1])]?.id;
      if (id && !stateIds.has(id)) {
        stateIds.add(id);
        targets.push({ fieldId: `graph-diagnostic-state-${id}`, kind: "state", id });
      }
    }
    if (transitionMatch) {
      const id = definition.transitions[Number(transitionMatch[1])]?.id;
      if (id && !transitionIds.has(id)) {
        transitionIds.add(id);
        targets.push({ fieldId: `graph-diagnostic-transition-${id}`, kind: "transition", id });
      }
    }
  }
  return { stateIds, transitionIds, targets };
}

function plainKind(kind: StateDefinition["kind"]): string {
  return kind === "atomic" ? "Situation" : kind === "composite" ? "Group" : "Finished";
}
