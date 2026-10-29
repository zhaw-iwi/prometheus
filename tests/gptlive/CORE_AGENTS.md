# Core-agent operator checks

Use new instances: saved graphs deliberately retain their previous policies. Run
against a configured local app first; keep provider billing bounded and record the
commit, instance, voice, browser, microphone/speaker and robot setup. Automated
evidence and known failures are in
[COREAGENTIMPROVEMENT_RESULTS.md](../../.agents/COREAGENTIMPROVEMENT_RESULTS.md).
Use English for these built-in core/healthcare definitions.

## Short journeys

1. **Live Multimodal / Generic / Social Space:** have a short conversation, ask
   about the current camera view, then explicitly request a supported hand sign.
   Decorative gestures should be occasional and varied (12-second spacing,
   60-second same-label suppression); an explicit gesture or agreed task action
   remains exempt. Check actual robot execution separately from displayed plans.
2. **Generic and Social Space task:** ask for a bounded reaction, for example
   “When I show rock, show paper, twice, then stop.” Clarify any open question,
   accept the confirmed proposal with “yes”, and submit fresh matching signs.
   Check the stored agreement, action count and completion. “Okay” must not
   restart it; a new action needs explicit authorization. During an active task,
   a person entering the camera view must not replace the task with small talk.
   Stop Live: a session-bound task pauses and needs explicit resumption. Also
   exercise Text/Continuous on a separate instance to check legacy speech.
3. **Face / Social Context / Social Space sensing:** maintain a stable reading,
   refresh it, briefly fluctuate it, then make a sustained change. Repeated fresh
   copies alone should not cause repeated comments. Say “That expression reading
   is wrong; don't comment on it” and continue talking: conversation/correction
   takes priority. Ask explicitly for sensing again when resuming the demo.
   Social Space waits for two-second stability and has a 15-second comment
   cooldown; sensing demos use a shorter one-second/eight-second cadence.
4. **Rock Scissor Paper:** accept the opening, hear the countdown, then show a
   stable sign after the hand gesture/capture opens. Check the displayed result
   and spoken winner. Agree to replay and repeat with a different sign; the round
   and capture IDs must change. An old-round or pre-opening observation cannot
   resolve the new round. If preparation expires, the result/replay state should
   offer recovery without inventing a winner. After a Live result, stop Live and
   ask for the result in Text: it must still be available.
5. **Role Clarification Guessing Game, human guesses:** agree that the agent
   chooses and you ask. Ask a supported yes/no question, an unspecified detail
   such as colour, then “Is it bigger?” without a comparison. Expect an answer,
   honest missing knowledge, and a request for a comparison respectively. Give
   up: the selected item must be revealed. Start another round: it must use a
   new record/card; guess that item and confirm the solved outcome.
6. **Role Clarification, agent guesses:** choose that role explicitly, think of
   an item and answer its questions. Ask it to guess after supplying a useful
   clue; confirm a correct guess. Replay with the same roles and a new item.
   The agent must not claim it selected or already knows your unspoken secret.
   Repeat journey 5 with **healthcare User Is Guessing**, retaining its welcoming
   and closing style. “Yes”, “no” and harmless acknowledgements must not silently
   leave an active game.
7. **Optional Social Space reception:** enable
   `prometheus.core.social-space.reception=true` before creating a new instance.
   Arm reception with an empty view, enter, disappear for less than two seconds,
   then return: keep the same session and audio. A sustained departure closes it;
   a new arrival starts fresh Live dialogue while the recorded conversation and
   task agreements remain durable. Verify Stop prevents automatic restart.
   Revoking the scoped access code must stop access immediately, without grace.

## Coordination refinement

For **Coordination**, create a fresh `core.coordination` instance after deployment.
Existing saved graphs and prompts are preserved and do not gain these transitions.

1. Ask for one joke after a rock sign, with rock recognition checked first. Supply
   meaningful missing terms, then demonstrate rock. The footer should say
   **Awaiting your acceptance** and the reply should explain how to accept. More
   preparation signs must not tell the joke. Say "Start the saved proposal now",
   then show a new rock. Expect one response, **Finishing** while final speech is
   pending, then **Reflecting on the task** after client playback confirmation.
2. In reflection, say "Make only the response a dry one-liner, keeping every other
   term unchanged." Confirm rock, confidence/sample/cooldown settings, session
   limit and roles are retained. Repeat preparation and acceptance; a fresh rock
   should produce one revised response. Try a short hesitation in "A dry ...
   one-liner"; it should become one update or a clarification, never two guesses.
3. Try one joke plus an uncertain facial-expression estimate on scissors. Return
   with "Return to the initial conversation state", then ask the current state:
   both the reply and graph must say Conversation. Configure another task.
4. Try a one-contribution dialogue task. "Start all over again" should clarify
   return versus restart. "Restart the same agreed task" creates a new run/budget;
   returning to Conversation preserves the earlier transcript and outcomes.
5. Stop or mute during final speech: no automatic replay and no claim of successful
   delivery. A stopped/lost session or expired deadline leaves uncertainty. Check
   actual listening, camera classification and robot movement separately. These
   physical checks cannot be supplied by headless browser acceptance.

Use `tests/gptlive/run_acceptance.py` with a disposable local MySQL administrator
properties file for automated checks. `--paid-core coordination` supports the
combined `--coordination-scenario recognition`, `multimodal` and `dialogue`
journeys; `--paid-core coordination-text` supports `lifecycle` and `multimodal`.
These switches explicitly incur OpenAI charges. Each run has finite request/time
budgets and writes ignored local artifacts, then removes its schema/account and
stops its owned application. The retained failure/verification record is
`.agents/COORDINATION_RESULTS.md`.

## What to capture

Record the unexpected wording and approximate time, export cockpit telemetry and
note the observed state/round. For a missing final word, distinguish the caption
from what was actually audible. Follow
[PLAN_CUTOFFINSPECTION.md](../../.agents/PLAN_CUTOFFINSPECTION.md) for the separate,
unimplemented received-audio diagnostic; these changes do not fix audio cut-offs.

Live can still produce a waiting remark, duplicate question or premature wording
despite instructions. Report these as provider behaviour failures, not as passed
checks merely because the backend stayed correct. Do not repeatedly rerun a
failed paid journey until it happens to pass. Review its captions and persisted
control first. Physical timing, acoustic echo, camera accuracy and separation of
the robot's own hand from the visitor's remain operator checks.

## Integration handoff

The implementation is opt-in at definition construction; create fresh instances
to use it. Retain existing rows and graphs. Apply the additive nullable columns
documented in README before rollout if schema management is external (gesture,
sensory, RPS and guessing policy/action/decision fields). Default Social Space
reception is off; default new RPS Live countdown is on. Talk to Me remains exact
text and is outside these changes.

Coordination refinement additionally widens `storage_entry.entry_value` to
`varchar(8192)`; apply that additive change if automatic schema updates are disabled.
The frontend uses the existing playback-cue endpoint. Captions alone never release
Finishing, and browser acknowledgement does not prove physical audibility.

The inspected standalone Valerian branch `feature/galadinnerupdate` at `398f410`
already consumes `display.captureId`, scoped playback cues and
`presenceControlledLive`/departure grace, including duplicate-opening handling.
No additional client contract is introduced here. This was a code compatibility
review, not a standalone robot/browser acceptance run. Integrate that client
branch separately and repeat the relevant physical journeys after deployment.
