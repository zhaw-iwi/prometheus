const THEME_STORAGE_KEY = "prometheus.valerian.theme";
const ACCESS_CODE_HEADER = "X-Prometheus-Access-Code";
const ADMIN_TOKEN_HEADER = "X-Prometheus-Admin-Token";

const DEFAULT_VARIABLES = {
  baseUrl: "",
  accessCode: "{accessCode}",
  adminToken: "{adminToken}",
  agentId: "{agentId}",
  accessCodeId: "{accessCodeId}",
  callId: "{callId}",
  presetKey: "default",
  agentDefinitionKey: "core.social_context_sensitivity",
};

const ENDPOINTS = [
  {
    id: "demo-session-open",
    group: "Scoped Demo",
    method: "POST",
    path: "/demo/session",
    summary: "Validate an access code and open a scoped browser/client session.",
    headers: { "Content-Type": "application/json" },
    body: { accessCode: "{accessCode}" },
  },
  {
    id: "demo-agent-types",
    group: "Scoped Demo",
    method: "GET",
    path: "/demo/agent-types",
    summary: "List agent definitions allowed by the current access code.",
    headers: { [ACCESS_CODE_HEADER]: "{accessCode}" },
  },
  {
    id: "demo-agent-list",
    group: "Scoped Demo",
    method: "GET",
    path: "/demo/agents",
    summary: "List agent instances created for the current access code.",
    headers: { [ACCESS_CODE_HEADER]: "{accessCode}" },
  },
  {
    id: "demo-agent-create",
    group: "Scoped Demo",
    method: "POST",
    path: "/demo/agents",
    summary: "Instantiate one allowed agent definition for the current access code.",
    headers: { "Content-Type": "application/json", [ACCESS_CODE_HEADER]: "{accessCode}" },
    body: { agentDefinitionKey: "{agentDefinitionKey}" },
  },
  {
    id: "demo-agent-delete",
    group: "Scoped Demo",
    method: "DELETE",
    path: "/demo/agents/{agentId}",
    summary: "Delete one scoped demo agent instance.",
    headers: { [ACCESS_CODE_HEADER]: "{accessCode}" },
    pathVariables: ["agentId"],
    dangerous: true,
  },
  {
    id: "demo-agent-info",
    group: "Scoped Demo",
    method: "GET",
    path: "/demo/agents/{agentId}/info",
    summary: "Inspect agent metadata, active state, language, and interaction profile.",
    headers: { [ACCESS_CODE_HEADER]: "{accessCode}" },
    pathVariables: ["agentId"],
  },
  {
    id: "demo-agent-state",
    group: "Scoped Demo",
    method: "GET",
    path: "/demo/agents/{agentId}/state",
    summary: "Read the agent's current state.",
    headers: { [ACCESS_CODE_HEADER]: "{accessCode}" },
    pathVariables: ["agentId"],
  },
  {
    id: "demo-agent-storage",
    group: "Scoped Demo",
    method: "GET",
    path: "/demo/agents/{agentId}/storage",
    summary: "Read inspectable agent storage entries.",
    headers: { [ACCESS_CODE_HEADER]: "{accessCode}" },
    pathVariables: ["agentId"],
  },
  {
    id: "demo-agent-start",
    group: "Scoped Demo",
    method: "POST",
    path: "/demo/agents/{agentId}/start",
    summary: "Start the agent and receive any synchronous start response.",
    headers: { [ACCESS_CODE_HEADER]: "{accessCode}" },
    pathVariables: ["agentId"],
  },
  {
    id: "demo-agent-reset",
    group: "Scoped Demo",
    method: "DELETE",
    path: "/demo/agents/{agentId}/reset",
    summary: "Reset the agent instance to its initial runtime state.",
    headers: { [ACCESS_CODE_HEADER]: "{accessCode}" },
    pathVariables: ["agentId"],
    dangerous: true,
  },
  {
    id: "demo-agent-acknowledge",
    group: "Events",
    method: "POST",
    path: "/demo/agents/{agentId}/acknowledge",
    summary: "Publish one observation event to the agent runtime.",
    headers: { "Content-Type": "application/json", [ACCESS_CODE_HEADER]: "{accessCode}" },
    query: { profile: "full_plan" },
    pathVariables: ["agentId"],
    body: {
      type: "obs.user_utterance",
      actor: "user",
      kind: "message",
      payload: "Hello from the API Workbench.",
    },
  },
  {
    id: "demo-behaviour-generate",
    group: "Behaviour",
    method: "POST",
    path: "/demo/agents/{agentId}/behaviour/generate",
    summary: "Ask the backend to generate a behaviour plan for the current state/history.",
    headers: { "Content-Type": "application/json", [ACCESS_CODE_HEADER]: "{accessCode}" },
    pathVariables: ["agentId"],
    body: {
      outputProfile: "full_plan",
      omitModalities: [],
    },
  },
  {
    id: "demo-behaviour-stream",
    group: "Streams",
    method: "GET",
    path: "/demo/agents/{agentId}/behaviour/stream",
    summary: "Subscribe to asynchronous behaviour-plan events. Browser EventSource uses accessCode as query.",
    headers: { [ACCESS_CODE_HEADER]: "{accessCode}" },
    query: { accessCode: "{accessCode}", lastEventId: "" },
    pathVariables: ["agentId"],
    sse: true,
  },
  {
    id: "demo-monitor-stream",
    group: "Streams",
    method: "GET",
    path: "/demo/agents/{agentId}/monitor/stream",
    summary: "Subscribe to live agent monitor snapshots.",
    headers: { [ACCESS_CODE_HEADER]: "{accessCode}" },
    query: { accessCode: "{accessCode}" },
    pathVariables: ["agentId"],
    sse: true,
  },
  {
    id: "demo-agent-prompt",
    group: "Diagnostics",
    method: "GET",
    path: "/demo/agents/{agentId}/prompt",
    summary: "Inspect the prompt bundle for a selected output profile.",
    headers: { [ACCESS_CODE_HEADER]: "{accessCode}" },
    query: { profile: "full_plan" },
    pathVariables: ["agentId"],
  },
  {
    id: "demo-realtime-call",
    group: "Realtime",
    method: "POST",
    path: "/demo/agents/{agentId}/realtime/call",
    summary: "Create a PROMETHEUS-owned Realtime WebRTC call from an SDP offer.",
    headers: { "Content-Type": "application/sdp", [ACCESS_CODE_HEADER]: "{accessCode}" },
    query: { voice: "cedar", turnDetection: "server_vad", generateComplement: "true" },
    pathVariables: ["agentId"],
    bodyKind: "text",
    body: "v=0\r\n...client SDP offer...",
  },
  {
    id: "admin-agent-types",
    group: "Admin",
    method: "GET",
    path: "/admin/agent-types",
    summary: "List all registered agent definitions with an admin token.",
    headers: { [ADMIN_TOKEN_HEADER]: "{adminToken}" },
  },
  {
    id: "admin-access-codes",
    group: "Admin",
    method: "GET",
    path: "/admin/access-codes",
    summary: "List access codes and their assigned agent definitions.",
    headers: { [ADMIN_TOKEN_HEADER]: "{adminToken}" },
  },
  {
    id: "admin-create-access-code",
    group: "Admin",
    method: "POST",
    path: "/admin/access-codes",
    summary: "Create an access code for a demo or client-development session.",
    headers: { "Content-Type": "application/json", [ADMIN_TOKEN_HEADER]: "{adminToken}" },
    body: { code: "DEV01", enabled: true },
  },
  {
    id: "admin-assign-agent-types",
    group: "Admin",
    method: "PUT",
    path: "/admin/access-codes/{accessCodeId}/agent-types",
    summary: "Replace the agent definition assignments for one access code.",
    headers: { "Content-Type": "application/json", [ADMIN_TOKEN_HEADER]: "{adminToken}" },
    pathVariables: ["accessCodeId"],
    body: { agentTypeKeys: ["core.social_context_sensitivity"] },
  },
  {
    id: "global-agent-info",
    group: "Trusted Global",
    method: "GET",
    path: "/{agentId}/info",
    summary: "Trusted global equivalent for reading agent metadata.",
    pathVariables: ["agentId"],
  },
  {
    id: "global-agent-acknowledge",
    group: "Trusted Global",
    method: "POST",
    path: "/{agentId}/acknowledge",
    summary: "Trusted global equivalent for publishing an observation event.",
    headers: { "Content-Type": "application/json" },
    query: { profile: "full_plan" },
    pathVariables: ["agentId"],
    body: {
      type: "obs.user_utterance",
      actor: "user",
      kind: "message",
      payload: "Hello from a trusted client.",
    },
  },
  {
    id: "global-behaviour-stream",
    group: "Trusted Global",
    method: "GET",
    path: "/{agentId}/behaviour/stream",
    summary: "Trusted global equivalent for behaviour SSE.",
    pathVariables: ["agentId"],
    sse: true,
  },
];

