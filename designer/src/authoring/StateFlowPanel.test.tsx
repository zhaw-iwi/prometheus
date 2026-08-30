import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import type { DefinitionDiagnostic } from "../api/designerApi";
import type { AgentDefinitionV1 } from "../model/agentDefinition";
import { createDefaultDefinition } from "./editorModel";
import { addState, addTransition, assignParent, expandDefaultState } from "./graphModel";
import { graphElements, StateFlowPanel } from "./StateFlowPanel";

describe("StateFlowPanel", () => {
  it("renders composite containment plus self-transition and cycle edges from one document", () => {
    let definition = expandDefaultState(createDefaultDefinition());
    definition = addState(definition, "composite", "Session");
    const composite = definition.states.find((state) => state.kind === "composite")!;
    definition = assignParent(definition, "next_situation", composite.id);
    definition = addTransition(definition, "main", "main", "repeat");
    definition = addTransition(definition, "main", "next_situation", "advance");
    definition = addTransition(definition, "next_situation", "main", "return");
    definition = addState(definition, "composite", "Outer session");
    const outer = definition.states.find((state) => state.id === "outer_session")!;

    const { nodes, edges } = graphElements(definition);
    expect(nodes.find((node) => node.id === "main")?.parentId).toBe(composite.id);
    expect(nodes.find((node) => node.id === "next_situation")?.parentId).toBe(composite.id);
    expect(nodes.find((node) => node.id === composite.id)?.parentId).toBe(outer.id);
    expect(Number(nodes.find((node) => node.id === outer.id)?.style?.height))
      .toBeGreaterThan(Number(nodes.find((node) => node.id === composite.id)?.style?.height));
    expect(edges.map((edge) => [edge.source, edge.target])).toEqual([
      ["main", "main"], ["main", "next_situation"], ["next_situation", "main"],
    ]);
  });

  it("provides keyboard list operations equivalent to graph additions and reordering", async () => {
    const user = userEvent.setup();
    let current = createDefaultDefinition();
    render(<Harness initial={current} diagnostics={[]} onCurrent={(value) => { current = value; }} />);

    await user.click(screen.getByTestId("expand-default-state"));
    await user.click(screen.getByRole("button", { name: "Add finished" }));
    await user.click(screen.getByRole("button", { name: "Add move" }));
    await user.click(screen.getByTestId("show-list-view"));

    expect(screen.getByTestId("state-flow-list").textContent).toContain("Next situation");
    expect(screen.getByTestId("state-flow-list").textContent).toContain("Finished");
    expect(current.states).toHaveLength(3);
    expect(current.transitions).toHaveLength(1);
    await user.click(screen.getByRole("button", { name: `Delete move ${current.transitions[0].id}` }));
    expect(current.transitions).toHaveLength(0);
  });

  it("focuses the exact erroneous edge and opens its accessible inspector", async () => {
    const user = userEvent.setup();
    const definition = addTransition(createDefaultDefinition(), "main", "main", "repeat");
    const diagnostic: DefinitionDiagnostic = {
      code: "TEST_EDGE", severity: "ERROR", pointer: "/transitions/0/targetStateId",
      message: "Target is invalid.", hint: null,
    };
    render(<Harness initial={definition} diagnostics={[diagnostic]} onCurrent={() => undefined} />);

    const target = screen.getByRole("button", { name: "Move repeat_main has a validation issue" });
    await user.click(target);

    expect(screen.getByTestId("state-flow-list")).not.toBeNull();
    expect(screen.getByDisplayValue("repeat_main")).not.toBeNull();
  });
});

function Harness({ initial, diagnostics, onCurrent }: {
  initial: AgentDefinitionV1;
  diagnostics: DefinitionDiagnostic[];
  onCurrent: (definition: AgentDefinitionV1) => void;
}) {
  const [definition, setDefinition] = useState(initial);
  return <StateFlowPanel definition={definition} components={[]} diagnostics={diagnostics} onChange={(next) => {
    onCurrent(next);
    setDefinition(next);
  }} />;
}
