import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import type { ComponentDefinition } from "../api/designerApi";
import type { ComponentEnvelope } from "../model/agentDefinition";
import { ComponentEnvelopeEditor } from "./ComponentEnvelopeEditor";

it("renders and updates component-specific fields from the backend schema", async () => {
  const user = userEvent.setup();
  let current: ComponentEnvelope | null = {
    kind: "test.decision", version: 1, config: { eventType: "obs.user_utterance", types: ["one"], enabled: false },
  };
  render(<Harness initial={current} onCurrent={(value) => { current = value; }} />);

  await user.click(screen.getByText("Advanced configuration"));
  await user.clear(screen.getByLabelText(/Event Type/));
  await user.type(screen.getByLabelText(/Event Type/), "obs.hand.sign");
  await user.clear(screen.getByLabelText(/Types/));
  await user.type(screen.getByLabelText(/Types/), "rock, paper");
  await user.click(screen.getByRole("checkbox", { name: /Enabled/ }));

  expect(current?.config).toEqual({ eventType: "obs.hand.sign", types: ["rock", "paper"], enabled: true });
});

function Harness({ initial, onCurrent }: {
  initial: ComponentEnvelope;
  onCurrent: (value: ComponentEnvelope | null) => void;
}) {
  const [envelope, setEnvelope] = useState<ComponentEnvelope | null>(initial);
  return <ComponentEnvelopeEditor envelope={envelope} category="DECISION" components={[component]}
    label="Condition" onChange={(value) => { setEnvelope(value); onCurrent(value); }} />;
}

const component: ComponentDefinition = {
  kind: "test.decision", version: 1, category: "DECISION", label: "Test decision", description: "Schema-driven test.",
  authoringRole: "RULE_CONDITION", exposure: "GUIDED", capabilityGroup: "test-condition", advancedReason: null,
  configSchema: {
    type: "object",
    required: ["eventType"],
    properties: {
      eventType: { type: "string", description: "Observation type." },
      types: { type: "array", items: { type: "string" }, description: "Allowed values." },
      enabled: { type: "boolean", description: "Whether it is enabled." },
    },
  },
  defaultConfig: {}, examples: [],
  capabilities: { consumedObservations: [], emittedBehaviourModalities: [], storage: [], resources: [], states: [] },
};