const LIFECYCLE_STEPS = [
  {
    title: "Open scoped session",
    detail: "Check an access code and establish the working scope.",
    endpointId: "demo-session-open",
  },
  {
    title: "Select agent definition",
    detail: "Load the agent definitions allowed for the access code.",
    endpointId: "demo-agent-types",
  },
  {
    title: "Instantiate agent",
    detail: "Create a concrete agent instance from one definition key.",
    endpointId: "demo-agent-create",
  },
  {
    title: "Inspect profile",
    detail: "Read supported observations and behaviour modalities.",
    endpointId: "demo-agent-info",
  },
  {
    title: "Subscribe behaviour",
    detail: "Open behaviour SSE before sending observations.",
    endpointId: "demo-behaviour-stream",
  },
  {
    title: "Post observation",
    detail: "Acknowledge an event such as user speech or perception JSON.",
    endpointId: "demo-agent-acknowledge",
  },
  {
    title: "Generate behaviour",
    detail: "Trigger asynchronous behaviour generation when needed.",
    endpointId: "demo-behaviour-generate",
  },
  {
    title: "Monitor runtime",
    detail: "Watch state, storage, event history, and monitor snapshots.",
    endpointId: "demo-monitor-stream",
  },
];

const state = {
  selectedEndpointId: "demo-session-open",
  selectedSnippet: "fetch",
  variables: { ...DEFAULT_VARIABLES },
};

