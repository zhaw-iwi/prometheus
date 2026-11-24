# Hand interaction contracts and acceptance

HI-01 establishes the baseline definitions in
[`interaction-v1.schema.json`](../../src/main/resources/hand/interaction-v1.schema.json).
Validate the named `$defs` entry, not the unrestricted bundle root. Examples are
contract fixtures, not claims that the runtime implements these future features.
The roadmap and evidence are in `.agents/PLAN_HAND_INTERACTION.md` and
`.agents/HAND_INTERACTION_RESULTS.md`.

## Wire boundaries

| Boundary | Contract definition | Compatibility |
| --- | --- | --- |
| `obs.hand.signs` | `signObservation` v1 | New opt-in per-hand evidence; legacy `obs.hand.sign` unchanged |
| `obs.hand.pose` | `poseObservation` v2 | New opt-in envelope; existing v1 Generic tasks unchanged |
| `obs.hand.pointing` | `pointObservation` v1 | Direction is separate from target resolution |
| `obs.scene.targets` | `sceneTargets` v1 | Bounded scoped target registry |
| Hand conditions | `signCondition`, `poseCondition`, `pointCondition`, `inputSequence` | Parsed by runtime; never executable model code |
| `BehaviourPlan.motion.handAction` | `motion` v1 | Canonical semantic intent, not actuator coordinates |
| Connected embodiment capabilities | `capabilities` | Input and output must be checked separately |
| Scoped execution requests and evidence | `executionRequest`, `executionReceipt` v1 | HI-10 binds the canonical event to a server command/epoch and exclusive renderer lease |

HI-02 implements `obs.hand.signs` and its `handSign` condition for new Coordination
instances. HI-03 implements the six-sign subset of `motion.handAction` for new
Coordination profiles, direct commands and task output. HI-04 adds the explicit
`hand.poses.v2` profile tag for v2 poses/two-hand conditions, custom output and
shaka/horns/middle_finger. The tag never widens a saved profile implicitly. HI-05..09 implement geometry, pointing, targets and input sequences. HI-10 adds
finite output and scoped receipts. Its request carries eventId/owner/operation/step;
the server supplies command identity, epoch, source position and times. This
refines the previously unimplemented draft receipt instead of trusting client
claims of epoch, completion or observation time. Only ordered, elapsed steps under
one current owner can complete. Other future boundaries remain specifications. Do
not send new envelopes to old agents. Later adoption must explicitly check the
persisted profile and offer recreation/upgrade without silently widening an
accepted agreement or deleting history. Legacy `rock`, `scissor`, `paper` remain
unchanged; their corresponding shapes are `fist`, `peace`, `open_palm` only in
the new vocabulary. `thumbs_up` and `ok_ring` are different shapes. Natural
language "OK" needs shape clarification where context does not settle it.

## Meaning, limits and validation responsibilities

- At most two distinct hands and five named fingers per hand. Hand IDs are
  local capture identities, not person identities. Left/right means subject
  handedness; unknown handedness is `unknown`, never an invented side.
- Bend values are finite 0–1: straight below 0.4, bent above 0.55, neutral
  inclusively between. `null` means unknown. Compared numeric and state evidence
  must both be present/consistent. Unspecified condition fingers are unconstrained;
  an output pose supplies all five values (unspecified requests default to 0.5).
- Confidence is a named classifier score, not finger visibility or bend accuracy.
  Model-inferred landmarks do not prove that every finger is visible.
- A single camera frame shares `observationId`, source, timestamp and frame across
  sign/pose/point events. Runtime admission consumes that evidence at most once
  per contribution, also across equivalent event representations. Preparation
  cannot spend an action; acceptance requires a new, fresh execution cue.
- `camera_image`: normalized, unmirrored camera view, x right/y down; no claimed
  metric depth. `robot_local`: robot-own right/up (front-view right is negative
  renderer x); HI-06 supports plane directions only. `virtual_scene`: declared
  scene x-right/y-up/z-toward-viewer units. Frame ID/revision must match the
  registered transform. Preview mirroring affects display only. "My left" requires
  an explicit subject-frame mapping; screen-left alone cannot establish it.
