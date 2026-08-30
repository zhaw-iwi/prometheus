import type { AgentDefinitionV1, JsonObject } from "../model/agentDefinition";

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

export interface ComponentCapabilities {
  consumedObservations: string[];
  emittedBehaviourModalities: string[];
  storage: unknown[];
  resources: unknown[];
  states: unknown[];
}

export interface ComponentDefinition {
  kind: string;
  version: number;
  category: "POLICY" | "SELECTOR" | "DECISION" | "ACTION" | "INITIALIZER" | "RESOURCE";
  configSchema: JsonObject;
  label: string;
  description: string;
  defaultConfig: JsonObject;
  examples: JsonObject[];
  capabilities: ComponentCapabilities;
}

export interface DefinitionRevisionView {
  id: number;
  key: string;
  revision: number;
  schemaVersion: number;
  status: "DRAFT" | "PUBLISHED" | "ARCHIVED";
  contentHash: string;
  provenance: "BUNDLED" | "DESIGNER" | "IMPORTED";
  sourceDetail: string;
  optimisticVersion: number;
  createdAt: string;
  updatedAt: string;
  publishedAt: string | null;
  archivedAt: string | null;
  definition: AgentDefinitionV1;
}

export interface DefinitionDiagnostic {
  code: string;
  severity: "ERROR" | "WARNING";
  pointer: string;
  message: string;
  hint: string | null;
}

export interface DefinitionValidationResult {
  valid: boolean;
  diagnostics: DefinitionDiagnostic[];
}

interface ApiErrorPayload {
  code?: unknown;
  diagnostics?: unknown;
}

export class DesignerApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code = "REQUEST_FAILED",
    public readonly diagnostics: DefinitionDiagnostic[] = [],
  ) {
    super(apiMessage(status, code));
  }
}

export async function fetchDefinitionCatalog(
  token: string,
  request: RequestFunction = fetch,
): Promise<DefinitionSummary[]> {
  return getArray("/admin/agent-definitions", token, request);
}

export async function fetchComponentCatalog(
  token: string,
  request: RequestFunction = fetch,
): Promise<ComponentDefinition[]> {
  return getArray("/admin/agent-definitions/component-catalog", token, request);
}

export async function fetchDesignerWorkspace(
  token: string,
  request: RequestFunction = fetch,
): Promise<{ definitions: DefinitionSummary[]; components: ComponentDefinition[] }> {
  const [definitions, components] = await Promise.all([
    fetchDefinitionCatalog(token, request),
    fetchComponentCatalog(token, request),
  ]);
  return { definitions, components };
}

export async function fetchDefinitionRevision(
  key: string,
  revision: number,
  token: string,
  request: RequestFunction = fetch,
): Promise<DefinitionRevisionView> {
  return requestJson(`/admin/agent-definitions/${encodeURIComponent(key)}/revisions/${revision}`, token, request);
}

export async function createDefinitionDraft(
  definition: AgentDefinitionV1,
  token: string,
  request: RequestFunction = fetch,
): Promise<DefinitionRevisionView> {
  return requestJson("/admin/agent-definitions", token, request, {
    method: "POST",
    body: JSON.stringify({ definition }),
  });
}

export async function updateDefinitionDraft(
  definition: AgentDefinitionV1,
  optimisticVersion: number,
  token: string,
  request: RequestFunction = fetch,
): Promise<DefinitionRevisionView> {
  return requestJson(
    `/admin/agent-definitions/${encodeURIComponent(definition.key)}/revisions/${definition.revision}`,
    token,
    request,
    { method: "PUT", body: JSON.stringify({ definition, optimisticVersion }) },
  );
}

export async function validateDefinition(
  definition: AgentDefinitionV1,
  token: string,
  request: RequestFunction = fetch,
): Promise<DefinitionValidationResult> {
  return requestJson("/admin/agent-definitions/validation", token, request, {
    method: "POST",
    body: JSON.stringify({ definition }),
  });
}

async function getArray<T>(path: string, token: string, request: RequestFunction): Promise<T[]> {
  const payload = await requestJson<unknown>(path, token, request);
  if (!Array.isArray(payload)) throw new DesignerApiError(502);
  return payload as T[];
}

async function requestJson<T>(
  path: string,
  token: string,
  request: RequestFunction,
  init: RequestInit = {},
): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set(ADMIN_TOKEN_HEADER, token);
  if (init.body) headers.set("Content-Type", "application/json");
  const response = await request(path, { ...init, headers });
  if (!response.ok) throw await apiError(response);
  return await response.json() as T;
}

async function apiError(response: Response): Promise<DesignerApiError> {
  if (response.status === 401) return new DesignerApiError(401, "UNAUTHORIZED");
  let payload: ApiErrorPayload = {};
  try {
    payload = await response.json() as ApiErrorPayload;
  } catch {
    // Non-JSON upstream failures are intentionally not reflected to the UI.
  }
  const code = typeof payload.code === "string" ? payload.code : "REQUEST_FAILED";
  const diagnostics = Array.isArray(payload.diagnostics)
    ? payload.diagnostics.filter(isDiagnostic)
    : [];
  return new DesignerApiError(response.status, code, diagnostics);
}

function isDiagnostic(value: unknown): value is DefinitionDiagnostic {
  if (typeof value !== "object" || value === null) return false;
  const diagnostic = value as Record<string, unknown>;
  return typeof diagnostic.code === "string" && typeof diagnostic.severity === "string"
    && typeof diagnostic.pointer === "string" && typeof diagnostic.message === "string";
}

function apiMessage(status: number, code: string): string {
  if (status === 401) return "The admin token was not accepted.";
  if (code === "OPTIMISTIC_CONFLICT") return "This draft changed after it was loaded.";
  if (code === "SCHEMA_VALIDATION_FAILED" || code === "VALIDATION_FAILED") {
    return "The backend found definition issues that need attention.";
  }
  if (code === "LIFECYCLE_CONFLICT") return "This revision cannot be changed in its current lifecycle state.";
  return "The designer request could not be completed.";
}
