import { useState } from "react";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import type { ComponentDefinition } from "../api/designerApi";
import type { AgentDefinitionV1 } from "../model/agentDefinition";
import { InteractionPanel } from "./InteractionPanel";
import { createDefaultDefinition, projectDefinition } from "./projection";

describe("InteractionPanel", () => {
  it("authors event, semantic condition, prompt effect, and a new destination through one rule card", async () => {
    const user = userEvent.setup();
    const definition = createDefaultDefinition();
    definition.interaction.supportedObservations = ["obs.user_utterance", "obs.emotion.face"];
    render(<Harness initial={definition} />);

    expect(screen.getByTestId("situation-card-main")).not.toBeNull();
    expect(screen.queryByText("Reactions")).toBeNull();
    expect(screen.queryByText("State flow")).toBeNull();
    expect(screen.queryByTestId("derived-flow-overview")).toBeNull();

    const main = screen.getByTestId("situation-card-main");
    await user.selectOptions(within(main).getByLabelText("When"), "obs.emotion.face");
    await user.click(within(main).getByRole("button", { name: "Add interaction rule" }));
    const rule = within(main).getByText("In this situation").closest("article")!;
    expect(screen.getByTestId("derived-flow-overview")).not.toBeNull();
    await user.click(within(rule).getByRole("button", { name: "Add condition" }));
    await user.clear(within(rule).getByLabelText("Decision criterion"));
    await user.type(within(rule).getByLabelText("Decision criterion"), "The person appears uncertain.");
    await user.click(within(rule).getByRole("button", { name: "Add positive example" }));
    await user.type(within(rule).getByLabelText("Positive example"), "They explicitly ask for clarification.");
    await user.click(within(rule).getByRole("button", { name: "Add effect" }));
    await user.clear(within(rule).getByLabelText("Response guidance"));
    await user.type(within(rule).getByLabelText("Response guidance"), "Acknowledge uncertainty and ask one question.");
    await user.type(within(rule).getByLabelText("New destination situation"), "Clarification");
    await user.click(within(rule).getByRole("button", { name: "Create and continue there" }));

    const represented = JSON.parse(screen.getByTestId("interaction-json").textContent ?? "{}") as AgentDefinitionV1;
    expect(represented.states.some((state) => state.kind === "atomic" && state.name === "Clarification")).toBe(true);
    expect(represented.transitions[0].decisions.map((item) => item.kind)).toEqual([
      "prometheus.decision.latest-event-type", "prometheus.decision.prompt",
    ]);
    expect(represented.transitions[0].actions[0].kind).toBe("prometheus.action.prompt-behaviour");
    expect(represented.transitions[0].targetStateId).not.toBe("main");
  });

  it("keeps rule-order controls keyboard reachable and exposes exact condition/effect focus targets", async () => {
    const user = userEvent.setup();
    const definition = createDefaultDefinition();
    definition.interaction.supportedObservations = ["obs.user_utterance"];
    definition.transitions = [rule("first", 10), rule("second", 20)];
    definition.transitions[0].decisions.push({
      kind: "prometheus.decision.prompt", version: 1,
      config: { decisionPrompt: { sections: [{ id: "criterion", kind: "transition-criterion", content: "When ready." }] } },
    });
    definition.transitions[0].actions.push({
      kind: "prometheus.action.prompt-behaviour", version: 1,
      config: { responsePrompt: { sections: [{ id: "response", kind: "objective", content: "Continue." }] } },
    });
    render(<Harness initial={definition} />);

    const moveEarlier = screen.getAllByRole("button", { name: "Move rule earlier" })[1];
    moveEarlier.focus();
    expect(document.activeElement).toBe(moveEarlier);
    await user.click(moveEarlier);
    const represented = JSON.parse(screen.getByTestId("interaction-json").textContent ?? "{}") as AgentDefinitionV1;
    expect(represented.transitions.sort((left, right) => left.order - right.order).map((item) => item.id)).toEqual(["second", "first"]);
    expect(document.querySelector("#interaction-rule-first-condition-1")).not.toBeNull();
    expect(document.querySelector("#interaction-rule-first-effect-0")).not.toBeNull();
  });
});

function Harness({ initial }: { initial: AgentDefinitionV1 }) {
  const [definition, setDefinition] = useState(initial);
  return <><InteractionPanel projection={projectDefinition(definition)} components={components()} readOnly={false}
    onChange={setDefinition} onGoToCapabilities={() => undefined} />
  <pre data-testid="interaction-json">{JSON.stringify(definition)}</pre></>;
}

function rule(id: string, order: number) {
  return {
    id, sourceStateId: "main", targetStateId: "main", order,
    decisions: [{ kind: "prometheus.decision.latest-event-type", version: 1, config: { eventType: "obs.user_utterance" } }],
    actions: [],
  };
}

function components(): ComponentDefinition[] {
  const base = {
    version: 1, exposure: "GUIDED" as const, advancedReason: null, examples: [],
    capabilities: { consumedObservations: [], emittedBehaviourModalities: [], storage: [], resources: [], states: [] },
  };
  return [{
    ...base, kind: "prometheus.policy.prompt", category: "POLICY", label: "Guided response",
    description: "Guided ordinary response.", authoringRole: "RESPONSE_STRATEGY", capabilityGroup: "prompt-response",
    configSchema: { type: "object" }, defaultConfig: {},
  }, {
    ...base, kind: "prometheus.decision.prompt", category: "DECISION", label: "Meaning-based condition",
    description: "Checks an ordinary-language criterion.", authoringRole: "RULE_CONDITION", capabilityGroup: "semantic-condition",
    configSchema: { type: "object" }, defaultConfig: {
      decisionPrompt: { sections: [{ id: "criterion", kind: "transition-criterion", content: "Describe when this applies." }] },
    },
  }, {
    ...base, kind: "prometheus.action.prompt-behaviour", category: "ACTION", label: "Guided response",
    description: "Produces a response for this rule.", authoringRole: "RULE_RESPONSE", capabilityGroup: "prompt-response",
    configSchema: { type: "object" }, defaultConfig: {
      responsePrompt: { sections: [{ id: "response", kind: "objective", content: "Describe the response." }] },
    },
  }];
}