- Directions are eight plane sectors; forward/back need a later calibrated
  extension. Targets bind ID/revision/frame, not free-text names. Extents are
  nonnegative. Runtime rejects expired or mismatched registry entries and never
  turns ambiguous/unresolved evidence into a selected person or object.
- Relations: normalized tip distance (hand scale, 0–4), spread (0–1) and axis
  orientation (5–45 degree tolerance). Proximity does not establish contact.
  HI-05 specifies the supported geometric combinations and output adapter.
- Input sequences: 1–12 steps, default dwell 300 ms, default gap 5 s, total at
  most 120 s. Output sequences: 1–12 steps, each 100–5000 ms (default 500 ms),
  total at most 60 s, no nesting. Clock and sequence progress belong to runtime
  state. Lost ownership/reconnect cannot automatically replay output.
- Proposed ingress ceilings: 16 KiB per hand envelope, five admitted frames/s,
  64 KiB hand state per agent. Reject oversized input before parsing. JSON
  Schema enforces shape/range/unknown fields; runtime additionally enforces byte
  limits, uniqueness by hand/finger ID, distinct hand assignment, source freshness,
  state/value consistency, sequence time relationships, target scoping and
  capability/ownership authority. These semantic checks arrive with each feature.
- Failure reasons distinguish `invalid_contract`, `unsupported_capability`,
  `stale_observation`, `duplicate_observation`, `ambiguous_target`,
  `unresolved_target`, `frame_mismatch`, `sequence_timeout`, `owner_changed` and
  `execution_unknown`. Follow the existing HTTP error contract; these are bounded
  domain diagnostics, not a new response envelope. A rendered pose or audio
  acknowledgement is not proof of physical robot completion.

## Verification and budget

```powershell
python -m pip install -r tests/hand-interaction/requirements.txt
python -B -m unittest discover -s tests/gptlive -p test_hand_interaction.py
python tests/gptlive/run_acceptance.py --hand-interaction contracts
python tests/gptlive/run_acceptance.py --database-properties <local-admin-properties> --hand-interaction baseline
```

The baseline uses a disposable local MySQL schema/user, existing real-app
Playwright journeys, mocked providers, and the existing query-budget tests.
`manifest.json` maps all nine Live journeys to milestones and expected outcomes.
Paid `L-01` through `L-09` selections require the explicit `--paid-core hand-live`
opt-in and the bounded plan described below. Baseline success is not provider
acceptance and never enables paid calls.

The new authorization is at most $50 cumulatively, including failed attempts,
backend inference, native audio, synthesis and grading. Current allocation 3
retains the first three $4 reservations and allows nineteen later $2 reservations:
22 attempts/$50 maximum in a separate persistent ledger. L-09 uses separate
comparison and Coordination reservations. Historical exhausted ledgers remain
untouched. Verified prices and per-session limits are enforced before provider
access; the manifest is not itself a billing meter. See the results for current
usage and remaining allowance.

## Actual detector smoke

```powershell
node tests/hand-interaction/detector-smoke.mjs
```

This downloads/cache-checks SHA-256 pinned MediaPipe 0.10.35 CPU WASM, its existing
model bundle and public upstream images under ignored `target`. Seven frames per
case exercise actual model inference plus the production stability tracker. The
corpus declares a 0.60 operator threshold, 150 ms frame intervals and a 1000 ms
stable-result deadline; this does not change the product's 0.65 default. Images,
per-frame classifications/timing, the manifest and results are retained in a new
run directory each time. Rotated/mirrored/cropped views are declared pixel
transforms, not additional independent people or private captures. Model/source
digest drift fails before inference. This small corpus is not a real-room accuracy
claim; retained default-threshold and exploratory-view failures are in the results.

HI-04 expanded corpus (18 cases / 126 actual inference frames):

```powershell
node tests/hand-interaction/detector-smoke.mjs --manifest=tests/hand-interaction/composed-corpus.json
```

