# TDSR Demonstrator Agents Roadmap

## Purpose

This document defines the planned GIGI TDSR demonstrator agents for PROMETHEUS.
The goal is to showcase framework capabilities that go beyond ordinary
turn-based chat while preserving the existing PROMETHEUS architecture:

- event-driven interaction
- explicit state-machine task control
- persisted event history
- multimodal `BehaviourPlan` output
- deterministic scripted replay tests
- web-client-only demonstration paths before robot integration

All new demo seed agents should live in:

`src/test/java/ch/zhaw/prometheus/agents/gigitdsr`

All agents use the persona GIGI and speak German. Existing seed agents in
`ch.zhaw.prometheus.agents` and `ch.zhaw.prometheus.agents.gigielderlycare`
must remain unchanged unless a later milestone explicitly scopes shared
helpers or prompt extraction.

## Locked Decisions

- Create new demo seed agents instead of modifying existing ones.
- Persist computed social situation changes as real events.
- Use default social thresholds:
  - crowd: `humanCount >= 3`
  - arrival: count moves from `0` to `>0`
  - departure/goodbye: count moves from `>0` to `0`
  - single nearby person: `humanCount == 1`
  - no one nearby: `humanCount == 0`
- Add cooldown/rate limiting for spontaneous social utterances so repeated
  camera observations do not produce repeated greetings.
- For rock-scissor-paper, start with browser/client-side hand-sign detection
  plus manual fallback.
- Encode robot-like hand/arm output in the existing `motion` modality, not a
  new top-level behaviour modality.
- Keep robot integration out of the first implementation. Web clients are the
  acceptance target.

## Agent 1: Social Context Sensitivity

### Demonstrated Capability

This agent demonstrates that PROMETHEUS agents can initiate behaviour from
visual/social perception events, not only from user utterances.

The existing social visual client at
`src/main/resources/public/visual/social` detects people in the camera view and
emits raw observations:

- `obs.human.presence`
- `obs.social.grouping`

The planned agent should react when people enter or leave GIGI's vicinity and
when the social configuration changes in a meaningful way.

### Proposed Seed Agent

Proposed class:

`src/test/java/ch/zhaw/prometheus/agents/gigitdsr/SocialContextSensitivity.java`

The agent should support two concurrent interaction modes through explicit
state-machine design:

- ordinary German conversation with the user
- proactive short German social utterances triggered by computed social events

The interaction ends only when the user clearly asks to end it. On exit the
agent transitions to a final state and stores a compact interaction summary.

### Raw Events

The current social client emits raw JSON payloads like:

```json
{
  "source": "visual.social",
  "humanCount": 1,
  "trackedCount": 1,
  "trackedIds": [4],
  "avgDetectionConfidence": 0.83,
  "ts": "2026-06-10T18:30:00.000Z"
}
```

and:

```json
{
  "source": "visual.social",
  "humanCount": 3,
  "groupCount": 1,
  "singletonCount": 0,
  "largestGroupSize": 3,
  "groupSizes": [3],
  "groups": [{"memberIds": [4, 5, 6]}],
  "ts": "2026-06-10T18:30:00.000Z"
}
```

These raw events are useful for history and prompt context, but the agent
should not have to infer every arrival/departure from raw count changes inside
an LLM prompt.

### Proposed Computed Event

Add a computed event type:

`obs.social.situation_change`

Proposed payload:

```json
{
  "source": "prometheus.social_situation_change",
  "changeType": "arrival|departure|crowd_detected|now_alone|single_person_nearby|group_size_changed",
  "previousHumanCount": 0,
  "currentHumanCount": 1,
  "previousLargestGroupSize": 0,
  "currentLargestGroupSize": 1,
  "previousGroupCount": 0,
  "currentGroupCount": 0,
  "confidence": 0.83,
  "reason": "human count increased from 0 to 1",
  "sourceEventTypes": ["obs.human.presence", "obs.social.grouping"],
  "ts": "2026-06-10T18:30:00.000Z"
}
```

