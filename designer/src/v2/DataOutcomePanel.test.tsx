import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { useState } from "react";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import type { AgentDefinitionV1 } from "../model/agentDefinition";
import { DataOutcomePanel } from "./DataOutcomePanel";
import { createDefaultDefinition, projectDefinition } from "./projection";

const CATALOG_ROOT = resolve(process.cwd(), "src/main/resources/agent-definitions/catalog/main");

describe("DataOutcomePanel", () => {
  it("authors starting context and a guided outcome from the empty document", async () => {
    const user = userEvent.setup();
    const definition = createDefaultDefinition();
    definition.states.push({ id: "done", name: "Done", kind: "final" });
    definition.transitions.push({
      id: "finish", sourceStateId: "main", targetStateId: "done", order: 10, decisions: [], actions: [],
    });
    render(<Harness initial={definition} />);

    expect(screen.getByTestId("data-role-starting-context")).not.toBeNull();
    expect(screen.getByTestId("data-role-working-data")).not.toBeNull();
    expect(screen.getByTestId("data-role-learned-information")).not.toBeNull();
    expect(screen.getByTestId("data-role-outcome-report")).not.toBeNull();
    expect(screen.queryByText("Data & outcome projection")).toBeNull();

    const contextSection = screen.getByTestId("data-role-starting-context");
    await user.type(within(contextSection).getByLabelText("New starting context key"), "visitorContext");
    await user.click(within(contextSection).getByRole("button", { name: "Add value" }));
    const contextCard = screen.getByTestId("data-item-card-visitorContext");
    await user.click(within(contextCard).getByRole("button", { name: "Use typed choices" }));
    expect(screen.getByTestId("typed-choices-visitorContext")).not.toBeNull();

    const outcomeSection = screen.getByTestId("data-role-outcome-report");
    await user.click(within(outcomeSection).getByRole("button", { name: "Add outcome report" }));
    expect(screen.getByTestId("guided-outcome-outcome")).not.toBeNull();
    const represented = representedDefinition();
    expect(represented.resources[0]).toMatchObject({ kind: "prometheus.resource.typed-choices" });
    expect(represented.lifecycle.initializers[0]).toMatchObject({ kind: "prometheus.initializer.random-choice" });
    expect(represented.transitions[0].actions[0]).toMatchObject({
      kind: "prometheus.action.extract", config: { targetStorageKey: "outcome" },
    });
  });

  it("shows registered rock-scissor-paper storage as one owned pack", () => {
    render(<Harness initial={bundledDefinition("core.rock_scissor_paper")} />);

    const owned = screen.getByTestId("operation-data-rock-scissor-paper");
    expect(within(owned).getByText("4 owned values")).not.toBeNull();
    expect(within(owned).getByText("Deterministically selected agent hand sign.")).not.toBeNull();
    expect(screen.queryByTestId("data-item-card-rps_current_agent_sign")).toBeNull();
  });

  it("previews and cancels custom SMART conversion without mutation, then applies it explicitly", async () => {
    const user = userEvent.setup();
    const source = bundledDefinition("usecases.healthcare.smart_goal_coaching");
    render(<Harness initial={source} />);

    const custom = screen.getByTestId("custom-outcome-outcome");
    expect(within(custom).getAllByLabelText("Instruction")).toHaveLength(2);
    await user.click(within(custom).getByRole("button", { name: "Convert to guided fields…" }));
    await user.click(within(custom).getByRole("button", { name: "Preview canonical change" }));
    expect(screen.getByTestId("outcome-conversion-diff")).not.toBeNull();
    await user.click(within(custom).getByRole("button", { name: "Cancel" }));
    expect(representedDefinition()).toEqual(source);

    await user.click(within(custom).getByRole("button", { name: "Convert to guided fields…" }));
    await user.click(within(custom).getByRole("button", { name: "Preview canonical change" }));
    await user.click(within(custom).getByRole("button", { name: "Apply conversion" }));
    expect(await screen.findByTestId("guided-outcome-outcome")).not.toBeNull();
    expect((representedDefinition().storage[0].valueSchema as Record<string, unknown>).additionalProperties).toBe(false);
  });
});

function Harness({ initial }: { initial: AgentDefinitionV1 }) {
  const [definition, setDefinition] = useState(initial);
  return <><DataOutcomePanel projection={projectDefinition(definition)} readOnly={false} onChange={setDefinition}
    onGoToInteraction={() => undefined} />
  <pre data-testid="data-json">{JSON.stringify(definition)}</pre></>;
}

function representedDefinition(): AgentDefinitionV1 {
  return JSON.parse(screen.getByTestId("data-json").textContent ?? "{}") as AgentDefinitionV1;
}

function bundledDefinition(key: string): AgentDefinitionV1 {
  const manifest = JSON.parse(readFileSync(resolve(CATALOG_ROOT, "manifest.json"), "utf8")) as {
    entries: Array<{ key: string; resource: string }>;
  };
  const entry = manifest.entries.find((candidate) => candidate.key === key);
  if (!entry) throw new Error(`Missing bundled definition ${key}`);
  return JSON.parse(readFileSync(resolve(CATALOG_ROOT, entry.resource), "utf8")) as AgentDefinitionV1;
}