It adds hash-pinned crops of the public HaGRID gesture overview (Kapitanov et al.),
three derived signs and mirrored views, the I-love-you near miss and partial
one/two-hand masks. Attribution and source/license links are in the manifest;
photos and transformed captures remain under ignored target. This is static-image
inference, not recorded moving hands. The new admission criterion requires the
expected stable result by 1000 ms and forbids any stable wrong result; unstable
warm-up estimates remain in artifacts. Original every-raw-frame and initial
geometry failures are retained in the results log, not relabelled as passing.

Derived named shapes use proximal/distal extension and palm-normalized thumb
opposition. They deliberately differ from mean joint-curvature `fingerStates`:
folding a thumb across the palm can leave its joints nearly straight. Their
confidence is null. The original numeric bend estimator is unchanged, including
its neutral band; use the named rule for a named shape instead of inventing a
low-level numeric equivalence. Synthetic rotation/scale/mirroring and unknown
landmark tests protect geometry; corpus results establish only these examples.

HI-05 geometry corpus: `node tests/hand-interaction/detector-smoke.mjs
--manifest=tests/hand-interaction/geometry-corpus.json` adds ring and reflected ring
to the 18 existing cases. Its declared photographed near misses are thumbs up,
open palm, index up and I-love-you. Controlled geometric tests cover opened-ring
thresholds and unknown landmarks. These remain static-image model trials; no
moving-user or physical execution accuracy is implied.

HI-06 completes the previously unimplemented pointing-v1 envelope with required
handedness, validity and aspect-corrected ray evidence. The initial emitter only
publishes unresolved target selection. New instances explicitly opt in to
`hand.pointing.v1`; frame revision binds proposals and cues. Run the pinned actual
model direction corpus with `node tests/hand-interaction/detector-smoke.mjs
--manifest=tests/hand-interaction/pointing-corpus.json`. Its translated/rotated
photo case exercises pixel motion, not recorded-user tracking accuracy.

## Configured scene registration (HI-07)

New `hand.targets.v1` instances expose scoped GET/POST
`/demo/agents/{agentId}/hand-scene`, authenticated with
`X-Prometheus-Access-Code`. POST validates `$defs.sceneConfiguration` and semantic
bounds before one serialized Storage write. The request replaces the configuration:

```json
{"schemaVersion":1,"expectedRevision":0,
 "cameraFrame":{"id":"current-camera","revision":2,"space":"camera_image","mirrored":false},
 "transform":[8,0,-4,0,-3,3],
 "targets":[{"key":"display","label":"Display","type":"region",
   "position":{"x":0.9,"y":0.5,"z":0},"extent":{"x":0.1,"y":0.3,"z":0},"expiresAt":null}]}
```

Copy an actually fresh observed frame; the illustrative ID is not registrable.
Transform `[a,b,c,d,e,f]` maps camera `(x,y)` to scene
`(a*x+b*y+c,d*x+e*y+f,0)`. Coefficients are finite -10..10, determinant magnitude
at least .01, all image corners inside x=-4..4/y=-1..5. A null transform permits
region cues but no region output. Virtual entries use scene positions directly;
no camera is needed for virtual-only setup. Region extents are full dimensions,
strictly positive in x/y, z=0, entirely inside the image. Virtual z is -3..3.
Targets optionally expire within the next day. Maximum sixteen entries/32 KiB.

Responses assign agent-namespaced IDs, monotonic revisions, provenance, observed
and expiry times, and frame identity. All references become obsolete on any
configuration replacement. Stable IDs do not imply stable meaning after edits.
Target conditions are exactly `{target:{id,revision,frame},handedness}`.
Target commands are `{kind:"point",effector:"left_hand",target:{id,revision,frame}}`.
The backend adds `resolved:{label,position,frame}` from current scoped state,
ignoring any supplied snapshot. Missing/expired mappings never dispatch. The
reference arm requires a target at least 1.8 virtual units from its shoulder.

Incoming selection is always recomputed; up to four candidate references explain
ambiguity without asserting an exhaustive list. Pointing-v1's unresolved `reason`
is optional for the already published HI-06 emitter, and validated when present.
The same-capture, source freshness, preparation/acceptance and action-budget gates
remain in force. Native instructions expose concise capability semantics; full
registered scene data is supplied only to backend task/command interpretation.


