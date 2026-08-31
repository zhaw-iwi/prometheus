import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { clampStepIndex, DESIGNER_STEPS, DesignerStepper } from "./DesignerStepper";

describe("DesignerStepper", () => {
  it("renders the exact six steps with synchronized tab and panel ARIA state", async () => {
    const user = userEvent.setup();
    render(<DesignerStepper />);

    const tabs = screen.getAllByRole("tab");
    expect(tabs).toHaveLength(6);
    expect(tabs.map((tab) => tab.textContent)).toEqual(expect.arrayContaining(
      DESIGNER_STEPS.map((step) => expect.stringContaining(step.title)),
    ));
    expect(tabs[0].getAttribute("aria-current")).toBe("step");
    expect(tabs[0].getAttribute("aria-selected")).toBe("true");
    expect((screen.getByTestId("step-panel-brief") as HTMLElement).hidden).toBe(false);

    await user.click(screen.getByTestId("step-target-data-outcome"));

    expect(screen.getByTestId("step-target-data-outcome").getAttribute("aria-current")).toBe("step");
    expect(screen.getByTestId("step-target-brief").hasAttribute("aria-current")).toBe(false);
    expect((screen.getByTestId("step-panel-data-outcome") as HTMLElement).hidden).toBe(false);
    expect((screen.getByTestId("step-panel-brief") as HTMLElement).hidden).toBe(true);
  });

  it("navigates with next and back and clamps requests to valid bounds", async () => {
    const user = userEvent.setup();
    render(<DesignerStepper />);

    expect((screen.getByTestId("step-back-brief") as HTMLButtonElement).disabled).toBe(true);
    await user.click(screen.getByTestId("step-next-brief"));
    expect(screen.getByTestId("step-target-capabilities").getAttribute("aria-selected")).toBe("true");
    await user.click(screen.getByTestId("step-back-capabilities"));
    expect(screen.getByTestId("step-target-brief").getAttribute("aria-selected")).toBe("true");
    expect(clampStepIndex(-10)).toBe(0);
    expect(clampStepIndex(99)).toBe(5);

    await user.click(screen.getByTestId("step-target-review"));
    expect((screen.getByTestId("step-next-review") as HTMLButtonElement).disabled).toBe(true);
  });

  it("opens and focuses the panel identified by a validation target", () => {
    const onStepChange = vi.fn();
    const field = <input id="capability-card" aria-label="Capability card" />;
    render(<DesignerStepper panels={{ capabilities: field }} onStepChange={onStepChange}
      validationTarget={{ stepId: "capabilities", fieldId: "capability-card", message: "Choose a capability." }} />);

    expect((screen.getByTestId("step-panel-capabilities") as HTMLElement).hidden).toBe(false);
    expect(document.activeElement).toBe(screen.getByLabelText("Capability card"));
    expect(screen.getByRole("alert").textContent).toContain("Choose a capability.");
    expect(onStepChange).toHaveBeenCalledWith("capabilities");
  });
});
