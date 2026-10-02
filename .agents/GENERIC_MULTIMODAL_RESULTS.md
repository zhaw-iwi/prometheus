# Generic Multimodal Behaviour

## Scope and implementation

Milestone 202, 2026-10-02, on `feature/gptlive`. The new
`core.generic_multimodal_behaviour` definition has the same declared observations
and output modalities as `core.multimodal_behaviour`. Existing definitions and
saved instances retain their behaviour. The user authorized integration from
`feature/gptlive` to `main`, then `main` to `agents` and Heroku redeployment.
Branch integration and deployment verification are recorded below.

The agent supports capability discovery, goal clarification, a proposed task,
activation, cue-driven execution, revision and cancellation. Standard transitions
and blocking actions own configuration/execution/completion; task state is visible
in existing storage and state monitors. An internal routing state selects the
next phase after a validated action. `TaskState` adds only task-storage reset.

One bounded inference request processes a complete user utterance and returns a
typed operation, optional task specification and canonical behaviour plan.
PROPOSE saves a draft and pauses execution. ACTIVATE installs the complete agreed
task (or existing draft). KEEP answers questions without replacing the task;
STOP ends the task. Common explicit stop/cancel/pause commands work without
provider inference. Malformed configuration does not replace the prior agreement
or publish a model's activation claim. Provider/model quality still determines
whether a natural-language agreement is interpreted correctly.

Task rules are data: up to four allowed observation/field comparisons, bounded
actions, confidence, distinct sample count and cooldown. Matching uses the loaded
event history without further database queries or inference on every sample.
Duplicate timestamps, out-of-order samples, unknown/expired observations and
unmatched cues cannot advance the task. Terminal rules have priority. A task has
a finite action budget, including its initial action. Generation failure pauses
the task for explicit resumption instead of retrying on subsequent camera frames.

Live uses the existing committed narration intent -> commentary path. The new
policy is explicitly supported by the Live adapter; its voice instructions defer
task execution and answers to confirmed backend output. Ordinary Text/Continuous
also consume canonical behaviour plans. The current perception snapshot goes
directly into backend user-turn inference, so questions such as whether one
person is visible do not depend solely on the Live model using quiet updates.

## Storage and schema

`task.phase`, `task.spec`, `task.draft`, `task.actions`, `task.after` and
`task.revision` use existing Storage. `task.reply` is consumed once by state entry.
Specifications and replies are bounded to 1,800 serialized JSON characters to fit
the existing 2,048-character storage column, including Gson escaping.

JPA adds `TaskState`, `TaskPolicy`, `TaskDecision` and `TaskUpdateAction`
discriminators, plus nullable `policy.task_instructions`,
`decision.task_condition` and `action.task_cue` columns under the existing schema
update configuration. No existing rows are backfilled. Coordinate deployment
before creating these instances: older code cannot load the new discriminators.

## Verification

### Activation validation follow-up (Milestone 203, 2026-10-02)

The user's `prometheus-interaction-timing-2026-10-02T19-41-32-490Z.json`, scoped
read-only database inspection and Heroku CLI logs establish that the facial-joke
draft was saved at 19:40:21 UTC. Both subsequent activation turns reached the
backend and their extraction requests succeeded, but validation produced the
generic rejection at 19:40:33 and 19:41:09. The phase remained CONFIGURATION,
revision 1, with the valid draft still stored. No task activation was committed.
The spoken summary was narration of the draft, not confirmation of activation;
delayed Live narration also combined the summary and rejection in one response.

The original invalid model JSON and validation reason were not retained. Two
bounded text-inference replays reconstructed the first activation context using
the deployed prompt, saved draft, recent history and configured `gpt-5.6-luna` /
`none` route. One response validated; the second returned ACTIVATE with a valid
task and reply speech but `nonVerbal.gesture=PLAYFUL_CURIOUS`. The validator
rejected that unsupported gesture and therefore the entire activation. This is a
reproduced failure mechanism, not proof of the exact original invalid field.

Unknown string-valued expressive gesture labels now normalize to `NONE` on a
copy of the reply. Valid speech, facial expression and other output are retained;
recognized gestures, task-rule checks, non-string gesture rejection, intensity
bounds and motion-command validation are unchanged. There is no repair inference
or sensor retry. Other validation failures retain the draft/active task and tell
the user it was kept, rather than asking for the full task again. Rejections log
the correlated request ID, validation stage and fixed reason code without private
model content. No persisted prompt or schema change is required for this fix.

All **13 focused tests passed** with no failures, errors or skips:

```powershell
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests GenericMultimodalTaskUnitTest,GenericMultimodalTaskIntegrationTest
```

Final evidence: `target/gptlive-acceptance-8caae7f257/java.log` and `test-counts.json`.
The real-MySQL case now saves a proposal, reloads it, activates it through Live
with `task=null` and the reproduced invalid gesture, then verifies narration,
sensor-only execution, duplicate suppression, stop and reset. Unit checks protect
known labels, source-object immutability, strict motion rejection, draft retention
and content-free correlated diagnostics. The retained failing provider response
also validates with the patched code: gesture NONE and identical speech.

