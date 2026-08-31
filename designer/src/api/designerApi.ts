import type { AgentDefinitionV1, JsonObject, JsonValue } from "../model/agentDefinition";

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
  authoringRole: "RESPONSE_STRATEGY" | "RULE_TRIGGER" | "RULE_CONDITION" | "RULE_RESPONSE"
    | "DETERMINISTIC_OPERATION" | "DATA_UPDATE" | "DATA_INITIALIZER" | "DATA_RESOURCE"
    | "OUTCOME_EXTRACTION" | "TECHNICAL_SELECTOR";
  exposure: "GUIDED" | "ADVANCED" | "GENERATED_INTERNAL";
  capabilityGroup: string | null;
  advancedReason: string | null;
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

export interface PromptPreview {
  pointer: string;
  label: string;
  composed: string;
}

export interface PreviewEvent {
  type: string;
  actor: string | null;
  kind: string | null;
  payload: string | null;
}

export interface PreviewStorageChange {
  before: JsonValue | null;
  after: JsonValue | null;
}

export interface PreviewBehaviour {
  speech: string | null;
  nonVerbal: JsonValue | null;
  motion: JsonValue | null;
  display: JsonValue | null;
}

export interface PreviewOperation {
  sequence: number;
  kind: "CREATE" | "EVENT" | "GENERATE" | "RESET";
  at: string;
  input: PreviewEvent | null;
  activeStatePath: string[];
  storageChanges: Record<string, PreviewStorageChange>;
  acceptedTransitionIds: string[];
  behaviour: PreviewBehaviour | null;
  diagnostics: Array<{ code: string; message: string; hint: string | null }>;
}

export interface PreviewSnapshot {
  id: string;
  source: "UNSAVED" | "SAVED";
  storedRevisionId: number | null;
  definitionKey: string;
  definitionRevision: number;
  createdAt: string;
  lastAccessedAt: string;
  expiresAt: string;
  activeStatePath: string[];
  storage: Record<string, JsonValue>;
  history: PreviewEvent[];
  started: boolean;
  active: boolean;
  transcript: PreviewOperation[];
  diagnostics: Array<{ code: string; message: string; hint: string | null }>;
}

export interface ScenarioExpectationResult {
  id: string;
  label: string;
  passed: boolean;
  expected: JsonValue;
  actual: JsonValue;
  explanation: string;
}

export interface ScenarioStorageChange {
  sequence: number;
  key: string;
  before: JsonValue | null;
  after: JsonValue | null;
}

