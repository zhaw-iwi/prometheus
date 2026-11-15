# Camera condition: implementation and validation

The owner clarified on 15 November 2026 that text-to-text means typed dialogue
**with actual visual perception**. Scenario B uses a **participant-arranged
consenting adult helper**, not synthetic person buttons. This supersedes the earlier
simulation-only assumption. Work remains on `experiment/hri-2027-coordination`.

## Participant experience

The landing page explains the camera, typed dialogue, approximate 20-minute duration,
random assignment, helper requirement, voluntary participation and recorded data.
The invitation's participant information remains the source of institution/contact,
retention and withdrawal details. Human collection remains closed. Allocation is
still balanced and persisted at invitation issuance; revisiting the page does not
redraw a task. Private preparation and assessments remain outside model prompts.

After preparation creates the assigned agent, the participant page connects and
requests browser camera permission. It loads the face, person and hand detectors
and displays their status and local preview. There is no microphone request or
speech output. Permission denial, missing devices and detector failure are visible;
Finish and Withdraw remain available. Hidden tabs stop capture and require an
explicit restart. Finish, withdrawal and navigation release tracks and detectors.
Recognition is an estimate, never proof that a person feels an emotion or that a
room is private. With multiple people, a facial label cannot identify its subject.

The existing task cards and saved agreement remain visible. Initial acceptance
does not finish participation. Reflection offers **Continue to questionnaire**;
the participant confirms the ending reason. Manual finishing remains available
without agreement or after technical failure. Understanding questions are answered
with saved terms available, followed by two UMUX-LITE items, four separate exploratory
ratings and two optional comments. Answers lock before debrief. Keep the planned
revision and interruption probes; missing probes are missing evidence, not success.

## Implementation boundaries

- `materials-v3.json` is the draft camera condition; new instances default to
  `prometheus.study.sensing=camera`. The `simulation` configuration selects v2
  solely for legacy synthetic regression. Every invitation freezes its materials;
  existing v2 attempts keep their simulation UI and semantics. Neither material
  version is ready for human collection.
- `camera-models.js` uses the same detector families and versions as Valerian:
  face-api.js 0.22.2 (TinyFaceDetector and FaceExpressionNet), COCO-SSD 2.2.3
  (lite_mobilenet_v2, TensorFlow.js 1.7.4), and MediaPipe Tasks Vision 0.10.35
  GestureRecognizer. Asset hosts are `cdn.jsdelivr.net`,
  `justadudewhohacks.github.io` and `storage.googleapis.com`. These deliver model
  code/weights; application code does not send them frames. Before study freeze,
  verify availability and archive/hash the exact deployed model assets.
- The detector supports `Thumb_Up`, `Thumb_Down` and `Open_Palm`; unlike Valerian's
  rock/paper/scissors mapping, the study maps these directly to negotiated answers.
  See the [official MediaPipe Gesture Recognizer documentation](https://developers.google.com/edge/mediapipe/solutions/vision/gesture_recognizer).
- `camera.js` processes locally, normally every 350 ms after a completed pass.
  Hand admission needs confidence at least .65 and three stable frames in one
  scope. Two frames **without a hand**, after the relevant scope opens, arm a new
  sign. Low confidence or an unclassified visible hand does not count as release.
  Question/revision change, Stop and restart cannot carry a held answer forward.
- Person count is capped at three; an extra person suppresses disclosure promptly,
  while absence and return require three frames. Detector failure and unavailable
  frames produce unknown evidence, not an observed zero count. Sampling/inference
  delays are not a hard real-time privacy guarantee.
- `/api/study/vision` accepts only bounded derived count/expression/confidence and
  capture time. It emits `obs.study.vision` with actor `study-camera`,
  `simulation:false` and `source:study.browser.camera`. Its frame is stored and
  recorded with the same atomic receipt/agent boundary as other study inputs.
  Default refresh is ten seconds, plus changed counts and a fresh post-acceptance
  scene. Capture time is aligned to the server clock from the page response.
  Late evidence is not replayed as a new observation.
- `/api/study/sign` uses the existing question/run/revision scope and durable
  observation identity; camera input also carries confidence/stability. The v3
  executor rejects simulated sources. Browser provenance is client-reported,
  not cryptographic attestation or independent recognition ground truth.
- Face estimates provide fallible context and can answer perception questions;
  they cannot accept, revise, answer or end an activity. The typed context/sign
  executors retain their authority. Source-time freshness is 15 seconds. A context
  change fences older pending output. Expired evidence cannot support a newly
  committed private response. The contextual UI hides conversation and terms
  while the local view is unknown or does not show exactly one person.
- Visual receipts have their own 400-observation bound and do not consume the
  50-message/sign allowance. The 45-minute interaction bound and access/admission
  guards remain. Metadata records detector versions, sampling thresholds and
  limits. There is no raw-image/audio column, recording endpoint or upload.

## Verification and release gates

Use the isolated local database runner:

```text
node --test tests/js/hri/camera.test.mjs
python tests/gptlive/run_acceptance.py --study --java-tests "Study*Test" --browser
python tests/gptlive/run_acceptance.py --study --study-camera --java-tests StudyCameraIntegrationTest,StudyMaterialsTest --browser
python tests/gptlive/run_acceptance.py --study --study-camera --study-models --java-tests none --browser
python tests/gptlive/run_acceptance.py --study --study-camera --java-tests none --paid-core coordination-text --study-scenario guess
```

The context and signs paid journeys use the corresponding `--study-scenario`.
These are live OpenAI/local-DB tests with **synthetic detector observations**.
Playwright exercises browser media lifecycle, observation delivery, typed execution,
assessment and responsive layout using a generated video stream and controlled
detector results. This is not an accuracy test of the downloaded vision models.
Actual runs, failures and limits belong in RESULTS.md.

OS-13 software verification is complete: local-DB, controlled-media browser,
real-model loading/inference on generated frames and bounded live-provider trials
passed. The actual user-device/pilot and capacity checks below are still pending.

Before participant release, perform a physical-camera rehearsal across the supported
browsers/devices, lighting/positioning, hand-sign confusions and real one–two–one
helper encounters. Separate recognition errors from coordination errors in coding.
Validate permission/download recovery, helper information/consent, accessibility
alternatives, session duration and questionnaires in the 4–6-person pilot.

The OS-12 four-session/two-waves capacity qualification covers v2 simulations only.
The camera condition adds detector CPU and periodic server observation traffic;
it requires its own workload/hosting qualification. Do not advertise the earlier
result as camera capacity. Institutional review, deployed provider/DB quotas,
participant/helper information and materials freeze remain pending.
