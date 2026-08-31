import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const FIXTURE = JSON.parse(readFileSync(
  resolve("src/test/resources/agent-definitions/valid/composite-flow.json"),
  "utf8",
));

export const VISUAL_KEY = "designer.visual_acceptance";

export async function installDesignerApiMock(page, options = {}) {
  const definition = structuredClone(options.definition ?? FIXTURE);
  if (!options.definition) {
    definition.key = VISUAL_KEY;
    definition.revision = 1;
    definition.metadata.displayName = "Visual acceptance agent";
    definition.metadata.description = "Deterministic fixture for the six-step V2 release gate.";
    definition.metadata.categoryPath = "designer.visual";
    const session = definition.states.find((state) => state.id === "session");
    session.policy = {
      kind: "prometheus.policy.prompt", version: 1,
      config: {
        responsePrompt: { sections: [{ id: "agent.objective", kind: "objective", content: "Help a visitor understand a practical next step while acknowledging uncertainty and keeping the response concise enough for a busy public setting." }] },
        consumedObservations: [], emittedModalities: [],
      },
    };
    const conversation = definition.states.find((state) => state.id === "conversation");
    conversation.policy = {
      kind: "prometheus.policy.prompt", version: 1,
      config: {
        responsePrompt: { sections: [{ id: "response.context", kind: "context", content: "Outer policy." }] },
        consumedObservations: ["obs.user_utterance", "obs.social.context"], emittedModalities: ["speech", "display"],
      },
    };
    definition.transitions.push({
      id: "repeat", sourceStateId: "conversation", targetStateId: "conversation", order: 20,
      decisions: [{ kind: "prometheus.decision.latest-event-type", version: 1, config: { eventType: "obs.user_utterance" } }],
      actions: [],
    });
  }
  const scenario = {
    catalogMode: options.catalogMode ?? "populated",
    catalogDelay: options.catalogDelay ?? 0,
    definition,
    revision: revisionView(definition),
    activeRevision: null,
    readinessDiagnostics: false,
    conflictOnFirstSave: options.conflictOnFirstSave ?? false,
    saveAttempts: 0,
    previewSequence: 1,
    scenarioExecutions: 0,
    openScenarioSessions: 0,
  };

  await page.route("**/admin/agent-definitions**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();

    if (method === "GET" && path === "/admin/agent-definitions/component-catalog") {
      return fulfillJson(route, componentCatalog());
    }
    if (method === "GET" && path === "/admin/agent-definitions") {
      if (scenario.catalogDelay) await new Promise((resolveDelay) => setTimeout(resolveDelay, scenario.catalogDelay));
      if (scenario.catalogMode === "unauthorized") {
        return route.fulfill({ status: 401, contentType: "application/json", body: "{}" });
      }
      if (scenario.catalogMode === "error") {
        return route.fulfill({ status: 503, contentType: "application/json", body: "{}" });
      }
      return fulfillJson(route, scenario.catalogMode === "empty" ? [] : [summary(scenario)]);
    }
    if (method === "GET" && path.endsWith("/export")) {
      return fulfillJson(route, scenario.definition);
    }
    if (method === "GET" && path.includes("/revisions/")) {
      return fulfillJson(route, scenario.revision);
    }
    if (method === "PUT" && path.includes("/revisions/")) {
      scenario.saveAttempts++;
      if (scenario.conflictOnFirstSave && scenario.saveAttempts === 1) {
        scenario.revision = { ...scenario.revision, optimisticVersion: scenario.revision.optimisticVersion + 1 };
        return fulfillJson(route, { code: "OPTIMISTIC_CONFLICT", diagnostics: [] }, 409);
      }
      const body = request.postDataJSON();
      scenario.definition = structuredClone(body.definition);
      scenario.revision = {
        ...scenario.revision,
        definition: structuredClone(body.definition),
        optimisticVersion: scenario.revision.optimisticVersion + 1,
        contentHash: "saved-hash",
      };
      return fulfillJson(route, scenario.revision);
    }
    if (method === "POST" && path === "/admin/agent-definitions/validation") {
      return fulfillJson(route, { valid: true, diagnostics: [] });
    }
    if (method === "POST" && path === "/admin/agent-definitions/publication-readiness") {
      const diagnostics = scenario.readinessDiagnostics ? diagnosticFixture() : [];
      return fulfillJson(route, { valid: diagnostics.length === 0, diagnostics });
    }
    if (method === "POST" && path === "/admin/agent-definitions/prompt-previews") {
      return fulfillJson(route, [{
        pointer: "/states/1/policy/config/responsePrompt",
        label: "Response prompt",
        composed: "[context]\nOuter policy.",
      }]);
    }
    if (method === "POST" && path === "/admin/agent-definitions/previews/scenarios") {
      scenario.scenarioExecutions++;
      scenario.openScenarioSessions++;
      const result = scenarioExecution(request.postDataJSON());
      scenario.openScenarioSessions--;
      return fulfillJson(route, result);
    }
    if (method === "POST" && path === "/admin/agent-definitions/previews") {
      return fulfillJson(route, previewSnapshot(scenario, "CREATE"));
    }
    if (method === "POST" && path.endsWith("/events")) {
      const event = request.postDataJSON();
      return fulfillJson(route, previewSnapshot(scenario, "EVENT", event));
    }
    if (method === "POST" && path.endsWith("/generate")) {
      return fulfillJson(route, previewSnapshot(scenario, "GENERATE"));
    }
    if (method === "POST" && path.endsWith("/reset")) {
      scenario.previewSequence = 1;
      return fulfillJson(route, previewSnapshot(scenario, "RESET"));
    }
    if (method === "DELETE" && path.includes("/previews/")) {
      return route.fulfill({ status: 204 });
    }
    if (method === "POST" && path.endsWith("/publish")) {
      scenario.revision = {
        ...scenario.revision,
        status: "PUBLISHED",
        optimisticVersion: scenario.revision.optimisticVersion + 1,
        publishedAt: "2026-08-30T10:05:00Z",
      };
      return fulfillJson(route, scenario.revision);
    }
    if (method === "POST" && path.endsWith("/activate")) {
      scenario.activeRevision = scenario.revision.revision;
      return fulfillJson(route, summary(scenario));
    }
    if (method === "POST" && path.endsWith("/archive")) {
      scenario.revision = {
        ...scenario.revision,
        status: "ARCHIVED",
        optimisticVersion: scenario.revision.optimisticVersion + 1,
        archivedAt: "2026-08-30T10:06:00Z",
      };
      return fulfillJson(route, scenario.revision);
    }
    if (method === "POST" && path.endsWith("/clone")) {
      const body = request.postDataJSON();
      const cloned = structuredClone(scenario.definition);
      cloned.key = body.targetKey;
      cloned.revision = body.targetRevision;
      scenario.definition = cloned;
      scenario.revision = revisionView(cloned, body.targetRevision, 72);
      scenario.activeRevision = null;
      return fulfillJson(route, scenario.revision, 201);
    }
    return route.fulfill({ status: 404, contentType: "application/json", body: "{}" });
  });
  return scenario;
}

