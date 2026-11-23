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
  metric depth. `robot_local` and `virtual_scene`: meters, x right/y up/z toward
  the viewer in the declared robot/scene frame. Frame ID/revision must match the
  registered transform. Preview mirroring affects display only. "My left" requires
  an explicit subject-frame mapping; screen-left alone cannot establish it.
- Directions are eight plane sectors; forward/back need a later calibrated
  extension. Targets bind ID/revision/frame, not free-text names. Extents are
  nonnegative. Runtime rejects expired or mismatched registry entries and never
  turns ambiguous/unresolved evidence into a selected person or object.
- Relations: normalized tip distance (hand scale, 0–2), spread (0–1) and axis
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