const elements = {};

document.addEventListener("DOMContentLoaded", init);

function init() {
  collectElements();
  initVariables();
  applyStoredTheme();
  wireEvents();
  renderGroupFilter();
  renderLifecycleSteps();
  renderEndpointList();
  renderSelectedEndpoint();
}

function collectElements() {
  elements.baseUrlInput = document.getElementById("base_url_input");
  elements.endpointSearch = document.getElementById("endpoint_search");
  elements.groupFilter = document.getElementById("group_filter");
  elements.lifecycleSteps = document.getElementById("lifecycle_steps");
  elements.endpointList = document.getElementById("endpoint_list");
  elements.selectedMethod = document.getElementById("selected_method");
  elements.selectedGroup = document.getElementById("selected_group");
  elements.requestStatus = document.getElementById("request_status");
  elements.selectedName = document.getElementById("selected_name");
  elements.selectedSummary = document.getElementById("selected_summary");
  elements.requestMethodBadge = document.getElementById("request_method_badge");
  elements.resolvedUrl = document.getElementById("resolved_url");
  elements.pathVariables = document.getElementById("path_variables");
  elements.requestHeaders = document.getElementById("request_headers");
  elements.requestQuery = document.getElementById("request_query");
  elements.bodyEditor = document.getElementById("body_editor");
  elements.snippetOutput = document.getElementById("snippet_output");
  elements.httpResponsePreview = document.getElementById("http_response_preview");
  elements.sseResponsePreview = document.getElementById("sse_response_preview");
  elements.profilePreview = document.getElementById("profile_preview");
  elements.sendRequestButton = document.getElementById("send_request_button");
  elements.copyUrlButton = document.getElementById("copy_url_button");
  elements.copyFetchButton = document.getElementById("copy_fetch_button");
  elements.copyCurlButton = document.getElementById("copy_curl_button");
  elements.copySseButton = document.getElementById("copy_sse_button");
  elements.themeToggle = document.querySelector("[data-theme-toggle]");
  elements.variableInputs = Array.from(document.querySelectorAll("[data-workbench-variable]"));
}