Recommended `changeType` semantics:

- `arrival`: `previousHumanCount == 0 && currentHumanCount > 0`
- `departure`: `previousHumanCount > 0 && currentHumanCount == 0`
- `crowd_detected`: `currentHumanCount >= 3` and previous state was not crowd
- `now_alone`: `currentHumanCount == 0` and cooldown allows a loneliness remark
- `single_person_nearby`: `currentHumanCount == 1` and previous state was not
  exactly one person
- `group_size_changed`: group/largest-group changes without arrival/departure

The first implementation should keep this as a deterministic computation over
recent social observations. LLM interpretation should not be required to decide
whether an arrival or departure happened.

### Expected Behaviour

Example German utterances:

- arrival: `Hallo, ich habe dich bemerkt. Schoen, dass du da bist.`
- departure: `Tschuess, bis bald.`
- crowd: `Hallo zusammen. Mit so vielen Menschen habe ich gerade nicht gerechnet.`
- no one nearby: `Gerade ist niemand in meiner Naehe. Da fuehle ich mich fast ein bisschen allein.`
- single person: `Du bist gerade allein hier. Wenn du moechtest, leiste ich dir gern etwas Gesellschaft.`

The exact wording should be prompt-controlled and varied, but each utterance
must remain short, friendly, non-intrusive, and German.

### Architecture Notes

The computed event should be produced by a deterministic component before the
agent is expected to react. A likely implementation shape is:

- a pure social situation change detector that accepts recent social facts or
  previous/current raw social payloads
- application/runtime integration that persists the computed event and feeds it
  through the normal `Agent.acknowledge(...)` path
- seed-agent transitions that react to `obs.social.situation_change`, while
  ordinary user utterances remain available for conversation and final exit

The design must avoid hidden coupling between the visual client and one seed
agent. Computed events should be reusable by future agents.

### Tests

Minimum tests:

- unit test for social situation change detection:
  - no person to one person -> `arrival`
  - one person to zero people -> `departure`
  - one/two people to three people -> `crowd_detected`
  - repeated same count within cooldown -> no duplicate computed event
- unit test that malformed or partial raw social payloads fail safely without
  producing misleading computed events
- integration replay test:
  - seed the social context agent
  - acknowledge raw social observations
  - verify computed `obs.social.situation_change` events are persisted
  - verify behaviour SSE emits the expected German social reaction
  - verify normal user utterance still produces conversational behaviour
  - verify explicit user exit transitions to final state
- README/PROJECT documentation update for the new seed agent and event type

## Agent 2: Talking With Gestures

### Demonstrated Capability

This agent demonstrates multimodal behaviour output: GIGI speaks German and
occasionally accompanies utterances with appropriate gestures.

It should be based on the GIGI elderly-care guessing game in:

`src/test/java/ch/zhaw/prometheus/agents/gigielderlycare/SingleStateGuessingGame.java`

but implemented as a new seed agent under `gigitdsr`, not by modifying the
existing elderly-care seed.

### Proposed Seed Agent

Proposed class:

`src/test/java/ch/zhaw/prometheus/agents/gigitdsr/GuessingGameWithGestures.java`

The game variant remains:

- GIGI asks yes/no questions.
- The user thinks of an object, place, animal, or memory.
- GIGI makes a final guess.
- The interaction ends only when the user clearly wants to stop.

### Behaviour Output

Use the existing `BehaviourPlan` shape:

```json
{
  "speech": "Ich frage vorsichtig: Ist es etwas, das man drinnen findet?",
  "nonVerbal": {
    "gesture": "EXPLAIN",
    "facialExpression": "curious",
    "gaze": "towards_user",
    "posture": "open",
    "prosody": "warm"
  },
  "motion": {
    "stillness": 0.2,
    "energy": 0.35
  },
  "display": null
}
```

The first version should configure `PromptPolicy#setNonVerbalPlanPrompt(...)`
with a compact deterministic mapping:

