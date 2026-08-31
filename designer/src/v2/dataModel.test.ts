import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import type { AgentDefinitionV1 } from "../model/agentDefinition";
import {
  addDataItem,
  addGuidedOutcome,
  convertOutcomeToGuided,
  dataReferences,
  defaultOutcomeField,
  deleteDataItem,
  detachOutcomeExtraction,
  operationOwnedData,
  outcomeAttachments,
  outcomeConversionPreview,
  outcomeMode,
  renameDataItem,
  replaceStructuredDataFields,
  replaceGuidedOutcomeFields,
  replaceTypedChoices,
  setFixedInitialValue,
  typedChoiceSetup,
  type GuidedField,
} from "./dataModel";
import { createDefaultDefinition, projectDefinition, serializedDefinition } from "./projection";

const CATALOG_ROOT = resolve(process.cwd(), "src/main/resources/agent-definitions/catalog/main");

describe("Designer V2 data and outcome model", () => {
  it("classifies all twelve bundled documents without changing their canonical representation", () => {
    const definitions = bundledDefinitions();
    expect(definitions).toHaveLength(12);

    for (const definition of definitions) {
      const before = serializedDefinition(definition);
      const projection = projectDefinition(definition);
      expect(projection.data.items).toHaveLength(definition.storage.length);
      expect(projection.data.items.every((item) => [
        "starting-context", "working-data", "learned-information", "outcome-report",
      ].includes(item.role))).toBe(true);
      expect(serializedDefinition(projection.source)).toBe(before);
    }
  });

  it("edits therapy appointment context through the registered typed-choice resource", () => {
    const source = bundledDefinition("usecases.healthcare.therapy_appointment_reminder");
    const original = structuredClone(source);
    let projection = projectDefinition(source);
    const context = projection.data.items.find((item) => item.key === "therapyAppointmentContext")!;
    expect(context.role).toBe("starting-context");
    expect(typedChoiceSetup(projection.source, context.key)).toMatchObject({
      source: "resource", values: [{ type: "physiotherapy" }, { type: "occupational_therapy" }, { type: "activation" }],
    });

    const values = typedChoiceSetup(projection.source, context.key)!.values;
    const edited = structuredClone(values);
    (edited[0] as Record<string, unknown>).safeFocus = "safe walking and mobility";
    edited.push({ type: "speech", label: "speech therapy", safeFocus: "communication", examples: ["word practice"] });
    projection = replaceTypedChoices(projection, context.key, edited);

    expect(typedChoiceSetup(projection.source, context.key)?.values).toEqual(edited);
    expect(projection.source.lifecycle.initializers).toHaveLength(1);
    expect(projection.source.resources).toHaveLength(1);
    expect(source).toEqual(original);

    const structured = replaceStructuredDataFields(projection, context.storageIndex, [
      { key: "type", label: "Therapy type", type: "string", required: true, enumValues: [] },
      { key: "safeFocus", label: "Safe focus", type: "string", required: true, enumValues: [] },
      { key: "examples", label: "Examples", type: "string-list", required: false, enumValues: [] },
    ]);
    const structuredChoices = typedChoiceSetup(structured.source, context.key)!.values as Array<Record<string, unknown>>;
    expect(structuredChoices[0]).toEqual({
      type: "physiotherapy", safeFocus: "safe walking and mobility",
      examples: ["walking with support", "gentle strength practice", "mobility exercises"],
    });
    expect(structuredChoices.every((choice) => !("label" in choice))).toBe(true);
    expect(structured.source.lifecycle.initializers[0].config.storageKey).toBe(context.key);
    expect(structured.source.storage[context.storageIndex]).toMatchObject({ required: true, reset: "initial" });
  });

  it("groups all four rock-scissor-paper values as operation-owned data without mutating them", () => {
    const source = bundledDefinition("core.rock_scissor_paper");
    const before = serializedDefinition(source);
    const projection = projectDefinition(source);

    expect(operationOwnedData(projection)).toEqual([{
      group: "rock-scissor-paper",
      itemKeys: ["rps_current_agent_sign", "rps_current_round_number", "rps_last_round", "rps_rounds"],
    }]);
    expect(serializedDefinition(projection.source)).toBe(before);
  });

  it("classifies initialized, operation-written, and outcome values by lifecycle purpose", () => {
    let projection = projectDefinition(createDefaultDefinition());
    projection = addDataItem(projection, "starting-context", "visitorContext");
    projection = addDataItem(projection, "working-data", "progressCount", "integer");
    projection = addDataItem(projection, "working-data", "notes", "string-list");
    const progress = projection.data.items.find((item) => item.key === "progressCount")!;
    projection.source.transitions.push({
      id: "record-progress", sourceStateId: "main", targetStateId: "main", order: 10, decisions: [],
      actions: [{ kind: "prometheus.action.increment", version: 1, config: { targetStorageKey: progress.key, amount: 1 } }],
    });
    projection = projectDefinition(projection.source);
    projection = addGuidedOutcome(projection, "report");

    expect(projection.data.items.find((item) => item.key === "visitorContext")?.role).toBe("starting-context");
    expect(projection.data.items.find((item) => item.key === progress.key)?.role).toBe("learned-information");
    expect(projection.data.items.find((item) => item.key === "notes")?.declaration.valueSchema)
      .toEqual({ type: "array", items: { type: "string" } });
    expect(projection.data.items.find((item) => item.key === "report")?.role).toBe("outcome-report");
  });

  it("generates a strict outcome schema and deterministic extraction contract on finish rules", () => {
    const definition = createDefaultDefinition();
    definition.states.push({ id: "complete", name: "Complete", kind: "final" });
    definition.transitions.push({
      id: "finish", sourceStateId: "main", targetStateId: "complete", order: 10, decisions: [], actions: [],
    });
    let projection = addGuidedOutcome(projectDefinition(definition), "outcome", [
      { key: "summary", label: "Summary", type: "string", required: true, enumValues: [] },
      { key: "completed", label: "Completed", type: "boolean", required: true, enumValues: [] },
      { key: "status", label: "Status", type: "string", required: false, enumValues: ["complete", "partial"] },
    ]);
    const outcome = projection.outcomes.items[0];
    expect(outcomeMode(projection.source, outcome)).toBe("guided");
    expect(outcome.declaration.valueSchema).toEqual({
      type: "object",
      properties: {
        summary: { type: "string", title: "Summary" },
        completed: { type: "boolean", title: "Completed" },
        status: { type: "string", title: "Status", enum: ["complete", "partial"] },
      },
      required: ["summary", "completed"],
      additionalProperties: false,
    });
    const attachment = outcomeAttachments(projection.source, outcome.key)[0];
    expect(attachment.ruleId).toBe("finish");
    expect(attachment.envelope.config.outputSchema).toEqual(outcome.declaration.valueSchema);
    expect((attachment.envelope.config.extractionPrompt as { sections: Array<{ kind: string }> }).sections.map((section) => section.kind))
      .toEqual(["outcome-instruction", "outcome-structure", "outcome-rules"]);

    projection = replaceGuidedOutcomeFields(projection, outcome.storageIndex, [
      defaultOutcomeField(),
      { key: "score", label: "Score", type: "integer", required: false, enumValues: [] },
    ]);
    expect(outcomeAttachments(projection.source, outcome.key)[0].envelope.config.outputSchema)
      .toEqual(projection.source.storage[outcome.storageIndex].valueSchema);
  });

  it("keeps imported SMART extraction custom until an explicit previewed conversion is applied", () => {
    const source = bundledDefinition("usecases.healthcare.smart_goal_coaching");
    const before = serializedDefinition(source);
    const projection = projectDefinition(source);
    const outcome = projection.outcomes.items[0];
    expect(outcomeMode(projection.source, outcome)).toBe("custom");

    const fields: GuidedField[] = [
      { key: "goal", label: "SMART goal", type: "string", required: true, enumValues: [] },
      { key: "confirmed", label: "Confirmed", type: "boolean", required: true, enumValues: [] },
    ];
    const preview = outcomeConversionPreview(projection, outcome.storageIndex, fields);
    expect(preview?.changedRuleIds).toHaveLength(2);
    expect((preview?.after.valueSchema as Record<string, unknown>).additionalProperties).toBe(false);
    expect(serializedDefinition(projection.source)).toBe(before);

    const converted = convertOutcomeToGuided(projection, outcome.storageIndex, fields);
    expect(outcomeMode(converted.source, converted.outcomes.items[0])).toBe("guided");
    expect(outcomeAttachments(converted.source, outcome.key)).toHaveLength(2);
    expect(source).toEqual(bundledDefinition("usecases.healthcare.smart_goal_coaching"));
  });

  it("protects referenced values from rename and delete until their bindings are detached", () => {
    const definition = createDefaultDefinition();
    definition.states.push({ id: "done", name: "Done", kind: "final" });
    definition.transitions.push({
      id: "finish", sourceStateId: "main", targetStateId: "done", order: 10, decisions: [], actions: [],
    });
    let projection = addGuidedOutcome(projectDefinition(definition), "outcome");
    const item = projection.outcomes.items[0];
    expect(dataReferences(projection.source, item.key)).toHaveLength(1);
    expect(renameDataItem(projection, item.storageIndex, "renamed").source).toEqual(projection.source);
    expect(deleteDataItem(projection, item.storageIndex).source).toEqual(projection.source);

    projection = detachOutcomeExtraction(projection, item.key);
    projection = renameDataItem(projection, item.storageIndex, "renamed");
    expect(projection.source.storage[item.storageIndex].key).toBe("renamed");
    projection = deleteDataItem(projection, item.storageIndex);
    expect(projection.source.storage).toHaveLength(0);
  });

  it("replaces a typed-choice setup with a fixed value without leaving an orphaned resource", () => {
    let projection = projectDefinition(bundledDefinition("usecases.healthcare.therapy_appointment_reminder_intro"));
    const item = projection.data.items.find((candidate) => candidate.key === "therapyAppointmentContext")!;
    const first = typedChoiceSetup(projection.source, item.key)!.values[0];
    projection = setFixedInitialValue(projection, item.storageIndex, first);

    expect(projection.source.lifecycle.initializers).toHaveLength(0);
    expect(projection.source.resources).toHaveLength(0);
    expect(projection.source.storage[item.storageIndex].initialValue).toEqual(first);
  });
});

function bundledDefinitions(): AgentDefinitionV1[] {
  const manifest = JSON.parse(readFileSync(resolve(CATALOG_ROOT, "manifest.json"), "utf8")) as {
    entries: Array<{ resource: string }>;
  };
  return manifest.entries.map((entry) => JSON.parse(
    readFileSync(resolve(CATALOG_ROOT, entry.resource), "utf8"),
  ) as AgentDefinitionV1);
}

function bundledDefinition(key: string): AgentDefinitionV1 {
  const definition = bundledDefinitions().find((candidate) => candidate.key === key);
  if (!definition) throw new Error(`Missing bundled definition ${key}`);
  return definition;
}