function initVariables() {
  state.variables.baseUrl = window.location.origin;
  elements.variableInputs.forEach((input) => {
    const key = input.dataset.workbenchVariable;
    if (key === "baseUrl") {
      input.value = state.variables.baseUrl;
    } else if (key === "agentDefinitionKey") {
      input.value = state.variables.agentDefinitionKey;
    }
  });
}

function wireEvents() {
  elements.variableInputs.forEach((input) => {
    input.addEventListener("input", () => {
      const key = input.dataset.workbenchVariable;
      state.variables[key] = input.value.trim() || DEFAULT_VARIABLES[key] || `{${key}}`;
      renderSelectedEndpoint();
    });
  });
  elements.endpointSearch.addEventListener("input", renderEndpointList);
  elements.groupFilter.addEventListener("change", renderEndpointList);
  elements.bodyEditor.addEventListener("input", renderSnippet);
  elements.sendRequestButton.addEventListener("click", sendSelectedRequest);
  elements.copyUrlButton.addEventListener("click", () => copyText(buildResolvedUrl(selectedEndpoint()), "URL"));
  elements.copyFetchButton.addEventListener("click", () => {
    state.selectedSnippet = "fetch";
    renderSnippet();
    copyText(elements.snippetOutput.textContent, "fetch");
  });
  elements.copyCurlButton.addEventListener("click", () => {
    state.selectedSnippet = "curl";
    renderSnippet();
    copyText(elements.snippetOutput.textContent, "curl");
  });
  elements.copySseButton.addEventListener("click", () => {
    state.selectedSnippet = "sse";
    renderSnippet();
    copyText(elements.snippetOutput.textContent, "SSE");
  });
  elements.themeToggle.addEventListener("click", toggleTheme);
}

function renderGroupFilter() {
  const groups = ["All", ...Array.from(new Set(ENDPOINTS.map((endpoint) => endpoint.group)))];
  elements.groupFilter.innerHTML = groups
    .map((group) => `<option value="${escapeHtml(group)}">${escapeHtml(group)}</option>`)
    .join("");
}

function renderLifecycleSteps() {
  elements.lifecycleSteps.innerHTML = "";
  LIFECYCLE_STEPS.forEach((step, index) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "lifecycle-step";
    button.dataset.endpointId = step.endpointId;
    button.dataset.testid = `lifecycle-step-${step.endpointId}`;
    button.innerHTML = `
      <span class="step-index">${index + 1}</span>
      <span>
        <span class="item-title">${escapeHtml(step.title)}</span>
        <span class="item-subtitle">${escapeHtml(step.detail)}</span>
      </span>
    `;
    button.addEventListener("click", () => selectEndpoint(step.endpointId));
    elements.lifecycleSteps.appendChild(button);
  });
}

function renderEndpointList() {
  const search = elements.endpointSearch.value.trim().toLowerCase();
  const group = elements.groupFilter.value || "All";
  const endpoints = ENDPOINTS.filter((endpoint) => {
    const groupMatches = group === "All" || endpoint.group === group;
    const haystack = `${endpoint.method} ${endpoint.path} ${endpoint.group} ${endpoint.summary} ${endpoint.id}`
      .toLowerCase();
    return groupMatches && (!search || haystack.includes(search));
  });

  elements.endpointList.innerHTML = "";
  endpoints.forEach((endpoint) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "endpoint-item";
    button.dataset.endpointId = endpoint.id;
    button.dataset.testid = `endpoint-${endpoint.id}`;
    button.innerHTML = `
      <span class="method-chip ${methodClass(endpoint.method)}">${escapeHtml(endpoint.method)}</span>
      <span>
        <span class="item-title">${escapeHtml(endpoint.path)}</span>
        <span class="item-subtitle">${escapeHtml(endpoint.summary)}</span>
      </span>
    `;
    button.addEventListener("click", () => selectEndpoint(endpoint.id));
    elements.endpointList.appendChild(button);
  });

  if (!endpoints.length) {
    const empty = document.createElement("div");
    empty.className = "item-subtitle";
    empty.textContent = "No endpoint matches the current filter.";
    elements.endpointList.appendChild(empty);
  }
  markActiveEndpoint();
}

