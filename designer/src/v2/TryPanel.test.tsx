import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import type { RequestFunction, ScenarioExecutionResult } from "../api/designerApi";
import type { AgentDefinitionV1 } from "../model/agentDefinition";
import { TryPanel } from "./TryPanel";
import { createDefaultDefinition, projectDefinition } from "./projection";

function response(payload: unknown, status = 200): Response {
  return { ok: status >= 200 && status < 300, status, json: async () => payload } as Response;
}

function definition(): AgentDefinitionV1 {
  const source = createDefaultDefinition();
  source.key = "designer.try_panel";
  source.interaction.supportedObservations = ["obs.user_utterance"];
  source.interaction.supportedBehaviourModalities = ["speech"];
  source.storage = [{
    key: "score", description: "Current score", valueSchema: { type: "integer" },
    required: true, visibility: "working", reset: "initial", initialValue: 0,
  }];
  return source;
}

function Harness({ source = definition(), request = vi.fn() as RequestFunction }: {
  source?: AgentDefinitionV1; request?: RequestFunction;
}) {
  const [document, setDocument] = useState(source);
  return <><TryPanel projection={projectDefinition(document)} readOnly={false} adminToken="admin-token"
    request={request} onChange={setDocument} onDiagnostics={() => undefined} />
    <pre data-testid="scenario-document">{JSON.stringify(document)}</pre></>;
}

describe("TryPanel", () => {
  it("authors canonical Given, When, and Expect fields through guided controls", async () => {
    const user = userEvent.setup();
    render(<Harness />);

    await user.click(screen.getByRole("button", { name: "Add scenario" }));
    const card = screen.getByTestId("scenario-card-0");
    await user.clear(within(card).getByLabelText("Scenario 1 name"));
    await user.type(within(card).getByLabelText("Scenario 1 name"), "Greeting stays in Main");
    await user.type(within(card).getByLabelText("Initializer seed (optional)"), "17");
    await user.click(within(card).getByRole("button", { name: "What the person says" }));
    await user.clear(within(card).getByLabelText("Event payload"));
    await user.type(within(card).getByLabelText("Event payload"), "Hello there");
    await user.selectOptions(within(card).getByLabelText("Active situation after all events (optional)"), "main");
    const addData = within(card).getAllByRole("button", { name: "Add data value" });
    await user.click(addData[0]);
    await user.click(within(card).getAllByRole("button", { name: "Add data value" })[0]);
    await user.click(within(card).getByRole("button", { name: "Add behaviour expectation" }));

    const saved = JSON.parse(screen.getByTestId("scenario-document").textContent ?? "{}") as AgentDefinitionV1;
    expect(saved.verification?.scenarios[0]).toMatchObject({
      name: "Greeting stays in Main",
      initializerSeed: 17,
      initialStorage: { score: 0 },
      events: [{ type: "obs.user_utterance", actor: "user", kind: "observation", payload: "Hello there" }],
      expected: { activeStatePath: ["context", "main"], storage: { score: 0 }, behaviourFragments: [{ speech: "Expected phrase" }] },
    });
  });

  it("runs the unsaved canonical scenario and exposes pass evidence, staleness, and clearing", async () => {
    const user = userEvent.setup();
    const source = definition();
    source.verification = { scenarios: [{ name: "Pass", events: [], expected: { activeStatePath: ["main"] } }] };
    const result = execution(true);
    const request = vi.fn().mockResolvedValue(response(result)) as RequestFunction;
    render(<Harness source={source} request={request} />);

    await user.click(screen.getByTestId("run-scenario-0"));
    expect(await screen.findByText("All expectations passed")).not.toBeNull();
    expect(screen.getByText(/disposable runtime session has been discarded/i)).not.toBeNull();
    expect(request).toHaveBeenCalledTimes(1);
    const [path, init] = vi.mocked(request).mock.calls[0];
    expect(path).toBe("/admin/agent-definitions/previews/scenarios");
    expect(new Headers(init?.headers).get("X-Prometheus-Admin-Token")).toBe("admin-token");
    expect(JSON.parse(String(init?.body))).toMatchObject({ scenarioIndex: 0, definition: { key: source.key } });

    await user.type(screen.getByLabelText("Scenario 1 name"), " changed");
    expect(screen.getByText(/predates the latest scenario edits/i)).not.toBeNull();
    await user.click(screen.getByRole("button", { name: "Clear result" }));
    expect(screen.queryByTestId("scenario-result-0")).toBeNull();
  });

  it("renders failed expectations and safe trace explanations without claiming success", async () => {
    const user = userEvent.setup();
    const source = definition();
    source.verification = { scenarios: [{ name: "Fail", events: [], expected: { activeStatePath: ["main"] } }] };
    const request = vi.fn().mockResolvedValue(response(execution(false))) as RequestFunction;
    render(<Harness source={source} request={request} />);

    await user.click(screen.getByTestId("run-scenario-0"));
    expect(await screen.findByText("Some expectations failed")).not.toBeNull();
    await user.click(screen.getByText("Why did this not happen?"));
    expect(screen.getByText("The active path did not match the expected situation.")).not.toBeNull();
    expect(screen.getByText("Did not match")).not.toBeNull();
  });
});

function execution(passed: boolean): ScenarioExecutionResult {
  return {
    scenarioIndex: 0,
    name: passed ? "Pass" : "Fail",
    passed,
    expectations: [{
      id: "active-state-path", label: "Active situation path", passed,
      expected: ["main"], actual: passed ? ["main"] : ["finished"],
      explanation: passed ? "The active path matched Main."
        : "The active path did not match the expected situation.",
    }],
    activeStatePath: passed ? ["main"] : ["finished"],
    storage: {}, acceptedTransitionIds: passed ? [] : ["finish"], storageChanges: [],
    emittedModalities: ["speech"], transcript: [], diagnostics: [], discarded: true,
  };
}
