import test from "node:test";
import assert from "node:assert/strict";
import { InteractionActivity } from "../../../src/main/resources/public/performance/activity.js";

test("draft acceptance, brief cue acknowledgement and paused tasks share the footer", () => {
  let now = 0; const a = new InteractionActivity({ now: () => now }); a.scope("agent");
  a.taskStatus({ epoch: "epoch", phase: "CONFIGURATION", draft: true });
  assert.equal(a.view().label, "Awaiting your acceptance"); assert.equal(a.view().busy, false);
  a.taskStatus({ epoch: "epoch", phase: "RUNNING" });
  a.accept({ version: 1, agentId: "agent", epoch: "epoch", revision: 1, serverMs: 1000,
    active: [{ operationId: "one", stage: "thinking" }],
    recent: [{ sequence: 1, serverMs: 990, stage: "cue", outcome: "cue_matched", sourceId: "sample", details: { kind: "obs.emotion.face" } }] });
  assert.equal(a.view().label, "Thinking"); assert.match(a.view().detail, /relax your expression/);
  a.accept({ version: 1, agentId: "agent", epoch: "epoch", revision: 2, serverMs: 1100, active: [], recent: [] });
  assert.equal(a.view().label, "Cue accepted");
  now = 4100; a.taskStatus({ epoch: "epoch", phase: "WAITING" }); assert.equal(a.view().label, "Waiting for the condition to change");
  a.taskStatus({ phase: "PAUSED" }); assert.equal(a.view().label, "Task paused");
  a.taskStatus({ phase: "COMPLETED" }); assert.equal(a.view().label, "Task completed");
  a.scope("other"); assert.equal(a.view().label, "Ready");
});

test("overlap, stale progress, quiet cues and playback use one model with lifecycle fences", () => {
  let now = 0; const activity = new InteractionActivity({ now: () => now });
  const generation = activity.scope("agent1");
  const update = { version: 1, agentId: "agent1", epoch: "epoch1", revision: 2,
    active: [{ operationId: "one", stage: "thinking", elapsedMs: 1200 }, { operationId: "two", stage: "persist", elapsedMs: 20 }], recent: [] };
  activity.accept(update); assert.equal(activity.view().label, "Thinking"); assert.equal(activity.view().busy, true);
  activity.accept({ ...update, revision: 3, active: update.active.slice(0, 1) });
  assert.equal(activity.view().label, "Thinking"); // Another completion never clears this request.
  now = 7000; assert.equal(activity.view().label, "Progress unavailable"); assert.equal(activity.view().busy, false);
  activity.accept({ ...update, revision: 4, active: [], cue: { reason: "insufficient_samples", occurrences: 4 } });
  assert.equal(activity.view().label, "Waiting for a stable cue"); assert.equal(activity.view().busy, false);
  activity.set("playback", "speaking"); assert.equal(activity.view().label, "Speaking");
  activity.set("playback", null); assert.equal(activity.view().label, "Waiting for a stable cue");
  activity.scope("agent2"); activity.accept(update); activity.set("late", "thinking", { generation });
  assert.equal(activity.view().label, "Ready");
  activity.scope(null); assert.equal(activity.view().label, "Connect an agent to begin");
});

test("exports bound journals, entries and issue markers and exclude arbitrary fields", () => {
  const activity = new InteractionActivity({ now: () => 50, wall: () => 100 }); activity.scope("agent");
  for (let sequence = 1; sequence <= 1100; sequence++) activity.accept({ version: 1, agentId: "agent", epoch: "epoch", revision: sequence,
    recent: [{ sequence, stage: "inference", transcript: "private", details: { prompt: "private", inputTokens: 4, requestId: "request" } }] });
  for (let i = 0; i < 70; i++) activity.mark("speech_cut_off");
  activity.mark("private");
  const value = activity.snapshot();
  assert.equal(value.journals[0].recent.length, 1024); assert.equal(value.journals[0].retainedDropped, 76);
  assert.equal(value.markers.length, 64); assert.equal(value.droppedMarkers, 6);
  assert.equal(value.markers[0].association, "operator_marker_approximate");
  assert.equal(JSON.stringify(value).includes("private"), false);
  activity.accept({ version: 1, agentId: "agent", epoch: "older", revision: 1, active: [{ stage: "thinking" }] });
  assert.equal(activity.epoch, "epoch");
  for (let i = 0; i < 20; i++) { activity.scope(`agent${i}`); activity.accept({ version: 1, agentId: `agent${i}`, epoch: "epoch", revision: 1 }); }
  assert.equal(activity.snapshot().journals.length, 16); assert.equal(activity.snapshot().droppedJournals, 5);
  activity.clear(); assert.equal(activity.snapshot().journals.length, 0);
});

test("failed outcomes and slow unconfirmed local work never claim healthy processing", () => {
  let now = 0; const activity = new InteractionActivity({ now: () => now }); activity.scope("a");
  activity.set("request", "processing"); now = 31000;
  assert.equal(activity.view().label, "Waiting for confirmation"); assert.equal(activity.view().busy, false);
  activity.set("request", null);
  activity.accept({ version: 1, agentId: "a", epoch: "e", revision: 1, serverMs: 500,
    recent: [{ sequence: 1, serverMs: 490, stage: "operation", outcome: "failed" }] });
  assert.equal(activity.view().tone, "error"); now += 11000; assert.equal(activity.view().label, "Ready");
});
