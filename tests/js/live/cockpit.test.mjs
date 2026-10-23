import test from "node:test";
import assert from "node:assert/strict";
import { LiveCockpit } from "../../../src/main/resources/public/live/cockpit.js";

test("only a current backend intent can coordinate countdown playback; history, replay and native speech cannot", () => {
  const displays = [];
  const cockpit = Object.assign(Object.create(LiveCockpit.prototype), {
    client: { observeBehaviour: display => displays.push(display) }, history: [], renderHistory() {},
  });
  const display = { mode: "game_countdown", speechCue: { id: "current-cue" } };
  const event = { id: "plan", type: "resp.behaviour_plan", payload: JSON.stringify({ display }), provenance: { origin: "BACKEND_INTENT" } };
  cockpit.event(event); // History/replay callers never authorize playback coordination.
  cockpit.event({ ...event, provenance: undefined }, { coordinatePlayback: true });
  cockpit.event({ ...event, provenance: { origin: "NATIVE" } }, { coordinatePlayback: true });
  assert.deepEqual(displays, []);
  cockpit.event(event, { coordinatePlayback: true });
  assert.deepEqual(displays, [display]);
  assert.equal(cockpit.history.length, 1);
});