## Detected people and items (HI-08)

`hand.targets.dynamic.v1` also requires `hand.targets.v1` and
`hand.pointing.v1`, plus declared `obs.scene.targets`. Old profiles remain intact.
The existing COCO-SSD 2.2.3/lite_mobilenet_v2 inference supplies person, cup, bottle
and book boxes (score >= .5), at most sixteen. The client sends snapshots at most
2/s; ingress enforces 16 KiB, source age <=2 seconds (1 second skew), monotonically
increasing captures >=200 ms apart, unique IDs and normalized image bounds.
`sceneTargets` now requires `observationId` and `frame`. No image leaves the client.

Server IDs are agent-namespaced and revisions are monotonic generations. Expiry
is exactly capture+3 seconds. Exact retries cannot append events. Missing tracks,
changed frames and gaps over 1.5 seconds retire references; ambiguous client
assignments/crossings also create fresh IDs. Pointing pairs only with an earlier
same-frame snapshot no older than 1.5 seconds. Optional `handOrigin` is the
estimated wrist in image coordinates. The backend replaces `sourceAssociation`
and `resolution`; exactly one overlapping person box is a projected source to
exclude. Without one unambiguous projected source, dynamic target resolution
stays unresolved (`source_unknown`); direction remains available. Configured-region
selection retains HI-07 semantics. This is not identity or depth.

POST `/demo/agents/{id}/hand-scene/binding` with the normal scoped header and
`{target:{id,revision,frame},label,destination:null|{id,revision,frame}}` selects one
fresh track. Labels are 1..80 characters; at most sixteen bindings are retained.
Only a current registered virtual destination can be an output counterpart.
Bindings/reconfiguration pause active tasks and reset preparation; accepted terms
remain intact. Selected refs and labels never survive a generation change as
valid bindings. Dispatch rechecks both target and counterpart after inference.

Scene capture acknowledgements now include optional `sceneCapture`, a JSON string
containing the validated canonical snapshot. It confirms a durable capture, not
a task transition. One latest snapshot per agent is stored in
`scene_target_capture`, fenced by execution epoch and deleted with its agent.
The agent assimilates the latest fresh snapshot during its next available turn
and after blocking interpretation, before target grounding. Intermediate passive
scene frames coalesce while a turn is busy; all received snapshots still undergo
the same identity, geometry, rate and source-time checks. No timestamp or expiry
is extended. Accepted scene data alone never authorizes motion.

The camera keeps detection running while other sensor acknowledgements wait,
with at most one in-flight emission per other sensor kind. The target selector
uses canonical capture receipts and does not replace a newer capture with an older
monitor update. This supports tracking continuity during model latency, not slow
detector inference; the existing 1.8-second detector cutoff remains.

Run `node tests/hand-interaction/scene-detector-smoke.mjs` for hash-pinned public
images and the real pinned model (local ignored artifacts). `scene-corpus.json`
retains class-output expectations and **separate visual semantic findings**:
carton/jar false bottle positives remain a known failure. Software-WebGL timings
exceeded the live freshness window; this offline replay is not real-time device
acceptance. The client withholds inference taking >=1.8 seconds and explains it.
No nearest-target/depth heuristic or automatic label transfer compensates for
missing detections. Tests with controlled SDK output cover the scoped UI flow;
they are not recognition accuracy or paid provider evidence.

## HI-12 provider acceptance (incomplete)

Acceptance revision 2 (owner decision 2026-11-24) preserves the ownership split:
Text/Continuous use backend speech; GPT-Live generates and streams native speech
independently of backend inference. Strict silent commands/tasks are unsupported
in GPT-Live. Live stimuli ask for the hand action without silence; optional native
speech is allowed when consistent with authoritative task state. False claims,
changed terms, premature actions and incorrect visible output still fail. No
playback gate, classification buffer, task-window muting or replacement TTS is used.

Required M-01 checks silence in Text and Continuous for a custom pose, finite count
sequence and one sensor-triggered hand task: no canonical speech, synthesis or
action audio, including completion, with a spoken Continuous control. Required
M-02 retains native audio while backend inference is held, immediate Stop and
restored Text/Continuous ownership on switching. Use real local MySQL, ingress,
SSE and rendering with labelled provider/media fixtures. These mode checks are
separate from the required L-01..09 real-provider runs and physical trials.