- greeting/invitation -> small wave/open posture
- asking a yes/no question -> explanatory hand motion or attentive stillness
- acknowledging user answer -> nod/acknowledge gesture
- playful self-correction -> small shrug
- final guess -> focused presentation gesture
- goodbye/final -> farewell gesture

The existing nonverbal renderer can visualize at least the gesture field. Richer
fields can be stored and replayed even if not every channel is rendered yet.

### Tests

Minimum tests:

- prompt contract test:
  - agent persists a nonverbal plan prompt
  - agent remains German/GIGI-persona scoped
  - final transition and outcome extraction remain present
- prompt policy/unit coverage if new normalization rules are needed
- scripted REST+SSE replay:
  - start emits German speech
  - at least one generated behaviour contains `nonVerbal.gesture`
  - game can proceed through yes/no turns
  - explicit user exit reaches final state
  - final outcome is stored
- README/PROJECT documentation update for the new seed agent

## Agent 3: Schere, Stein, Papier

### Demonstrated Capability

This agent demonstrates a tightly coordinated multimodal loop:

- GIGI speaks the game phrase `Schere, Stein, Papier`
- at the same moment GIGI emits a hand/arm sign through the `motion` modality
- a browser sensing client detects or accepts the user's hand sign
- PROMETHEUS computes the winner deterministically
- GIGI reports the result and asks whether to play again

The first implementation is web-client-only. Later robot integration can map
the same `motion` payload to a Unitree G1 controller.

### Proposed Seed Agent

Proposed class:

`src/test/java/ch/zhaw/prometheus/agents/gigitdsr/RockScissorPaper.java`

German user-facing name:

`GIGI TDSR - Schere, Stein, Papier`

### Proposed Behaviour Client

Proposed static client:

`src/main/resources/public/rps`

Possible endpoint:

`http://localhost:8080/rps/?agentId=<uuid>`

The client should subscribe to behaviour SSE and render `motion` payloads that
describe GIGI's arm/hand sign. The first renderer can be symbolic rather than
robotic: a clear UI showing GIGI's selected sign, round state, and latest result.

### Proposed Sensing Client

The same web client can include user input for the first milestone:

- manual buttons: `Schere`, `Stein`, `Papier`
- optional camera detection if reliable enough during implementation

Recommended event emitted to PROMETHEUS:

`obs.hand.sign`

Payload:

```json
{
  "source": "rps.web",
  "hand": "right|left|unknown",
  "sign": "rock|scissor|paper",
  "confidence": 1.0,
  "detectionMode": "manual|client_camera",
  "ts": "2026-06-10T18:30:00.000Z"
}
```

The game logic should consume only this normalized event. That keeps manual
fallback, browser-side detection, future server-side image interpretation, and
future robot perception interchangeable.

### Proposed Motion Payload

Use existing `BehaviourPlan.motion`:

```json
{
  "effector": "right_hand",
  "armPose": "present_forward",
  "handSign": "rock|scissor|paper",
  "timing": {
    "synchronizeWithSpeech": "Schere, Stein, Papier",
    "revealAt": "phrase_end"
  },
  "confidence": 1.0
}
```

Optional `display` payload:

```json
{
  "mode": "game_status",
  "title": "Schere, Stein, Papier",
  "agentSign": "rock",
  "round": 3
}
```

### Game Loop

The explicit state machine should avoid relying on the LLM for winner
calculation.

Suggested states:

- `Spielstart`
  - GIGI explains the game shortly in German.
- `RundeVorbereiten`
  - asks whether the user is ready.
- `ZeichenZeigen`
  - emits speech `Schere, Stein, Papier`
  - emits deterministic or seeded-random `motion.handSign`
  - stores GIGI's selected sign.
- `NutzerzeichenAuswerten`
  - waits for `obs.hand.sign`
  - computes winner deterministically.
- `Rundenergebnis`
  - GIGI reports winner and asks whether to play again.
- `Abschluss`
  - final state when the user stops.

