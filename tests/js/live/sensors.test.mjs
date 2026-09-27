import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import vm from "node:vm";

// Execute the cockpit's actual emitters with controlled time and HTTP/DOM boundaries.
const source = fs.readFileSync(new URL("../../../src/main/resources/public/valerian/script.js", import.meta.url), "utf8");
function fixture({ live = true, interval = 2500 } = {}) {
  let now = Date.parse("2026-09-28T00:00:00Z");
  const events = [], failures = new Set();
  const context = vm.createContext({
    Date: class extends Date {
      constructor(...args) { super(...(args.length ? args : [now])); }
      static now() { return now; }
    },
    camera: {}, liveVoice: { ui: { active: live } }, LIVE_SENSOR_REFRESH_MS: 5000,
    enabled: true,
    document: { getElementById(id) { return { checked: context.enabled,
      value: id === "face_confidence_threshold" ? "0.55" : String(interval) }; } },
    currentProfileSupportsObservation: () => true,
    setEmotionEmitStatus() {}, appendLog() {},
    async acknowledgeEvent(event) { events.push(event); return !failures.has(event.type); },
  });
  for (const name of ["maybeEmitEmotion", "emotionPayload", "compressExpressions", "liveSensorRefreshDue",
    "passesSensorEmitInterval", "markSensorEmitted", "maybeEmitSocial", "submitSocialPayloads",
    "socialContextPayload", "socialContextPerson", "socialContextSignature", "normalizeAttentionSignal",
    "normalizeAttentionState", "normalizeMovementState", "asUnitNumber", "clamp", "round", "average"]) {
    const match = new RegExp(`^(?:async )?function ${name}\\(`, "m").exec(source);
    assert.ok(match, name);
    const rest = source.slice(match.index), end = /^}/m.exec(rest);
    vm.runInContext(rest.slice(0, end.index + 1), context);
  }
  context.face = { emotion: "neutral", confidence: .99, valence: 0, arousal: .2, expressions: { neutral: .99 } };
  context.social = { humanCount: 1, groupCount: 0, singletonCount: 1, largestGroupSize: 0, groups: [] };
  context.people = [{ id: 1, score: .99, movementState: "stationary", movementConfidence: .9,
    attention: { state: "attending", confidence: .9, personVisible: true, faceVisible: true } }];
  return { context, events, failures,
    advance(ms) { now += ms; },
    async sense() {
      await vm.runInContext("maybeEmitEmotion(face, 0.99)", context);
      await vm.runInContext("maybeEmitSocial(social, people)", context);
    },
    manual() { return vm.runInContext('submitSocialPayloads(social, people, "manual.social")', context); },
  };
}

test("unchanged Live readings refresh every five seconds with new observation times", async () => {
  const f = fixture(); await f.sense(); assert.equal(f.events.length, 4);
  for (let second = 1; second <= 30; second++) { f.advance(1000); await f.sense(); }
  assert.equal(f.events.length, 28);
  const byType = Map.groupBy(f.events, event => event.type);
  for (const events of byType.values()) {
    const times = events.map(event => Date.parse(JSON.parse(event.payload).ts));
    assert.deepEqual(times.slice(1).map((time, i) => time - times[i]), Array(6).fill(5000));
  }
});

test("text and TTS retain change-only deduplication, including after Live stops", async () => {
  const f = fixture({ live: false }); await f.sense(); f.advance(30000); await f.sense();
  assert.equal(f.events.length, 4);
  f.context.liveVoice.ui.active = true; await f.sense(); assert.equal(f.events.length, 8);
  f.context.liveVoice.ui.active = false; f.advance(30000); await f.sense(); assert.equal(f.events.length, 8);
});

test("failed observations retry after cooldown and only successful types advance their deduplication", async () => {
  const f = fixture({ live: false });
  f.failures.add("obs.emotion.face"); f.failures.add("obs.human.presence");
  await f.sense(); assert.equal(f.events.length, 4);
  f.advance(1000); await f.sense(); assert.equal(f.events.length, 4);
  f.advance(1500); f.failures.clear(); await f.sense();
  assert.deepEqual(f.events.slice(4).map(event => event.type), ["obs.emotion.face", "obs.human.presence"]);
  f.advance(30000); await f.sense(); assert.equal(f.events.length, 6);
});

test("changing social context does not indefinitely postpone stable presence/group refresh", async () => {
  const f = fixture(); await f.sense(); f.advance(2500);
  f.context.people[0].attention.state = "not_attending"; await f.sense();
  f.advance(2500); await f.sense();
  assert.equal(f.events.filter(event => event.type === "obs.human.presence").length, 2);
  assert.equal(f.events.filter(event => event.type === "obs.social.grouping").length, 2);
});

test("disabled emission and below-threshold face do not refresh stale evidence", async () => {
  const f = fixture(); await f.sense(); f.advance(30000);
  f.context.enabled = false; await f.sense(); assert.equal(f.events.length, 4);
  f.context.enabled = true; f.context.face.confidence = .1; await f.sense();
  assert.equal(f.events.filter(event => event.type === "obs.emotion.face").length, 1);
});

test("manual social samples do not heartbeat and slow Live interval is capped for freshness", async () => {
  const f = fixture({ interval: 60000 }); await f.sense(); f.advance(5000); await f.sense();
  assert.equal(f.events.length, 8);
  f.advance(30000); await f.manual(); assert.equal(f.events.length, 8);
});