Run the mode matrix with the isolated runner:

```powershell
python -B tests/gptlive/run_acceptance.py --database-properties <loopback-properties> --java-tests 'CoordinationAgentIntegrationTest,LiveQueryBudgetIntegrationTest' --browser --browser-mode enabled --browser-grep 'strict hand silence|native Coordination audio precedes blocked backend' --live-only
```

`valerian-hand-silence.spec.mjs` retains synthesis/playback counters and ordered
count images. The final peace screenshot is captured after release; active sign
geometry is covered by the sign/pose renderer evidence. Continuous includes a
spoken positive control before its silent windows.

Use `tests/gptlive/run_acceptance.py --database-properties <loopback-properties>
--hand-interaction L-01 --paid-core hand-live` with `OPENAI_MODEL=gpt-6.2-sol`.
Selections L-01 through L-09 are explicit individual attempts; the L-09 comparison
session uses `--hand-comparison` first. Run the existing local-database and browser
preflight before spending a reservation. Do not mix paid selections with mocked
browser/Java selections. Keep the existing developer model configuration unchanged.

The enforced plan is `tests/gptlive/hand-paid-plan.json`. Its separate persistent
ledger is `target/hand-interaction-budget.json`; never reset it or refund a failed
attempt. Allocation 15 permits up to four individually selected open journeys and raises the continuation from the 88-attempt/$186 checkpoint to $120.
Each new selection reserves $5 including backend and Live. The cumulative limit is
112 attempts/$306. Restore the latest committed ledger using the
[handoff](../../.agents/HANDOFF_HAND_INTERACTION_2026-11-24.md).
Each attempt permits one six-minute native session, ten backend requests, 80,000
UTF-8 bytes per request, 2,048 completion tokens per call and twelve visitor
turns. The overall watchdog is 390 seconds. Stimulus generation and grading are local.
Generate the labelled visitor audio with `python tests/gptlive/prepare_hand_audio.py`;
regenerate it after this revision, since old silence-request clips fail preflight.
This uses local Windows SAPI, not a provider. Controlled sensor fixtures do not
establish camera accuracy. Every paid run records source hashes and complete
received native audio, including audio suppressed by browser playback.
The full acceptance stimulus says “I accept the current proposal.” If its
admitted transcript is truncated, wait for the inactive-proposal repair, then
provide fresh explicit consent. L-03 can deliberately exercise a fragment with
`--hand-consent-repair`. PCM reaches the browser as lossless
base64 rather than a large array of JSON numbers, avoiding expensive automation
serialization. The harness test verifies a complete 45-second PCM round trip,
browser polling and the unchanged input-audio budget.

Optional independent audio transcription stays local:

```powershell
python -m pip install --no-user --target target/hand-audio-tools faster-whisper==1.2.1
python -B tests/gptlive/review_hand_audio.py --download-model target/gptlive-acceptance-<id>
```

Omit `--download-model` after the public base.en weights are cached. The report
retains package versions, model/audio hashes and all transcript segments. It is
supporting evidence, not an automatic semantic verdict or a physical listening
test. Review full captions, independent transcription, raw audio activity,
ordered renderer images, durable terms/actions and scoped completion evidence.
Revision-1 failed attempts remain failed under their original rubric. Every new
report records revision 2; L-09 requires a comparison from the same revision.
HI-12 is incomplete until L-01..09 and M-01/M-02 pass; see the project results.
The handoff records the current spend reservations. Actual invoice totals are unavailable.

Physical operator checklist (all NOT RUN): ten named signs on each hand; custom
one/two-hand patterns and occlusion; mirrored preview and all pointing directions;
configured targets; ambiguous, moving and disappearing people/items; ordered input
and output counting; Stop during playback; actual microphone/speaker operation;
and the selected physical robot adapter, if any. Record device, lighting, distance,
view, errors and latency. Digital renderer receipts establish no physical actuation.