Randomness must be controlled. The first implementation should use an explicit
seeded policy or deterministic cycle (`rock`, `scissor`, `paper`) so replay
tests are stable.

### Winner Logic

Canonical normalized signs:

- `rock`
- `scissor`
- `paper`

Rules:

- same sign -> draw
- rock beats scissor
- scissor beats paper
- paper beats rock

The result should be stored as structured JSON, for example under `rps_outcome`
or `rounds`.

### Tests

Minimum tests:

- unit test for winner calculation:
  - all win/loss/draw combinations
  - invalid sign handling
- unit test for deterministic agent sign selection:
  - fixed seed or deterministic cycle produces stable signs
- controller/static redirect test for the RPS client route
- client payload test where practical:
  - manual button emits `obs.hand.sign`
  - behaviour SSE `motion.handSign` renders in the UI
- scripted REST+SSE replay:
  - start game
  - generate or transition into `Schere, Stein, Papier`
  - verify behaviour payload contains speech and `motion.handSign`
  - acknowledge user `obs.hand.sign`
  - verify deterministic winner result
  - play again once
  - exit to final state
- README/PROJECT documentation update for the new seed agent, client, event
  type, and motion payload contract

## Roadmap

### TDSR Milestone 0: Planning Document

Goal:

Create this roadmap and record the architectural decisions before coding.

Deliverables:

- `.agents/TDSR.md`
- `PROJECT.md` milestone entry

Verification:

- documentation review
- no runtime tests required

### TDSR Milestone 1: Talking With Gestures Seed Agent

Status:

Implemented in PROJECT Milestone 23.

Goal:

Create the lowest-risk demonstrator first by adding a GIGI German guessing game
that emits speech plus occasional nonverbal gestures.

Deliverables:

- new `gigitdsr` package: `src/test/java/ch/zhaw/prometheus/agents/gigitdsr`
- `GuessingGameWithGestures` seed agent
- prompt contract test: `GigiTdsrPromptContractTest`
- scripted REST+SSE replay with nonverbal assertion:
  `GigiTdsrGuessingGameWithGesturesReplayIntegrationTest`
- replay fixture:
  `src/test/resources/scripts/gigi-tdsr-guessing-game-with-gestures-replay-script.json`
- README and PROJECT updates

Primary risks:

- LLM outputs may omit gestures unless prompt contract is strict.
- Existing nonverbal renderer may render only a subset of richer nonverbal
  fields.

### TDSR Milestone 2: Social Situation Change Events

Status:

Implemented in PROJECT Milestone 24.

Goal:

Add deterministic computed social situation events from raw visual social
observations.

Deliverables:

- event constant for `obs.social.situation_change`
- pure social situation change detector:
  `src/main/java/ch/zhaw/prometheus/model/social/SocialSituationChangeDetector.java`
- application/runtime integration that persists computed events from
  `obs.social.grouping` via the normal acknowledge path
- unit tests for arrival, departure, crowd, duplicate suppression, malformed
  payloads, initial alone/single-person states, group-size changes, and
  confidence forwarding
- service tests proving computed events are recorded for social grouping input
  and not recorded for unrelated user utterances
- README and PROJECT updates

Primary risks:

- Avoid emitting duplicate computed events from noisy camera updates.
- Avoid making the detector specific to one demo seed agent.

### TDSR Milestone 3: Social Context Sensitivity Seed Agent

Status:

Implemented in PROJECT Milestone 25.

Goal:

Create the German GIGI social context agent that reacts to computed social
events and still supports ordinary conversation.

Deliverables:

- `SocialContextSensitivity` seed agent:
  `src/test/java/ch/zhaw/prometheus/agents/gigitdsr/SocialContextSensitivity.java`
- reusable deterministic latest-event-type transition decision:
  `src/main/java/ch/zhaw/prometheus/model/commons/decisions/LatestEventTypeDecision.java`
- readable prompt adapter for computed social events:
  `src/main/java/ch/zhaw/prometheus/model/policy/SocialSituationChangePromptEventContentAdapter.java`