export function componentCatalog() {
  const none = { consumedObservations: [], emittedBehaviourModalities: [], storage: [], resources: [], states: [] };
  return [
    component("prometheus.policy.prompt", "POLICY", "Prompt policy", {
      responsePrompt: { sections: [{ id: "response.objective", kind: "objective", content: "Answer clearly." }] },
      consumedObservations: [], emittedModalities: [],
    }, { ...none, consumedObservations: ["obs.user_utterance"], emittedBehaviourModalities: ["speech", "display"] },
    [{ responsePrompt: { sections: [{ id: "response.objective", kind: "objective", content: "Example response." }] } }]),
    component("prometheus.policy.exact-text", "POLICY", "Exact text", {
      eventType: "obs.user_utterance", actor: "user", eventKind: "observation", maxTextCodePoints: 2000,
    }, { ...none, consumedObservations: ["obs.user_utterance"], emittedBehaviourModalities: ["speech"] }),
    component("prometheus.policy.no-op", "POLICY", "No response", {}, none),
    component("prometheus.policy.rps-reveal", "POLICY", "RPS reveal", {
      currentAgentSignStorageKey: "rps_current_agent_sign", currentRoundNumberStorageKey: "rps_current_round_number",
    }, { ...none, emittedBehaviourModalities: ["speech", "motion.handSign", "display"] }),
    component("prometheus.policy.rps-result", "POLICY", "RPS result", {
      lastRoundStorageKey: "rps_last_round",
    }, { ...none, emittedBehaviourModalities: ["speech", "display"] }),
    component("prometheus.action.rps-select-sign", "ACTION", "Select RPS sign", {
      roundsStorageKey: "rps_rounds", currentAgentSignStorageKey: "rps_current_agent_sign",
      currentRoundNumberStorageKey: "rps_current_round_number",
    }, none),
    component("prometheus.action.rps-evaluate-round", "ACTION", "Evaluate RPS round", {
      handSignEventType: "obs.hand.sign", currentAgentSignStorageKey: "rps_current_agent_sign",
      currentRoundNumberStorageKey: "rps_current_round_number", lastRoundStorageKey: "rps_last_round", roundsStorageKey: "rps_rounds",
    }, { ...none, consumedObservations: ["obs.hand.sign"] }),
    component("prometheus.selector.any", "SELECTOR", "Any event", {}, none),
    component("prometheus.selector.state-path", "SELECTOR", "State path", {}, none),
    component("prometheus.decision.latest-event-type", "DECISION", "Latest event type", { eventType: "obs.user_utterance" }, none),
    component("prometheus.decision.prompt", "DECISION", "Prompt decision", {}, none),
    component("prometheus.action.increment", "ACTION", "Increment value", { targetStorageKey: "count" }, none),
    component("prometheus.action.prompt-behaviour", "ACTION", "Prompt behaviour", {}, none),
  ];
}

