import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { addRule, addSituation, setRuleContinuation } from "../v2/interactionModel";
import { createDefaultDefinition, projectDefinition } from "../v2/projection";
import { AdvancedDefinitionViews } from "./AdvancedDefinitionViews";

describe("AdvancedDefinitionViews", () => {
  it("keeps the full graph and accessible rule list equivalent after guided interaction edits", () => {
    let projection = addSituation(projectDefinition(createDefaultDefinition()), "Follow up");
    projection = addRule(projection, "main", "obs.user_utterance");
    const rule = projection.rules[0];
    projection = setRuleContinuation(projection, rule.id, "move", "follow-up");

    render(<AdvancedDefinitionViews definition={projection.source} />);

    const graph = screen.getByTestId("advanced-flow-graph");
    projection.source.states.forEach((state) => {
      expect(graph.querySelector(`[data-state-id="${state.id}"]`)).not.toBeNull();
    });
    const list = screen.getByTestId("advanced-flow-list");
    const row = list.querySelector(`[data-rule-id="${rule.id}"]`);
    expect(row).not.toBeNull();
    expect(within(row as HTMLElement).getByText("Main interaction → Follow up")).not.toBeNull();
    expect(within(row as HTMLElement).getByText("obs.user_utterance")).not.toBeNull();
    expect(within(row as HTMLElement).getByText("move")).not.toBeNull();
    expect(screen.getByTestId("advanced-state-audit").textContent).toContain("follow-up");
    expect(screen.getByTestId("advanced-component-audit").textContent).toContain("/transitions/0/decisions/0");
  });
});
