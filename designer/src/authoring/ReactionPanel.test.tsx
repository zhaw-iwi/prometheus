import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import type { ComponentDefinition } from "../api/designerApi";
import type { AgentDefinitionV1 } from "../model/agentDefinition";
import { createDefaultDefinition } from "./editorModel";
import { addTransition } from "./graphModel";
import { ReactionPanel } from "./ReactionPanel";

describe("ReactionPanel", () => {
  it("authors a guided reaction directly into ordered transition components", async () => {
    const user = userEvent.setup();
    const definition = createDefaultDefinition();
    definition.interaction.supportedObservations = ["obs.user_utterance"];
    definition.interaction.supportedBehaviourModalities = ["speech"];
    let current = definition;
    render(<Harness initial={definition} components={[]} onCurrent={(value) => { current = value; }} />);

    await user.click(screen.getByTestId("add-reaction"));
    const card = screen.getByTestId("reaction-reaction_main");
    const response = card.querySelector("textarea");
    expect(response).not.toBeNull();
    await user.type(response!, "Acknowledge the request briefly.");

    expect(current.transitions[0]).toMatchObject({
      id: "reaction_main", sourceStateId: "main", targetStateId: "main", order: 10,
      decisions: [{ kind: "prometheus.decision.latest-event-type", config: { eventType: "obs.user_utterance" } }],
    });
    expect(current.transitions[0].actions[0]).toMatchObject({
      kind: "prometheus.action.prompt-behaviour",
      config: { consumedObservations: ["obs.user_utterance"], emittedModalities: ["speech"] },
    });
    expect(JSON.stringify(current.transitions[0].actions[0].config)).toContain("Acknowledge the request briefly.");
  });

  it("offers an explicit synchronized update for advanced undeclared capabilities", async () => {
    const user = userEvent.setup();
    let definition = addTransition(createDefaultDefinition(), "main", "main", "advanced");
    definition.transitions[0].actions = [{ kind: "test.action.display", version: 1, config: {} }];
    let current = definition;
    render(<Harness initial={definition} components={[displayAction]} onCurrent={(value) => { current = value; }} />);

    expect(screen.getByTestId("capability-sync-offer").textContent).toContain("display");
    await user.click(screen.getByRole("button", { name: "Add to Sensing and Behaviour" }));

    expect(current.interaction.supportedBehaviourModalities).toEqual(["display"]);
    expect(screen.queryByTestId("capability-sync-offer")).toBeNull();
  });
});

function Harness({ initial, components, onCurrent }: {
  initial: AgentDefinitionV1;
  components: ComponentDefinition[];
  onCurrent: (definition: AgentDefinitionV1) => void;
}) {
  const [definition, setDefinition] = useState(initial);
  return <ReactionPanel definition={definition} components={components} diagnostics={[]} onChange={(next) => {
    onCurrent(next);
    setDefinition(next);
  }} />;
}

const displayAction: ComponentDefinition = {
  kind: "test.action.display", version: 1, category: "ACTION", configSchema: {}, label: "Display",
  description: "Display output", defaultConfig: {}, examples: [],
  authoringRole: "DETERMINISTIC_OPERATION", exposure: "GUIDED", capabilityGroup: "test-display", advancedReason: null,
  capabilities: { consumedObservations: [], emittedBehaviourModalities: ["display"], storage: [], resources: [], states: [] },
};
