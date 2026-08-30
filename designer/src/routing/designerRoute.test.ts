import { describe, expect, it } from "vitest";
import { designerRouteHash, parseDesignerRoute } from "./designerRoute";

describe("designer routes", () => {
  it("round trips catalog, create, and encoded definition routes", () => {
    expect(parseDesignerRoute(designerRouteHash({ kind: "catalog" }))).toEqual({ kind: "catalog" });
    expect(parseDesignerRoute(designerRouteHash({ kind: "new" }))).toEqual({ kind: "new" });
    const editor = { kind: "editor", key: "designer.health/care", revision: 3 } as const;
    expect(parseDesignerRoute(designerRouteHash(editor))).toEqual(editor);
  });

  it("falls back safely for malformed or non-positive revisions", () => {
    expect(parseDesignerRoute("#/definitions/demo/revisions/0")).toEqual({ kind: "catalog" });
    expect(parseDesignerRoute("#/unexpected/path")).toEqual({ kind: "catalog" });
  });
});