function renderSelectedEndpoint() {
  const endpoint = selectedEndpoint();
  if (!endpoint) {
    return;
  }

  elements.selectedMethod.textContent = endpoint.method;
  elements.selectedMethod.className = `method-chip ${methodClass(endpoint.method)}`;
  elements.requestMethodBadge.textContent = endpoint.method;
  elements.requestMethodBadge.className = `method-chip ${methodClass(endpoint.method)}`;
  elements.selectedGroup.textContent = endpoint.group;
  elements.selectedName.textContent = endpoint.path;
  elements.selectedSummary.textContent = endpoint.summary;
  elements.resolvedUrl.textContent = buildResolvedUrl(endpoint);
  elements.pathVariables.innerHTML = formatList(endpoint.pathVariables || [], variableValue);
  elements.requestHeaders.innerHTML = formatMap(endpoint.headers || {});
  elements.requestQuery.innerHTML = formatMap(endpoint.query || {});
  elements.bodyEditor.value = bodyText(endpoint);
  elements.httpResponsePreview.textContent = httpPlaceholder(endpoint);
  elements.sseResponsePreview.textContent = ssePlaceholder(endpoint);
  elements.profilePreview.textContent = "No profile loaded.";
  elements.copySseButton.disabled = !endpoint.sse;
  elements.sendRequestButton.disabled = endpoint.sse;
  elements.requestStatus.textContent = endpoint.sse ? "Stream template" : "Idle";
  renderSnippet();
  markActiveEndpoint();
}

function renderSnippet() {
  const endpoint = selectedEndpoint();
  if (!endpoint) {
    return;
  }
  if (state.selectedSnippet === "curl") {
    elements.snippetOutput.textContent = curlSnippet(endpoint);
  } else if (state.selectedSnippet === "sse") {
    elements.snippetOutput.textContent = sseSnippet(endpoint);
  } else {
    elements.snippetOutput.textContent = fetchSnippet(endpoint);
  }
}

async function sendSelectedRequest() {
  const endpoint = selectedEndpoint();
  if (!endpoint || endpoint.sse) {
    return;
  }

  const url = buildResolvedUrl(endpoint);
  const unresolved = unresolvedPlaceholders(url);
  if (unresolved.length) {
    renderRequestError(`Missing variable: ${unresolved.join(", ")}`);
    return;
  }

  let requestBody;
  try {
    requestBody = requestBodyForSend(endpoint);
  } catch (error) {
    renderRequestError(error.message);
    return;
  }

  const options = {
    method: endpoint.method,
    headers: resolvedHeaders(endpoint),
  };
  if (requestBody !== undefined) {
    options.body = requestBody;
  }

  setRequestBusy(true);
  try {
    const response = await fetch(url, options);
    const text = await response.text();
    const parsed = parseResponseBody(text, response.headers.get("content-type") || "");
    const result = {
      ok: response.ok,
      status: response.status,
      statusText: response.statusText,
      headers: Object.fromEntries(response.headers.entries()),
      text,
      parsed,
    };
    renderHttpResult(result);
    if (response.ok) {
      handleSuccessfulResponse(endpoint, parsed);
    }
  } catch (error) {
    renderRequestError(error.message || String(error));
  } finally {
    setRequestBusy(false);
  }
}

function requestBodyForSend(endpoint) {
  const body = elements.bodyEditor.value.trim();
  if (!body) {
    return undefined;
  }
  if (endpoint.bodyKind === "text") {
    const unresolved = unresolvedPlaceholders(body);
    if (unresolved.length) {
      throw new Error(`Missing body variable: ${unresolved.join(", ")}`);
    }
    return body;
  }
  try {
    const parsed = JSON.parse(body);
    const normalized = JSON.stringify(parsed);
    const unresolved = unresolvedPlaceholders(normalized);
    if (unresolved.length) {
      throw new Error(`Missing body variable: ${unresolved.join(", ")}`);
    }
    return normalized;
  } catch (error) {
    if (error.message && error.message.startsWith("Missing body variable:")) {
      throw error;
    }
    throw new Error("Request body is not valid JSON.");
  }
}

