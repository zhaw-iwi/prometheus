import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { DomainHelp } from "./DomainHelp";

describe("DomainHelp", () => {
  it("explains the six cross-domain decisions without technical vocabulary", async () => {
    const user = userEvent.setup();
    render(<DomainHelp />);

    expect((screen.getByTestId("domain-help") as HTMLDetailsElement).open).toBe(false);
    await user.click(screen.getByText("How the Designer concepts fit together"));
    expect((screen.getByTestId("domain-help") as HTMLDetailsElement).open).toBe(true);

    [
      "Situation or context?",
      "Ordinary response or rule?",
      "Stay, move, or finish?",
      "Where does guidance apply?",
      "Which data role?",
      "What can prompt guidance guarantee?",
    ].forEach((heading) => expect(screen.getByRole("heading", { name: heading })).not.toBeNull());
    expect(screen.getByText(/cannot guarantee safety, access control, privacy, or clinical correctness/)).not.toBeNull();
    expect(screen.getByTestId("domain-help").textContent).not.toContain("prometheus.");
  });
});