The test schema/account were removed. Production inspection was read-only, and
the two provider replays did not acknowledge the deployed agent or start a Live
session. No production configuration, data or access-code assignments changed.
No physical camera/voice retest or broader Java/browser rerun was performed for
this focused follow-up. The user has authorized integration into `main`, then
`agents`, with commit/push and Heroku redeployment. Rollout verification is pending.

### Initial implementation

The full Java regression passed 482 tests, with no failures, errors or skips:

```powershell
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests all
```

Evidence is in `target/gptlive-acceptance-0419236990/java.log` and
`target/gptlive-acceptance-0419236990/test-counts.json`. After the final adjustment
to bind shared capability context through the existing prompt assembler, a fresh
focused run passed all 20 tests:

```powershell
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests GenericMultimodalTaskUnitTest,GenericMultimodalTaskIntegrationTest,AgentDefinitionRegistryUnitTest,ValerianCorePromptContractTest
```

The focused log is `target/gptlive-acceptance-17c4beebaa/java.log`. Ten task unit
tests cover negotiation, activation, cue filtering, completion, current perception,
Live speech gating, invalid specifications/output, bounded actions, failure and
reset. One real-MySQL integration case covers scoped creation, entity reload,
durable Live ingress, cue-triggered commentary, duplicate suppression, stop and
reset. Nine registry/persona checks protect catalog and prompt compatibility.

Both runs used disposable schemas/accounts on the configured local test database
and offline providers; the runner removed its owned app, schema and account.
There were no production data changes or paid provider requests. Frontend code
did not change; JavaScript and browser suites were not rerun for this milestone.

## Branch integration and deployment

Implementation commit `7457d1a` was pushed to `feature/gptlive` and fast-forwarded
into `main`. Main was merged into `agents` as `42a29a1`. The merge retained the
deployment catalog and per-agent persona binding in the Live adapter. The new
agent's deterministic welcome also uses the selected Valerian/Gigi persona on
the deployment branch. Persona tests cover its welcome, task inference and Live
instructions while retaining user dialogue verbatim.

The first merged run passed 618 of 619 Java tests; its one failure was the
deployment-only Live catalog expectation omitting the new definition. After
updating that expectation, the fresh full rerun passed **619 tests in 135 suites**,
with no failures, errors or skips. Evidence is in
`target/gptlive-acceptance-9ba0abf3fa/java.log` and `test-counts.json`; the first
run is `target/gptlive-acceptance-e79d270dfd/`. Both used disposable local MySQL
schemas/accounts and simulated providers, and removed their owned resources.
JavaScript/browser suites were not rerun because frontend sources did not change.

The `agents` push triggered [deployment workflow 37031277038](https://github.com/zhaw-iwi/prometheus/actions/runs/37031277038),
which succeeded for `42a29a1`. Heroku app `valerian` released **v103** at
2026-10-02 16:04:36 UTC; `web.1` is up and startup is confirmed in the release logs.
Heroku CLI and bounded HTTP/read-only database checks verified:

- Health returned HTTP 200 / UP; `/valerian/` and access-code login returned 200.
- The authorized admin catalog contains the new definition (84 types versus 83
  before deployment). No access-code assignments were changed.
- The three nullable task columns are present. The saved-agent count remained 26
  before and after deployment; verification created no agent or interaction data.
- The 916 sampled log lines since the release contained no R14/R15/H10, query
  quota, schema or startup-failure markers. This is a startup sample, not sustained
  load or physical speech acceptance.

Sanitized evidence is in `target/generic-rollout-before.json`,
`target/generic-rollout-after.json`, `target/generic-rollout-heroku.json` and
`target/generic-rollout-workflow.json`. Before rollout, v102 health/login already
passed; no outage recovery is attributed to this change. Credentials, local
properties and `codexpython.md` were not committed or changed. Completion records
are synchronized main -> feature/gptlive and main -> agents using documentation-only
`[skip ci]` commits/merges to retain the verified v103 runtime.

## Limits and live trial

This first version configures bounded reactions, not arbitrary generated code,
external tools, nested workflows or timers. It does not turn detectors on. All
declared observations are available as context; trigger fields are explicitly
listed in `TaskSpec.FIELDS`, including the first forecast day's supported fields.
Missing confidence cannot meet a positive confidence threshold. Sensor timestamps
allow at most two seconds of browser clock lead and retain the source TTL.

Live requires a completed native speech segment after the latest spoken intent,
then new matching samples; completion is a backend observation, not physical
audibility or a guaranteed whole-utterance boundary. Text/Continuous use cooldown
and source freshness, without a playback-completion acknowledgement. Stop prevents
new task reactions; existing provider speech/queued announcements may still finish.
Live's existing coalescing/append acknowledgement delays remain. Speculative or
duplicate voice replies remain a provider-quality trial concern despite guidance.

Assign the new type to an access code, create a fresh instance, enable the desired
sensors and start the chosen speech mode. Ask about capabilities, propose a
facial-feedback joke task, clarify neutral cues/limits, then activate it. Verify
negative cues alone request another joke, positive cues complete the task, a stop
works, and current presence questions report camera coverage accurately. Repeat
after reconnect and with sensors stopped. Paid provider trials, physical voice
timing and camera quality have not been run for this milestone.