function parseResponseBody(text, contentType) {
  if (!text) {
    return null;
  }
  if (contentType.includes("application/json")) {
    try {
      return JSON.parse(text);
    } catch (error) {
      return text;
    }
  }
  try {
    return JSON.parse(text);
  } catch (error) {
    return text;
  }
}

function renderHttpResult(result) {
  elements.requestStatus.textContent = `${result.status} ${result.statusText || ""}`.trim();
  elements.httpResponsePreview.textContent = JSON.stringify({
    status: result.status,
    statusText: result.statusText,
    headers: result.headers,
    body: result.parsed,
  }, null, 2);
}

function renderRequestError(message) {
  elements.requestStatus.textContent = "Error";
  elements.httpResponsePreview.textContent = JSON.stringify({
    error: message,
  }, null, 2);
}

function setRequestBusy(busy) {
  elements.sendRequestButton.disabled = busy || selectedEndpoint().sse;
  elements.sendRequestButton.innerHTML = busy
    ? `<span class="spinner-border spinner-border-sm" aria-hidden="true"></span> Send`
    : `<i class="bi bi-send-fill" aria-hidden="true"></i> Send`;
  if (busy) {
    elements.requestStatus.textContent = "Sending";
  }
}

function handleSuccessfulResponse(endpoint, parsed) {
  if (!parsed || typeof parsed !== "object") {
    return;
  }
  if (endpoint.id === "demo-session-open") {
    if (parsed.accessCode) {
      setVariable("accessCode", parsed.accessCode);
    }
    applyAgentTypes(parsed.agentTypes);
    applyAgents(parsed.agents);
  } else if (endpoint.id === "demo-agent-types" || endpoint.id === "admin-agent-types") {
    applyAgentTypes(parsed);
  } else if (endpoint.id === "demo-agent-list") {
    applyAgents(parsed);
  } else if (endpoint.id === "demo-agent-create" || endpoint.id === "demo-agent-info" || endpoint.id === "global-agent-info") {
    applyAgentInfo(parsed);
  }
}

function applyAgentTypes(value) {
  if (!Array.isArray(value) || !value.length) {
    return;
  }
  const first = value.find((entry) => entry && entry.key);
  if (first) {
    setVariable("agentDefinitionKey", first.key);
  }
  elements.profilePreview.textContent = JSON.stringify({
    agentTypes: value.map((entry) => ({
      key: entry.key,
      displayName: entry.displayName,
      packagePath: entry.packagePath || [],
    })),
  }, null, 2);
}

function applyAgents(value) {
  if (!Array.isArray(value) || !value.length) {
    return;
  }
  applyAgentInfo(value[0]);
}

function applyAgentInfo(value) {
  if (!value || typeof value !== "object") {
    return;
  }
  const id = value.id || value.ID;
  if (id) {
    setVariable("agentId", id);
  }
  if (value.interactionProfile) {
    renderProfile(value);
  }
}

function renderProfile(agentInfo) {
  const profile = agentInfo.interactionProfile || {};
  elements.profilePreview.textContent = JSON.stringify({
    agentId: agentInfo.id || agentInfo.ID || variableValue("agentId"),
    name: agentInfo.name || "",
    languageCode: agentInfo.languageCode || "",
    supportedObservations: profile.supportedObservations || [],
    supportedBehaviourModalities: profile.supportedBehaviourModalities || [],
    profileTags: profile.profileTags || [],
  }, null, 2);
}

function setVariable(key, value) {
  if (value == null || value === "") {
    return;
  }
  state.variables[key] = String(value);
  const input = elements.variableInputs.find((candidate) => candidate.dataset.workbenchVariable === key);
  if (input) {
    input.value = String(value);
  }
  elements.resolvedUrl.textContent = buildResolvedUrl(selectedEndpoint());
  renderSnippet();
}

function unresolvedPlaceholders(value) {
  return Array.from(new Set(String(value).match(/\{[a-zA-Z0-9_]+\}/g) || []));
}

