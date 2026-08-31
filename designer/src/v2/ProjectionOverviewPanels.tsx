import type { DesignerV2Projection } from "./projection";

interface ProjectionOverviewPanelsProps {
  projection: DesignerV2Projection;
}

export function ProjectionOverviewPanels({ projection }: ProjectionOverviewPanelsProps) {
  return {
    "data-outcome": <DataOverview projection={projection} />,
    try: <TryOverview projection={projection} />,
  } as const;
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

function count(value: number, singular: string): string {
  return `${value} ${value === 1 ? singular : `${singular}s`}`;
}
