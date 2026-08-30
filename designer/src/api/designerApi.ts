export const ADMIN_TOKEN_HEADER = "X-Prometheus-Admin-Token";
export const ADMIN_TOKEN_STORAGE_KEY = "prometheus.valerianAdmin.adminToken";

export type RequestFunction = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;

export interface DefinitionRevisionSummary {
  id: number;
  revision: number;
  status: "DRAFT" | "PUBLISHED" | "ARCHIVED";
  provenance: "BUNDLED" | "DESIGNER" | "IMPORTED";
  optimisticVersion: number;
  updatedAt: string;
}

export interface DefinitionSummary {
  key: string;
  activeRevisionId: number | null;
  activeRevision: number | null;
  optimisticVersion: number;
  displayName: string;
  description: string;
  categoryPath: string[];
  languageCode: string | null;
  revisions: DefinitionRevisionSummary[];
}

export class DesignerApiError extends Error {
  constructor(public readonly status: number) {
    super(status === 401 ? "The admin token was not accepted." : "The designer catalog could not be loaded.");
  }
}

export async function fetchDefinitionCatalog(
  token: string,
  request: RequestFunction = fetch,
): Promise<DefinitionSummary[]> {
  const response = await request("/admin/agent-definitions", {
    headers: { [ADMIN_TOKEN_HEADER]: token },
  });
  if (!response.ok) {
    throw new DesignerApiError(response.status);
  }
  const payload: unknown = await response.json();
  if (!Array.isArray(payload)) {
    throw new DesignerApiError(502);
  }
  return payload as DefinitionSummary[];
}
