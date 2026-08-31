import type { DesignerV2Projection } from "./projection";

interface ProjectionOverviewPanelsProps {
  projection: DesignerV2Projection;
}

export function ProjectionOverviewPanels({ projection }: ProjectionOverviewPanelsProps) {
  return {
    try: <TryOverview projection={projection} />,
  } as const;
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
