import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";
import type { ComponentDefinition } from "../api/designerApi";
import { BehaviourPanel, PurposePanel, SensingPanel } from "./AuthoringPanels";
import { createDefaultDefinition, definitionToAuthoringForm, type AuthoringForm } from "./editorModel";

describe("Purpose, Sensing, and Behaviour panels", () => {
  it("keeps prompt examples inert while viewing and adopts them only through the explicit action", async () => {
    const user = userEvent.setup();
    render(<Harness panel="purpose" />);

    expect(screen.getByTestId("dirty-probe").textContent).toBe("clean");
    const personaExample = screen.getByTestId("example-purpose.persona");
    await user.click(personaExample.querySelector("summary")!);
    expect(screen.getByTestId("dirty-probe").textContent).toBe("clean");
    await user.click(screen.getByTestId("adopt-example-purpose.persona"));

    expect(screen.getByTestId("dirty-probe").textContent).toBe("dirty");
    expect((screen.getByTestId("prompt-purpose-persona") as HTMLTextAreaElement).value).toContain("coaching assistant");
  });

  it("selects sensing capabilities and reports current strategy usage", async () => {
    const user = userEvent.setup();
    render(<Harness panel="sensing" />);

    await user.click(screen.getByTestId("observation-obs.user_utterance"));

    expect(screen.getByText("1 selected")).not.toBeNull();
    expect(screen.getByText("Used by the main strategy")).not.toBeNull();
  });

  it("uses backend component metadata and disables strategies incompatible with selected modalities", async () => {
    const user = userEvent.setup();
    render(<Harness panel="behaviour" />);

    await user.click(screen.getByTestId("modality-speech"));
    await user.click(screen.getByTestId("modality-display"));

    expect(screen.queryByTestId("strategy-prometheus.policy.exact-text")).toBeNull();
    expect((screen.getByTestId("strategy-prometheus.policy.prompt") as HTMLInputElement).disabled).toBe(false);
    expect(screen.getByTestId("behaviour-summary").textContent).toContain("Speech, Display content");
  });
});

function Harness({ panel }: { panel: "purpose" | "sensing" | "behaviour" }) {
  const [form, setForm] = useState<AuthoringForm>(() => definitionToAuthoringForm(createDefaultDefinition()));
  const [dirty, setDirty] = useState(false);
  const change = (next: AuthoringForm) => { setForm(next); setDirty(true); };
  return <>
    <output data-testid="dirty-probe">{dirty ? "dirty" : "clean"}</output>
    {panel === "purpose" && <PurposePanel form={form} onChange={change} isNew
      keyConfirmed={false} onKeyConfirmed={() => undefined} />}
    {panel === "sensing" && <SensingPanel form={form} onChange={change} />}
    {panel === "behaviour" && <BehaviourPanel form={form} onChange={change} components={components} />}
  </>;
}

const components: ComponentDefinition[] = [
  {
    kind: "prometheus.policy.prompt", version: 1, category: "POLICY", label: "Prompt policy",
    description: "Produces behaviour from typed prompt roles.",
    configSchema: { properties: { emittedModalities: { type: "array" } } },
    defaultConfig: { responsePrompt: { sections: [{ id: "response.objective", kind: "objective", content: "Example" }] } },
    examples: [{ responsePrompt: { sections: [{ id: "response.objective", kind: "objective", content: "Example" }] } }],
    capabilities: { consumedObservations: [], emittedBehaviourModalities: [], storage: [], resources: [], states: [] },
  },
  {
    kind: "prometheus.policy.exact-text", version: 1, category: "POLICY", label: "Exact text",
    description: "Emits the latest payload exactly.", configSchema: {},
    defaultConfig: { eventType: "obs.user_utterance", actor: "user", eventKind: "observation", maxTextCodePoints: 2000 },
    examples: [],
    capabilities: { consumedObservations: ["obs.user_utterance"], emittedBehaviourModalities: ["speech"], storage: [], resources: [], states: [] },
  },
];
