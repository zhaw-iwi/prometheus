import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ADMIN_TOKEN_STORAGE_KEY, type DefinitionSummary, type RequestFunction } from "../api/designerApi";
import { DesignerApp } from "./DesignerApp";

function response(payload: unknown, status = 200): Response {
  return { ok: status >= 200 && status < 300, status, json: async () => payload } as Response;
}

const definition: DefinitionSummary = {
  key: "designer.test",
  activeRevisionId: 4,
  activeRevision: 2,
  optimisticVersion: 0,
  displayName: "Test guide",
  description: "A focused guided definition.",
  categoryPath: ["designer", "tests"],
  languageCode: "en",
  revisions: [{
    id: 4,
    revision: 2,
    status: "PUBLISHED",
    provenance: "DESIGNER",
    optimisticVersion: 1,
    updatedAt: "2026-08-30T10:00:00Z",
  }],
};

describe("DesignerApp catalog", () => {
  beforeEach(() => {
    sessionStorage.setItem(ADMIN_TOKEN_STORAGE_KEY, "admin-session-token");
  });

  it("renders the catalog loading state while the request is pending", () => {
    const request = vi.fn(() => new Promise<Response>(() => undefined)) as RequestFunction;
    render(<DesignerApp request={request} />);

    expect(screen.getByTestId("catalog-loading").getAttribute("aria-busy")).toBe("true");
  });

  it("renders an empty state and routes create into the six-step editor", async () => {
    const user = userEvent.setup();
    const request = vi.fn().mockResolvedValue(response([]));
    render(<DesignerApp request={request} />);

    expect(await screen.findByTestId("catalog-empty")).not.toBeNull();
    await user.click(screen.getByTestId("create-definition"));
    expect(await screen.findByTestId("designer-editor")).not.toBeNull();
    expect(screen.getAllByRole("tab")).toHaveLength(6);
    expect(window.location.hash).toBe("#/new");
  });

  it("renders a populated catalog and opens an existing revision route", async () => {
    const user = userEvent.setup();
    const request = vi.fn().mockResolvedValue(response([definition]));
    render(<DesignerApp request={request} />);

    expect(await screen.findByTestId("catalog-populated")).not.toBeNull();
    expect(screen.getByText("Test guide")).not.toBeNull();
    await user.click(screen.getByTestId("open-definition-designer.test"));
    expect(await screen.findByTestId("designer-editor")).not.toBeNull();
    expect(window.location.hash).toBe("#/definitions/designer.test/revisions/2");
  });

  it("renders an actionable error state and retries", async () => {
    const user = userEvent.setup();
    const request = vi.fn()
      .mockResolvedValueOnce(response({}, 500))
      .mockResolvedValueOnce(response([definition]));
    render(<DesignerApp request={request} />);

    expect(await screen.findByTestId("catalog-error")).not.toBeNull();
    await user.click(screen.getByTestId("retry-catalog"));
    expect(await screen.findByTestId("catalog-populated")).not.toBeNull();
    expect(request).toHaveBeenCalledTimes(2);
  });
});

describe("DesignerApp token entry", () => {
  it("stores the existing admin convention and loads the catalog", async () => {
    sessionStorage.clear();
    const user = userEvent.setup();
    const request = vi.fn().mockResolvedValue(response([]));
    render(<DesignerApp request={request} />);

    expect(screen.getByTestId("designer-token-panel")).not.toBeNull();
    await user.type(screen.getByTestId("designer-token-input"), "entered-token");
    await user.click(screen.getByTestId("submit-designer-token"));

    expect(await screen.findByTestId("catalog-empty")).not.toBeNull();
    expect(sessionStorage.getItem(ADMIN_TOKEN_STORAGE_KEY)).toBe("entered-token");
    await waitFor(() => expect(request).toHaveBeenCalledTimes(1));
  });
});