function component(kind, category, label, defaultConfig, capabilities, examples = []) {
  const authoring = componentAuthoring(kind, category);
  return {
    kind, version: 1, category, label,
    description: `${label} deterministic visual fixture.`,
    ...authoring,
    configSchema: { type: "object", properties: {} },
    defaultConfig, examples, capabilities,
  };
}

function componentAuthoring(kind, category) {
  if (kind === "prometheus.policy.prompt") {
    return { authoringRole: "RESPONSE_STRATEGY", exposure: "GUIDED", capabilityGroup: "prompt-response", advancedReason: null };
  }
  if (kind === "prometheus.policy.exact-text") {
    return { authoringRole: "RESPONSE_STRATEGY", exposure: "GUIDED", capabilityGroup: "exact-text-response", advancedReason: null };
  }
  if (kind === "prometheus.policy.no-op") {
    return { authoringRole: "RESPONSE_STRATEGY", exposure: "ADVANCED", capabilityGroup: null, advancedReason: "Technical no-response policy." };
  }
  if (kind.includes(".rps-")) {
    return { authoringRole: "DETERMINISTIC_OPERATION", exposure: "GUIDED", capabilityGroup: "rock-scissor-paper", advancedReason: null };
  }
  if (kind === "prometheus.action.prompt-behaviour") {
    return { authoringRole: "RULE_RESPONSE", exposure: "GUIDED", capabilityGroup: "prompt-response", advancedReason: null };
  }
  if (kind === "prometheus.action.increment") {
    return { authoringRole: "DATA_UPDATE", exposure: "GUIDED", capabilityGroup: "increment-value", advancedReason: null };
  }
  if (kind === "prometheus.decision.prompt") {
    return { authoringRole: "RULE_CONDITION", exposure: "GUIDED", capabilityGroup: "semantic-condition", advancedReason: null };
  }
  if (kind === "prometheus.decision.latest-event-type") {
    return {
      authoringRole: "RULE_TRIGGER", exposure: "GENERATED_INTERNAL", capabilityGroup: null,
      advancedReason: "Generated from the selected event trigger.",
    };
  }
  if (category === "SELECTOR") {
    return {
      authoringRole: "TECHNICAL_SELECTOR", exposure: "ADVANCED", capabilityGroup: null,
      advancedReason: "Derived event-history selection.",
    };
  }
  const authoringRole = category === "POLICY" ? "RESPONSE_STRATEGY"
    : category === "DECISION" ? "RULE_CONDITION"
      : "DETERMINISTIC_OPERATION";
  return { authoringRole, exposure: "GUIDED", capabilityGroup: "visual-fixture", advancedReason: null };
}

function revisionView(definition, revision = 1, id = 71) {
  return {
    id, key: definition.key, revision, schemaVersion: 1, status: "DRAFT",
    contentHash: "visual-hash", provenance: "DESIGNER", sourceDetail: "playwright-visual",
    optimisticVersion: 1, createdAt: "2026-08-30T10:00:00Z", updatedAt: "2026-08-30T10:00:00Z",
    publishedAt: null, archivedAt: null, definition: structuredClone(definition),
  };
}

