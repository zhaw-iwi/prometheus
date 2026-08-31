import type { ComponentDefinition } from "../api/designerApi";
import type { DesignerV2Projection } from "./projection";

interface ProjectionOverviewPanelsProps {
  projection: DesignerV2Projection;
  components: ComponentDefinition[];
}

export function ProjectionOverviewPanels({ projection, components }: ProjectionOverviewPanelsProps) {
  const guidedKinds = new Set(components
    .filter((component) => component.exposure === "GUIDED")
    .map((component) => component.kind));
  return {
    brief: <BriefOverview projection={projection} />,
    capabilities: <CapabilitiesOverview projection={projection} guidedKinds={guidedKinds} />,
    interaction: <InteractionOverview projection={projection} />,
    "data-outcome": <DataOverview projection={projection} />,
    try: <TryOverview projection={projection} />,
  } as const;
}

function BriefOverview({ projection }: { projection: DesignerV2Projection }) {
  const agentGuidance = projection.guidance.filter((item) => item.scope === "agent");
  return <div className="v2-overview" data-testid="brief-overview">
    <IncompleteNotice next="Brief editing" />
    <section className="v2-summary-card" id="brief-display-name" tabIndex={-1}>
      <span className="eyebrow">Current brief</span>
      <h3>{projection.identity.metadata.displayName || "Unnamed agent"}</h3>
      <p>{projection.identity.metadata.description || "No purpose description has been added."}</p>
      <dl className="v2-facts">
        <Fact label="Stable key" value={projection.identity.key || "Not assigned"} />
        <Fact label="Language" value={projection.identity.metadata.languageCode ?? "Not specified"} />
        <Fact label="Agent-wide guidance" value={count(agentGuidance.length, "section")} />
      </dl>
    </section>
  </div>;
}

function CapabilitiesOverview({ projection, guidedKinds }: {
  projection: DesignerV2Projection;
  guidedKinds: Set<string>;
}) {
  const guided = projection.capabilities.installedComponents.filter((component) => guidedKinds.has(component.kind));
  return <div className="v2-overview" data-testid="capabilities-overview">
    <IncompleteNotice next="Capability card editing" />
    <div className="v2-summary-grid">
      <SummaryList title="Can notice" values={projection.capabilities.observations} empty="No observation capabilities declared." />
      <SummaryList title="Can express" values={projection.capabilities.behaviourModalities} empty="No output capabilities declared." />
      <SummaryList title="Installed strategies and operations"
        values={projection.capabilities.installedComponents.map((component) => component.kind)}
        empty="No registered components are used." suffix={guided.length ? `${guided.length} guided` : undefined} />
    </div>
  </div>;
}

function InteractionOverview({ projection }: { projection: DesignerV2Projection }) {
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
            ? `Ordinary response: ${situation.ordinaryPolicy.envelope.kind}`
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

function SummaryList({ title, values, empty, suffix }: {
  title: string;
  values: string[];
  empty: string;
  suffix?: string;
}) {
  return <section className="v2-summary-card">
    <span className="eyebrow">{title}</span>
    <h3>{count(values.length, "capability")}{suffix ? ` · ${suffix}` : ""}</h3>
    {values.length === 0 ? <p>{empty}</p> : <ul className="v2-plain-list">{values.map((value, index) =>
      <li key={`${value}:${index}`}>{value}</li>)}</ul>}
  </section>;
}

function RuleList({ rules }: { rules: DesignerV2Projection["rules"] }) {
  if (rules.length === 0) return null;
  return <ol className="v2-rule-list">{rules.map((rule) => <li id={`interaction-rule-${rule.id}`}
    tabIndex={-1} key={rule.id}>
    <span>{rule.eventTypes.length ? rule.eventTypes.join(", ") : "Registered conditions"}</span>
    <strong>{rule.continuation}</strong>
  </li>)}</ol>;
}

function Fact({ label, value }: { label: string; value: string }) {
  return <div><dt>{label}</dt><dd>{value}</dd></div>;
}

function count(value: number, singular: string): string {
  return `${value} ${value === 1 ? singular : `${singular}s`}`;
}
