# Coordination escalation: operator acceptance

**Current scope, 2026-11-12:** the owner approved existing Speech for committed
Coordination replies. ES-08's agreed behavioural scope is complete; the three
synthetic real-provider journeys have separate semantic PASS reviews and final
regression checks. This supersedes historical pending-choice/native-output instructions
below. Read the top of `ESCALATION_RESULTS.md` for current checks and outcomes;
physical trials, capacity and deployment remain separate. No commit or deployment
has been performed in this continuation.

Use a dedicated local/test environment containing the reviewed main revision.
Main-branch verification does not update the separate deployment branch or
existing remote application. Keep
database capacity separate: it is deferred under the owner's November 11 scope
update, and ES-07's short run failed the earlier deployment quota.
The latest synthetic acceptance and retained failures are in
`ESCALATION_RESULTS.md`. The owner-approved output design is implemented and
recorded in `ES08_SPEECH_DELIVERY_DECISION.md`: Live input plus the existing
Speech service for committed replies. Do not certify a physical trial from
synthetic runs.
Do not use a six-user class as a load test before provisioning/optimization and
a representative sustained 60-70 minute acceptance run.

## Before a physical trial

1. Use a dedicated scoped test code and retain the application revision, browser,
   device choices, local/server time and sanitized incident evidence. Do not copy
   student conversation into public issues. Saved graphs/history are not migrated
   by these changes; use a fresh Coordination instance for an unambiguous trial.
2. Check camera permission and hand/social acknowledgement indicators; a browser
   detection is not proof of backend receipt. Select and test microphone/speaker
   devices, avoid feedback, and make sure only one tab owns audio capture/output.
3. Choose either GPT-Live input with committed Speech output (Alloy), or ordinary
   text plus output-only Speech. Synthetic
   acceptance does not certify physical audibility, visual recognition accuracy,
   pedagogical quality or room acoustics. Record each separately.

## Ordinary, challenging and after-change trials

| Trial | Operator action | Required observation |
| --- | --- | --- |
| Preparation | Agree one fresh-rock, one-joke task with a recognition check. Try Start before the check; ask what went wrong. | Specific preparation guidance; preserved proposal, no action. |
| Acceptance | Demonstrate rock, wait for the invitation, say "Let's start." | Active task, zero actions until a fresh matching cue. Arming is not delivery. |
| Active questions | Ask for help and ask which fresh nonmatching sign is visible. | Grounded explanation; no invented perception or budget change. Help alone does not pause independent reactions. |
| Delivery | Supply one fresh matching cue. | One contribution, audible on the selected speaker; association and playback confirmation lead to Reflection. No repeated joke after an acknowledgement. |
| Dialogue | Agree a two-contribution riddle dialogue: question, then feedback after your answer. Ask for help between them. | No answer leak or extra contribution from help. Feedback fits the actual answer. |
| Unfinished answer | Say "My answer is", then wait before explicitly completing it. Compare the admitted transcript with what you said. | Requests completion without guessing or spending; completion is a new deliberate user turn. Missing provider words remain a separate input-reliability observation. |
| After change | Propose a revised riddle, review it, then accept. | No execution before renewed acceptance; fresh run and bounded budget, old outcome/history retained. |
| Social meaning | Ask for "another person enters" while somebody is present; review an explicit visible-count alternative. | No claim that first arrival means another person's entry, or that camera count identifies a person. |
| Pause/resume | Say "Pause the task, I need help", supply a matching cue, then explicitly Resume and supply a fresh cue. | Paused cue spends nothing; resumed run preserves agreement/progress and executes only an eligible fresh cue. |
| Unavailable service | In a controlled local/test environment, interrupt transport during a request, then restore it and use Check connection. | Last-known status and uncertain request are visible; read-only refresh, no automatic task replay. Review history before repeating anything. |
| Return | Return to Conversation, ask the current state. | Actual state agrees with speech; prior outcomes and history remain available. |

## Teardown and evidence

If synthesis or release fails, confirm that no native fallback reaches the
selected speaker or appears as delivered conversation. A response is synthesized
at most once; a lost release/playback acknowledgement cannot authorize replay.
The original response and playback deadlines remain. Inspect SYNTHESIZED
provenance and observed playback separately. A caption, generated PCM or successful
release is not proof of complete audible playback.

Explicitly Stop the task when ending an active trial, verify backend status, then
stop Live/transcription/sensing and disconnect. Local capture teardown during an
outage cannot confirm the backend task stopped. Preserve scoped history, relevant
timestamps, safe rejection category, response/delivery identity and sanitized
logs before any reset/deletion. Never repeat an uncertain spent contribution
automatically. Reset/deletion is deliberate cleanup, not a diagnostic substitute.

Record PASS/FAIL/NOT RUN for backend control, spoken truthfulness, physical
capture, physical output and sustained capacity independently. For a failure,
retain its evidence and the reason for any rerun; a later pass does not erase it.
External log drains, research archives, paid add-on upgrades and deployment remain
separate owner rollout decisions.