function summary(scenario) {
  return {
    key: scenario.definition.key,
    activeRevisionId: scenario.activeRevision ? scenario.revision.id : null,
    activeRevision: scenario.activeRevision,
    optimisticVersion: 1,
    displayName: scenario.definition.metadata.displayName,
    description: scenario.definition.metadata.description,
    categoryPath: ["designer", "visual"],
    languageCode: "en",
    revisions: [{
      id: scenario.revision.id, revision: scenario.revision.revision, status: scenario.revision.status,
      provenance: scenario.revision.provenance, optimisticVersion: scenario.revision.optimisticVersion,
      updatedAt: scenario.revision.updatedAt,
    }],
  };
}

function diagnosticFixture() {
  return [
    { code: "DISPLAY_NAME_REQUIRED", severity: "ERROR", pointer: "/metadata/displayName", message: "Give the agent a clear display name.", hint: "Return to Brief." },
    { code: "TRANSITION_TARGET_INVALID", severity: "ERROR", pointer: "/transitions/0/targetStateId", message: "Choose an existing target situation.", hint: "Inspect the rule in Interaction." },
  ];
}

function previewSnapshot(scenario, kind, input = null) {
  const sequence = scenario.previewSequence++;
  const generated = kind === "GENERATE";
  return {
    id: "visual-preview-1", source: "UNSAVED", storedRevisionId: null,
    definitionKey: scenario.definition.key, definitionRevision: 1,
    createdAt: "2026-08-30T10:00:00Z", lastAccessedAt: "2026-08-30T10:00:05Z",
    expiresAt: "2026-08-30T10:15:00Z", activeStatePath: ["root", "main"],
    storage: { count: kind === "EVENT" ? 2 : 0 }, history: input ? [input] : [], started: true, active: true,
    transcript: [{
      sequence, kind, at: "2026-08-30T10:00:05Z", input,
      activeStatePath: ["root", "main"],
      storageChanges: kind === "EVENT" ? { count: { before: 0, after: 2 } } : {},
      acceptedTransitionIds: kind === "EVENT" ? ["repeat"] : [],
      behaviour: generated ? { speech: "Deterministic preview response.", nonVerbal: null, motion: null, display: null } : null,
      diagnostics: [],
    }],
    diagnostics: [],
  };
}

function scenarioExecution(body) {
  const authored = body.definition.verification?.scenarios?.[body.scenarioIndex];
  const fragments = authored?.expected?.behaviourFragments ?? [];
  const forcedFailure = JSON.stringify(fragments).includes("force-fail");
  const expectedPath = authored?.expected?.activeStatePath ?? [];
  const actualPath = expectedPath.length ? expectedPath : ["session", "conversation"];
  const expectations = [];
  if (expectedPath.length) expectations.push({
    id: "active-state-path", label: "Active situation path", passed: true,
    expected: expectedPath, actual: actualPath,
    explanation: "The active path matched after the ordered events completed.",
  });
  fragments.forEach((fragment, index) => expectations.push({
    id: `behaviour-fragment-${index}`, label: `Behaviour fragment ${index + 1}`, passed: !forcedFailure,
    expected: fragment, actual: forcedFailure ? null : { speech: "Expected phrase" },
    explanation: forcedFailure
      ? "No emitted behaviour contained the expected fragment."
      : "An emitted speech behaviour contained the expected fragment.",
  }));
  return {
    scenarioIndex: body.scenarioIndex, name: authored?.name ?? "Scenario", passed: expectations.every((item) => item.passed),
    expectations, activeStatePath: actualPath, storage: {}, acceptedTransitionIds: ["repeat"], storageChanges: [],
    emittedModalities: ["speech"], transcript: [{
      sequence: 1, kind: "EVENT", at: "2026-08-30T10:00:05Z", input: authored?.events?.[0] ?? null,
      activeStatePath: actualPath, storageChanges: {}, acceptedTransitionIds: ["repeat"],
      behaviour: { speech: "Expected phrase", nonVerbal: null, motion: null, display: null }, diagnostics: [],
    }], diagnostics: [], discarded: true,
  };
}

function fulfillJson(route, body, status = 200) {
  return route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });
}
