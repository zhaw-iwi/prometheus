import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { DESIGNER_STEPS } from "../stepper/DesignerStepper";

describe("Designer V1 removal contract", () => {
  it("keeps one six-domain shell with no obsolete authoring modules, styles, or dependency", () => {
    const repositoryRoot = existsSync(resolve(process.cwd(), "designer/src/styles.css"))
      ? process.cwd()
      : resolve(process.cwd(), "..");
    const obsoleteModules = [
      "AuthoringPanels.tsx",
      "ComponentEnvelopeEditor.tsx",
      "DefinitionAuthoringEditor.tsx",
      "ReactionPanel.tsx",
      "StateFlowPanel.tsx",
      "authoringCatalog.ts",
      "editorModel.ts",
      "graphModel.ts",
    ];
    obsoleteModules.forEach((file) => {
      expect(existsSync(resolve(repositoryRoot, "designer/src/authoring", file)), file).toBe(false);
    });

    expect(DESIGNER_STEPS.map((step) => step.title)).toEqual([
      "Brief", "Capabilities", "Interaction", "Data & outcome", "Try", "Review",
    ]);
    const styles = readFileSync(resolve(repositoryRoot, "designer/src/styles.css"), "utf8");
    expect(styles).not.toMatch(/\.(?:purpose|sensing|behaviour|reaction|state-flow)-(?:panel|section|canvas)\b/);
    const packageJson = readFileSync(resolve(repositoryRoot, "package.json"), "utf8");
    expect(packageJson).not.toMatch(/react-?flow/i);
  });
});