- replay script covering no-one, arrival, crowd, departure, conversation, and exit:
  `src/test/resources/scripts/gigi-tdsr-social-context-sensitivity-replay-script.json`
- deterministic final-transition guard so only user utterances can trigger the
  prompt-based stop-intent decision
- prompt contract, decision, adapter, and REST+SSE replay tests
- README and PROJECT updates

Primary risks:

- State design must allow proactive social reactions without trapping the
  agent away from normal conversation.
- Cooldowns must prevent repeated spontaneous remarks during steady camera
  input.

### TDSR Milestone 4: RPS Core Game and Motion Contract

Status:

Implemented in PROJECT Milestone 26.

Goal:

Implement Schere-Stein-Papier game logic and behaviour output without camera
sensing first.

Deliverables:

- `RockScissorPaper` seed agent:
  `src/test/java/ch/zhaw/prometheus/agents/gigitdsr/RockScissorPaper.java`
- deterministic sign selection helper:
  `src/main/java/ch/zhaw/prometheus/model/rps/DeterministicRpsSignSelector.java`
- winner calculation helper and sign normalization:
  `src/main/java/ch/zhaw/prometheus/model/rps/RpsRules.java`
  `src/main/java/ch/zhaw/prometheus/model/rps/RpsSign.java`
- event constant for `obs.hand.sign`
- deterministic RPS transition actions:
  `RpsSelectAgentSignAction`
  `RpsEvaluateRoundAction`
- deterministic behaviour policies:
  `RpsRevealPolicy` emits speech `Schere, Stein, Papier` plus top-level
  `motion.handSign`
  `RpsResultPolicy` reports the deterministic winner
- scripted replay using manually supplied `obs.hand.sign`:
  `src/test/resources/scripts/gigi-tdsr-rock-scissor-paper-replay-script.json`
- unit tests for sign selection and winner logic
- prompt contract and REST+SSE replay tests
- README and PROJECT updates

Primary risks:

- Current prompt/action abstractions may need a small deterministic action
  helper for winner calculation and storage.
- The behaviour/event order must make the simultaneous speech and sign reveal
  clear in SSE.

### TDSR Milestone 5: RPS Web Behaviour and Manual Sensing Client

Status: Implemented in PROJECT.md Milestone 27.

Goal:

Add a browser-only demo client for RPS that renders GIGI's motion sign and lets
the user submit their sign manually.

Deliverables:

- static client under `src/main/resources/public/rps`
- redirect route for `/rps`
- behaviour SSE rendering for `motion.handSign`
- manual buttons emitting normalized `obs.hand.sign` events
- route/controller tests and static client contract tests
- local HTTP/static smoke check; browser-level interaction check when a browser target is available
- README and PROJECT updates

Primary risks:

- Keep the client focused on game operation rather than adding a general robot
  control abstraction too early.

### TDSR Milestone 6: RPS Client-Side Hand Detection

Status: Implemented in PROJECT.md Milestone 28.

Goal:

Add camera-based hand-sign detection to the RPS client while preserving manual
fallback.

Deliverables:

- browser-side MediaPipe Gesture Recognizer integration
- confidence threshold controls and stability gating
- normalized `obs.hand.sign` emission from camera detection
- manual fallback retained
- local HTTP/static smoke verification; browser/manual camera verification when a browser target and camera are available
- README and PROJECT updates

Primary risks:

- Browser-side hand detection may be unreliable under lighting, camera angle,
  or occlusion.
- If client-side detection is not adequate, a later milestone may add a
  server-side perception provider using image interpretation, but the game
  should still consume the same normalized `obs.hand.sign` event.

### TDSR Milestone 7: Unified GIGI Demo Cockpit

Status: Implemented in PROJECT.md Milestone 29.

Goal:

Add a single browser page for testing and demonstrating the TDSR agents without
opening the separate text, realtime, visual, nonverbal, monitor, and RPS
clients in parallel.

Deliverables:

