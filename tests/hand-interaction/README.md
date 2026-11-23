# Hand interaction contracts and acceptance

HI-01 freezes the definitions in
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
| Scoped execution evidence | `executionReceipt` v1 | Bound to canonical event, command, owner and epoch |

HI-02 implements `obs.hand.signs` and its `handSign` condition for new Coordination
instances. HI-03 implements the six-sign subset of `motion.handAction` for new
Coordination profiles, direct commands and task output. HI-04 adds the explicit
`hand.poses.v2` profile tag for v2 poses/two-hand conditions, custom output and
shaka/horns/middle_finger. The tag never widens a saved profile implicitly. Other new boundaries
remain specifications until their milestone. Do
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
`manifest.json` maps all nine future Live journeys to milestones and expected
outcomes. `--hand-interaction L-01` through `L-09` currently reject before database
provisioning or provider access. Baseline success is not new-feature acceptance.

The new authorization is at most $50 cumulatively, including failed attempts,
backend inference, native audio, synthesis and grading. Initial allocation is
12 attempts at $4 reserved each ($48), in a separate persistent ledger. L-09
requires two provider sessions within its reservation. Historical exhausted
ledgers remain untouched. Actual model prices and conservative per-session limits
must be verified and enforced before enabling the first paid journey; the
manifest is not itself a billing meter. No paid call is enabled by HI-01.

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

Run `node tests/hand-interaction/scene-detector-smoke.mjs` for hash-pinned public
images and the real pinned model (local ignored artifacts). `scene-corpus.json`
retains class-output expectations and **separate visual semantic findings**:
carton/jar false bottle positives remain a known failure. Software-WebGL timings
exceeded the live freshness window; this offline replay is not real-time device
acceptance. The client withholds inference taking >=1.8 seconds and explains it.
No nearest-target/depth heuristic or automatic label transfer compensates for
missing detections. Tests with controlled SDK output cover the scoped UI flow;
they are not recognition accuracy or paid provider evidence.