function selectEndpoint(endpointId) {
  if (!ENDPOINTS.some((endpoint) => endpoint.id === endpointId)) {
    return;
  }
  state.selectedEndpointId = endpointId;
  const endpoint = selectedEndpoint();
  state.selectedSnippet = endpoint.sse ? "sse" : "fetch";
  renderSelectedEndpoint();
}

function selectedEndpoint() {
  return ENDPOINTS.find((endpoint) => endpoint.id === state.selectedEndpointId) || ENDPOINTS[0];
}

function markActiveEndpoint() {
  document.querySelectorAll("[data-endpoint-id]").forEach((el) => {
    el.classList.toggle("is-active", el.dataset.endpointId === state.selectedEndpointId);
  });
}

function buildResolvedUrl(endpoint) {
  const baseUrl = normalizeBaseUrl(variableValue("baseUrl"));
  const path = substitute(endpoint.path);
  const query = buildQuery(endpoint.query || {});
  return `${baseUrl}${path}${query}`;
}

function buildQuery(query) {
  const entries = Object.entries(query)
    .map(([key, value]) => [key, substitute(String(value))])
    .filter(([, value]) => value !== "");
  if (!entries.length) {
    return "";
  }
  return "?" + entries
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeQueryValue(value)}`)
    .join("&");
}

function encodeQueryValue(value) {
  if (value.startsWith("{") && value.endsWith("}")) {
    return value;
  }
  return encodeURIComponent(value);
}

function normalizeBaseUrl(value) {
  const baseUrl = value || window.location.origin;
  return baseUrl.endsWith("/") ? baseUrl.slice(0, -1) : baseUrl;
}

function variableValue(key) {
  return state.variables[key] || DEFAULT_VARIABLES[key] || `{${key}}`;
}

function substitute(template) {
  return String(template).replace(/\{([a-zA-Z0-9_]+)\}/g, (_, key) => variableValue(key));
}

function resolvedHeaders(endpoint) {
  return Object.fromEntries(Object.entries(endpoint.headers || {}).map(([key, value]) => [key, substitute(value)]));
}

function bodyText(endpoint) {
  if (!Object.prototype.hasOwnProperty.call(endpoint, "body")) {
    return "";
  }
  if (endpoint.bodyKind === "text") {
    return substitute(endpoint.body);
  }
  return JSON.stringify(resolveValue(endpoint.body), null, 2);
}

function resolveValue(value) {
  if (Array.isArray(value)) {
    return value.map(resolveValue);
  }
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.entries(value).map(([key, entry]) => [key, resolveValue(entry)]));
  }
  if (typeof value === "string") {
    return substitute(value);
  }
  return value;
}

function fetchSnippet(endpoint) {
  const headers = resolvedHeaders(endpoint);
  const options = [`method: "${endpoint.method}"`];
  if (Object.keys(headers).length) {
    options.push(`headers: ${JSON.stringify(headers, null, 2)}`);
  }
  const body = elements.bodyEditor.value.trim();
  if (body) {
    if (endpoint.bodyKind === "text") {
      options.push(`body: ${JSON.stringify(body)}`);
    } else {
      options.push(`body: JSON.stringify(${body})`);
    }
  }
  return [
    `const response = await fetch(${JSON.stringify(buildResolvedUrl(endpoint))}, {`,
    indent(options.join(",\n"), 2),
    "});",
    "const contentType = response.headers.get(\"content-type\") || \"\";",
    "const payload = contentType.includes(\"application/json\")",
    "  ? await response.json()",
    "  : await response.text();",
  ].join("\n");
}

function curlSnippet(endpoint) {
  const lines = [`curl -i -X ${endpoint.method} ${quoteShell(buildResolvedUrl(endpoint))}`];
  Object.entries(resolvedHeaders(endpoint)).forEach(([key, value]) => {
    lines.push(`  -H ${quoteShell(`${key}: ${value}`)}`);
  });
  const body = elements.bodyEditor.value.trim();
  if (body) {
    lines.push(`  --data ${quoteShell(body)}`);
  }
  return lines.join(" \\\n");
}

function sseSnippet(endpoint) {
  if (!endpoint.sse) {
    return "This endpoint is not an SSE stream. Select a stream endpoint to create an EventSource snippet.";
  }
  return [
    `const stream = new EventSource(${JSON.stringify(buildResolvedUrl(endpoint))});`,
    "stream.addEventListener(\"open\", () => console.log(\"SSE connected\"));",
    "stream.addEventListener(\"message\", event => console.log(event.data));",
    "stream.addEventListener(\"behaviour\", event => console.log(JSON.parse(event.data)));",
    "stream.addEventListener(\"snapshot\", event => console.log(JSON.parse(event.data)));",
    "stream.onerror = () => console.warn(\"SSE disconnected or unavailable\");",
  ].join("\n");
}

function httpPlaceholder(endpoint) {
  if (endpoint.sse) {
    return "SSE endpoint.\nThe HTTP connection stays open while events arrive.";
  }
  return [
    "No request has been sent yet.",
    "Prepared output:",
    "- status",
    "- headers",
    "- body",
  ].join("\n");
}

function ssePlaceholder(endpoint) {
  if (!endpoint.sse) {
    return "Select a stream endpoint to prepare an EventSource subscription.";
  }
  return [
    "Prepared stream endpoint.",
    "Incoming events appear here when connected.",
    `Stream URL: ${buildResolvedUrl(endpoint)}`,
  ].join("\n");
}

function formatMap(map) {
  const entries = Object.entries(map);
  if (!entries.length) {
    return `<span class="empty-value">None</span>`;
  }
  return entries
    .map(([key, value]) => `<div><strong>${escapeHtml(key)}</strong><br><code>${escapeHtml(substitute(value))}</code></div>`)
    .join("");
}

function formatList(values, resolver) {
  if (!values.length) {
    return `<span class="empty-value">None</span>`;
  }
  return values
    .map((value) => `<div><strong>${escapeHtml(value)}</strong><br><code>${escapeHtml(resolver(value))}</code></div>`)
    .join("");
}

function methodClass(method) {
  return `method-${method.toLowerCase()}`;
}

function quoteShell(value) {
  return `'${String(value).replace(/'/g, "'\"'\"'")}'`;
}