- static client under `src/main/resources/public/gigi-demo`
- redirect aliases for `/gigi-demo`, `/gigi`, and `/tdsr`
- agent selection from `GET /agent` plus direct `?agentId=<uuid>` support
- text interaction and realtime speech-to-speech controls on the same page
- camera sensing controls for facial emotion, social grouping, and RPS hand
  sign detection
- manual fallback scenario buttons for conversation, social grouping, and RPS
  hand signs
- behaviour visualization for `speech`, `nonVerbal`, `motion`, and `display`
- diagnostics drawer for activity and storage snapshots
- static client contract tests and redirect tests

Primary risks:

- Browser-side perception remains dependent on camera permission, lighting,
  local hardware, and model/CDN availability.
- The cockpit selects existing database agents; it does not yet create seeded
  TDSR demo agents from a one-click registry.

## Cross-Cutting Constraints

- Do not introduce a new top-level behaviour modality for RPS. Use `motion`.
- Keep all demo agents German and GIGI-persona consistent.
- Keep new deterministic logic outside prompts where exact behaviour matters:
  social change detection, RPS winner calculation, and RPS sign selection.
- Use scripted gateway fixtures for deterministic replay coverage.
- Preserve existing seed agents and clients unless a milestone explicitly
  scopes shared reusable helpers.
- Update `README.md` and `PROJECT.md` in each implementation milestone.

## TODOs

### Assessment

We completed the original TDSR roadmap through Milestone 6. The repository is clean and `main` is aligned with `origin/main`.

What is done:

- **Planning**: `.agents/TDSR.md` created and kept updated.
- **Talking With Gestures**: `GuessingGameWithGestures` exists under `gigitdsr`, with German GIGI persona, structured `nonVerbal` output, prompt contract tests, and replay coverage.
- **Social Situation Change Events**: raw `obs.social.grouping` events now produce persisted computed `obs.social.situation_change` events through deterministic backend logic.
- **Social Context Sensitivity Agent**: `SocialContextSensitivity` reacts to computed social events and still supports ordinary conversation and explicit exit.
- **RPS Core Game**: `RockScissorPaper` has deterministic sign selection, winner calculation, `obs.hand.sign`, `motion.handSign`, result display, and replay coverage.
- **RPS Web Client**: `/rps` client renders GIGI’s motion sign, result state, and supports manual hand-sign input.
- **RPS Camera Detection**: `/rps` now includes browser-side MediaPipe detection with manual fallback, confidence threshold, stability gating, and normalized `obs.hand.sign` emission.
- **Unified Demo Client**: `/gigi-demo` combines agent selection, text,
  realtime speech, visual sensing controls, manual scenario inputs, behaviour
  rendering, and diagnostics for the TDSR agents.

What is left or worth doing:

- **Live demo validation**: camera-based sensing still needs real browser/camera testing on the target demo machine. Static/HTTP contracts cover the browser surface, but not camera hardware quality.
- **Tune RPS camera behavior**: threshold, stability frame count, camera angle, lighting, and false positives likely need adjustment after real use.
- **One-click demo seeding**: the cockpit can select existing agents, but does
  not yet seed GIGI TDSR agents from the browser.
- **Social cooldown/rate limiting**: we implemented duplicate suppression based on social state changes, but not a separate wall-clock cooldown policy for spontaneous remarks. If repeated visual transitions are noisy in demos, this is the main backend polish item.
- **Social final summary**: the original social agent description mentioned storing a compact interaction summary on exit; PROJECT does not record this as implemented.
- **Renderer completeness**: the nonverbal renderer may still visualize only part of richer `nonVerbal` plans from the gestures agent.
- **RPS model fidelity**: MediaPipe uses canned gestures, not a custom RPS-trained model. If it is unreliable, next options are a custom model or server-side image interpretation.

Pragmatic next step: run a full end-to-end demo rehearsal with seeded agents and real browser clients, then decide whether the next milestone should be “TDSR Demo Hardening” focused on live usability fixes rather than new architecture.
