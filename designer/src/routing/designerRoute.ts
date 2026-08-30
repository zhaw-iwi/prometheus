export type DesignerRoute =
  | { kind: "catalog" }
  | { kind: "new" }
  | { kind: "editor"; key: string; revision: number };

export function parseDesignerRoute(hash: string): DesignerRoute {
  const normalized = hash.replace(/^#\/?/, "");
  if (normalized === "new") {
    return { kind: "new" };
  }
  const parts = normalized.split("/");
  if (parts.length === 4 && parts[0] === "definitions" && parts[2] === "revisions") {
    const revision = Number(parts[3]);
    if (Number.isInteger(revision) && revision > 0) {
      return { kind: "editor", key: decodeURIComponent(parts[1]), revision };
    }
  }
  return { kind: "catalog" };
}

export function designerRouteHash(route: DesignerRoute): string {
  if (route.kind === "new") {
    return "#/new";
  }
  if (route.kind === "editor") {
    return `#/definitions/${encodeURIComponent(route.key)}/revisions/${route.revision}`;
  }
  return "#/catalog";
}