export interface ScenarioExecutionResult {
  scenarioIndex: number;
  name: string;
  passed: boolean;
  expectations: ScenarioExpectationResult[];
  activeStatePath: string[];
  storage: Record<string, JsonValue>;
  acceptedTransitionIds: string[];
  storageChanges: ScenarioStorageChange[];
  emittedModalities: string[];
  transcript: PreviewOperation[];
  diagnostics: Array<{ code: string; message: string; hint: string | null }>;
  discarded: boolean;
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

export async function validateDefinitionForPublication(
  definition: AgentDefinitionV1,
  token: string,
  request: RequestFunction = fetch,
): Promise<DefinitionValidationResult> {
  return requestJson("/admin/agent-definitions/publication-readiness", token, request, {
    method: "POST",
    body: JSON.stringify({ definition }),
  });
}

export async function fetchPromptPreviews(
  definition: AgentDefinitionV1,
  token: string,
  request: RequestFunction = fetch,
): Promise<PromptPreview[]> {
  return requestJson("/admin/agent-definitions/prompt-previews", token, request, {
    method: "POST",
    body: JSON.stringify({ definition }),
  });
}

export async function importDefinitionDraft(
  definition: AgentDefinitionV1,
  token: string,
  request: RequestFunction = fetch,
): Promise<DefinitionRevisionView> {
  return requestJson("/admin/agent-definitions/imports", token, request, {
    method: "POST",
    body: JSON.stringify({ definition }),
  });
}

export async function exportDefinitionRevision(
  key: string,
  revision: number,
  token: string,
  request: RequestFunction = fetch,
): Promise<AgentDefinitionV1> {
  return requestJson(`/admin/agent-definitions/${encodeURIComponent(key)}/revisions/${revision}/export`, token, request);
}

export async function publishDefinitionRevision(
  key: string,
  revision: number,
  optimisticVersion: number,
  token: string,
  request: RequestFunction = fetch,
): Promise<DefinitionRevisionView> {
  return revisionAction(key, revision, "publish", optimisticVersion, token, request);
}

export async function archiveDefinitionRevision(
  key: string,
  revision: number,
  optimisticVersion: number,
  token: string,
  request: RequestFunction = fetch,
): Promise<DefinitionRevisionView> {
  return revisionAction(key, revision, "archive", optimisticVersion, token, request);
}

export async function activateDefinitionRevision(
  key: string,
  revision: number,
  definitionOptimisticVersion: number,
  token: string,
  request: RequestFunction = fetch,
): Promise<DefinitionSummary> {
  return requestJson(`/admin/agent-definitions/${encodeURIComponent(key)}/revisions/${revision}/activate`, token, request, {
    method: "POST",
    body: JSON.stringify({ optimisticVersion: definitionOptimisticVersion }),
  });
}

export async function cloneDefinitionRevision(
  key: string,
  revision: number,
  targetKey: string,
  targetRevision: number,
  token: string,
  request: RequestFunction = fetch,
): Promise<DefinitionRevisionView> {
  return requestJson(`/admin/agent-definitions/${encodeURIComponent(key)}/revisions/${revision}/clone`, token, request, {
    method: "POST",
    body: JSON.stringify({ targetKey, targetRevision }),
  });
}

export async function createDefinitionPreview(
  definition: AgentDefinitionV1,
  token: string,
  request: RequestFunction = fetch,
): Promise<PreviewSnapshot> {
  return requestJson("/admin/agent-definitions/previews", token, request, {
    method: "POST",
    body: JSON.stringify({ definition }),
  });
}

export async function executeVerificationScenario(
  definition: AgentDefinitionV1,
  scenarioIndex: number,
  token: string,
  request: RequestFunction = fetch,
): Promise<ScenarioExecutionResult> {
  return requestJson("/admin/agent-definitions/previews/scenarios", token, request, {
    method: "POST",
    body: JSON.stringify({ definition, scenarioIndex }),
  });
}

export async function submitPreviewEvent(
  previewId: string,
  event: PreviewEvent,
  token: string,
  request: RequestFunction = fetch,
): Promise<PreviewSnapshot> {
  return previewAction(previewId, "events", token, request, event);
}

export async function generatePreviewBehaviour(
  previewId: string,
  token: string,
  request: RequestFunction = fetch,
): Promise<PreviewSnapshot> {
  return previewAction(previewId, "generate", token, request);
}

export async function resetDefinitionPreview(
  previewId: string,
  token: string,
  request: RequestFunction = fetch,
): Promise<PreviewSnapshot> {
  return previewAction(previewId, "reset", token, request);
}

export async function closeDefinitionPreview(
  previewId: string,
  token: string,
  request: RequestFunction = fetch,
): Promise<void> {
  const headers = new Headers({ [ADMIN_TOKEN_HEADER]: token });
  const response = await request(`/admin/agent-definitions/previews/${encodeURIComponent(previewId)}`, {
    method: "DELETE",
    headers,
  });
  if (!response.ok) throw await apiError(response);
}

async function revisionAction(
  key: string,
  revision: number,
  action: "publish" | "archive",
  optimisticVersion: number,
  token: string,
  request: RequestFunction,
): Promise<DefinitionRevisionView> {
  return requestJson(`/admin/agent-definitions/${encodeURIComponent(key)}/revisions/${revision}/${action}`, token, request, {
    method: "POST",
    body: JSON.stringify({ optimisticVersion }),
  });
}

async function previewAction(
  previewId: string,
  action: "events" | "generate" | "reset",
  token: string,
  request: RequestFunction,
  body?: PreviewEvent,
): Promise<PreviewSnapshot> {
  return requestJson(`/admin/agent-definitions/previews/${encodeURIComponent(previewId)}/${action}`, token, request, {
    method: "POST",
    body: body ? JSON.stringify(body) : undefined,
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
  if (code === "PREVIEW_NOT_FOUND") return "This disposable preview has expired or was already closed.";
  if (code === "PREVIEW_LIMIT") return "The preview limit is currently reached. Close another preview and retry.";
  if (code === "PREVIEW_EXECUTION_FAILED") return "The preview could not complete this operation.";
  if (status === 409) return "This definition or revision already exists.";
  if (status >= 500) return "The application could not complete the designer request. Try again.";
  return "The designer request could not be completed.";
}