function indent(value, spaces) {
  const prefix = " ".repeat(spaces);
  return String(value)
    .split("\n")
    .map((line) => `${prefix}${line}`)
    .join("\n");
}

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

async function copyText(value, label) {
  try {
    await navigator.clipboard.writeText(value);
    flashCopy(label);
  } catch (error) {
    flashCopy("copy unavailable");
  }
}

function flashCopy(label) {
  const previous = elements.selectedGroup.textContent;
  elements.selectedGroup.textContent = `Copied ${label}`;
  elements.selectedGroup.classList.add("copy-flash");
  window.setTimeout(() => {
    elements.selectedGroup.textContent = previous;
    elements.selectedGroup.classList.remove("copy-flash");
  }, 1200);
}

function applyStoredTheme() {
  const theme = document.documentElement.dataset.theme === "dark" ? "dark" : "light";
  setTheme(theme, { persist: false });
}

function toggleTheme() {
  const current = document.documentElement.dataset.theme === "dark" ? "dark" : "light";
  setTheme(current === "dark" ? "light" : "dark", { persist: true });
}

function setTheme(theme, options = {}) {
  const nextTheme = theme === "dark" ? "dark" : "light";
  document.documentElement.dataset.theme = nextTheme;
  document.documentElement.dataset.bsTheme = nextTheme;
  if (options.persist) {
    try {
      localStorage.setItem(THEME_STORAGE_KEY, nextTheme);
    } catch (error) {
      // Ignore storage failures in restricted browser contexts.
    }
  }
  const dark = nextTheme === "dark";
  elements.themeToggle.setAttribute("aria-pressed", dark ? "true" : "false");
  elements.themeToggle.setAttribute("aria-label", dark ? "Switch to light mode" : "Switch to dark mode");
  const icon = elements.themeToggle.querySelector("i");
  if (icon) {
    icon.className = dark ? "bi bi-sun" : "bi bi-moon-stars";
  }
}
