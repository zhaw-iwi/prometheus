import type { ComponentDefinition } from "../api/designerApi";
import type { DesignerV2Projection } from "./projection";

interface ProjectionOverviewPanelsProps {
  projection: DesignerV2Projection;
  components: ComponentDefinition[];
}

export function ProjectionOverviewPanels({ projection, components }: ProjectionOverviewPanelsProps) {
  return {
    interaction: <InteractionOverview projection={projection} components={components} />,
    "data-outcome": <DataOverview projection={projection} />,
    try: <TryOverview projection={projection} />,
  } as const;
}

function InteractionOverview({ projection, components }: { projection: DesignerV2Projection; components: ComponentDefinition[] }) {
  const globals = projection.rules.filter((rule) => rule.scope === "global");
  return <div className="v2-overview" data-testid="interaction-overview">
    <IncompleteNotice next="Interaction storyboard editing" />
    {globals.length > 0 && <section className="v2-summary-card">
      <span className="eyebrow">Always</span>
      <h3>{count(globals.length, "interaction rule")}</h3>
      <RuleList rules={globals} />
    </section>}
    <div className="v2-summary-grid">
      {projection.situations.map((situation) => {
        const rules = projection.rules.filter((rule) => rule.sourceStateId === situation.id);
        return <section className="v2-summary-card" id={`interaction-situation-${situation.id}`}
          tabIndex={-1} key={situation.id}>
          <span className="eyebrow">{situation.main ? "Main interaction" : "Situation"}</span>
          <h3>{situation.name}</h3>
          <p>{situation.ordinaryPolicy
            ? `Ordinary response: ${components.find((component) => component.kind === situation.ordinaryPolicy?.envelope.kind
              && component.version === situation.ordinaryPolicy.envelope.version)?.label ?? "Registered strategy"}`
            : "No ordinary response strategy."}</p>
          <p>{count(situation.guidance.length, "guidance section")} · {count(rules.length, "rule")}</p>
          <RuleList rules={rules} />
        </section>;
      })}
    </div>
  </div>;
}

function DataOverview({ projection }: { projection: DesignerV2Projection }) {
  const roles = ["starting-context", "working-data", "learned-information", "outcome-report"] as const;
  const labels = {
    "starting-context": "Starting context",
    "working-data": "Working data",
    "learned-information": "Learned information",
    "outcome-report": "Outcome report",
  };
  return <div className="v2-overview" data-testid="data-outcome-overview">
    <IncompleteNotice next="Data and outcome editing" />
    <div className="v2-summary-grid">
      {roles.map((role) => {
        const items = projection.data.items.filter((item) => item.role === role);
        return <section className="v2-summary-card" key={role}>
          <span className="eyebrow">{labels[role]}</span>
          <h3>{count(items.length, "item")}</h3>
          {items.length === 0 ? <p>None defined.</p> : <ul className="v2-plain-list">{items.map((item) =>
            <li id={`data-item-${item.key}`} tabIndex={-1} key={item.key}><code>{item.key}</code></li>)}</ul>}
        </section>;
      })}
    </div>
    <p className="v2-preservation-note">{count(projection.data.resources.length, "resource")} and {count(projection.data.initializers.length, "initializer")} remain preserved in canonical JSON.</p>
  </div>;
}

function TryOverview({ projection }: { projection: DesignerV2Projection }) {
  return <div className="v2-overview" data-testid="try-overview">
    <IncompleteNotice next="Given / When / Expect scenario editing" />
    <section className="v2-summary-card">
      <span className="eyebrow">Verification scenarios</span>
      <h3>{count(projection.verification.scenarios.length, "scenario")}</h3>
      <p>Existing scenarios remain unchanged and available in the canonical document.</p>
    </section>
  </div>;
}

function IncompleteNotice({ next }: { next: string }) {
  return <div className="v2-incomplete" role="status">
    <strong>Safe read-only projection</strong>
    <p>{next} arrives in its dedicated V2 milestone. Navigation and export do not rewrite this document.</p>
  </div>;
}

function RuleList({ rules }: { rules: DesignerV2Projection["rules"] }) {
  if (rules.length === 0) return null;
  return <ol className="v2-rule-list">{rules.map((rule) => <li id={`interaction-rule-${rule.id}`}
    tabIndex={-1} key={rule.id}>
    <span>{rule.eventTypes.length ? "When a declared event occurs" : "Registered conditions"}</span>
    <strong>{rule.continuation}</strong>
  </li>)}</ol>;
}

function count(value: number, singular: string): string {
  return `${value} ${value === 1 ? singular : `${singular}s`}`;
}
