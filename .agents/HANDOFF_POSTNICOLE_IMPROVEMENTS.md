# Post-Nicole improvements: Coordination / ES-08 handoff

Updated 2026-11-12. The owner approved **existing Speech for committed
Coordination replies**, retaining Live input, the state machine, budgets and
playback confirmation. **ES-08's agreed behavioural scope is complete:** all three
real-provider journeys have separate semantic PASS reviews on the same source
configuration, and final Java/Node/browser/preflight/package checks pass.
The owner has authorized committing and pushing `main`, then merging it into
`agents` for Heroku redeployment. Read the current evidence at the top of
`ESCALATION_RESULTS.md`. Earlier native-output attempts remain there as history.
Physical trials are NOT RUN; capacity remains deferred. Pushing `agents` triggers
the existing deployment workflow for `valerian`.

## Establish the checkout

Read `CODEX.md`, `CONTEXT.MD`, and `../PROJECT.md` through Current milestone
state, stopping before Historical milestones checklist. Inspect branch,
worktrees and dirty files. Acceptance was verified on `main` based on `333f0f0` in
`D:\VSCWorkSpace\prometheus`; the retained source manifests identify the exact
pre-commit runtime and test content.
Do not revert unrelated work, apply office backup stashes, or assume a deployment
branch/application contains these changes without checking its current revision.
Preserve the deployment branch's application catalogue and workflow during the
merge. No parallel agents are required.

`PLAN_ESCALATION.md` defines ES-01–07 and the three ES-08 journeys.
`ES08_SPEECH_DELIVERY_DECISION.md` records the approved output-path decision.
Its authority is settled; do not ask the owner to choose again.

## Mental model

The backend owns proposal terms, preparation, acceptance, run/revision, cue
eligibility, contribution budgets, pause/stop and Reflection. Live admits user
speech; it does not reconstruct audible Coordination output. The sequence is:

1. Commit complete spoken words with the existing response identity.
2. Claim one synthesis durably; invoke the existing Speech gateway outside the
   database transaction and agent lock.
3. Revalidate owner, epoch, revision, interruption and the original deadline;
   store explicit SYNTHESIZED provenance as unplayed.
4. Recheck authority at release; play through the production browser player.
5. Require observed output and source completion before recording playback.
   A final task still requires its separate playback-cue acknowledgement.

Generated, released, heard and task-completed remain different states. Lost
acknowledgement means uncertainty, never automatic replay or a fresh budget.
Native output is muted; its transcripts remain diagnostic evidence. Unplayed
speech cannot supply conversation, task prompts, consent or cue evidence.

Ordinary synthesis retains the original 45-second response / 60-second playback
bounds; help and optional sensory remarks keep their shorter release deadlines.
PCM is 24 kHz mono, at most 45 seconds/2,160,000 bytes. Four concurrent workers,
3,000 characters per reply and durable claims bound work. Speech uses the existing
configured model with Alloy at normal speed; synthetic visitors use Onyx.
The observer accounts for known leading PCM silence without trimming the clip
or extending its expiry. Source end prevents internal pauses completing a clip.
Immediate interruption uses the existing microphone worklet. A delayed native
caption is not new voice; a narrow backend guard keeps tiny structural fragments
over sustained measured silence as incomplete audit data. Real voice and ordinary
words retain their existing admission and interruption fences.

New delivery-aware Coordination instances require the current scoped Live client.
Saved graphs are not migrated; use a fresh instance for rollout acceptance.
Other agents and ordinary Text/Continuous paths keep their existing behavior.

## Code and tests

Java paths below start at `src/main/java/ch/zhaw/prometheus/`.

| Concern | Anchors |
| --- | --- |
| Committed words and task authority | `agentdefs/core/CoordinationSpeechApproval.java`, `CoordinationUpdateAction.java`, `CoordinationPolicy.java`, `model/coordination/CoordinationTaskContract.java` |
| Scoped delivery | `application/LiveCommittedSpeechService.java`, `ScopedLiveSessionService.java`, `controllers/ScopedLiveSessionController.java` |
| Heard history and contribution boundaries | `model/event/SpeechProvenance.java`, `ConversationProjection.java`, `model/task/TaskResponse.java`, `agentdefs/core/CoreTaskVoice.java` |
| Quiet Live context | `application/live/LiveContextDelivery.java`, `LiveVoicePolicyAdapter.java`, `application/LiveContextBridgeService.java` |
| Browser output | `src/main/resources/public/live/committed-speech.js`, `approved-speech.js`, `playback-cue.js`, `client.js` |
| Acceptance | `tests/gptlive/core_backend_journey.mjs`, `escalation_journeys.mjs`, `run_acceptance.py`, `review_synthesized_audio.py` |

Start with `LiveCommittedSpeechServiceUnitTest`,
`CommittedSpeechIntegrationTest`, `CoordinationVoiceUnitTest`,
`ProviderCaptureBudgetTest`, neighboring TaskResponse/Coordination tests,
`tests/js/live/committed-speech.test.mjs`, `playback-cue.test.mjs`, and
`tests/playwright/valerian-gptlive-smoke.spec.mjs`. The browser covers real WebRTC,
committed playback to Reflection, leading silence and lost acknowledgement.
Native approval/correction regressions remain covered separately.

## Verification and evidence hygiene

Read `../tests/gptlive/README.md`. Use the portable runner's disposable loopback
MySQL schema/account; never point it at the application or Heroku database.
Supply credentials privately in ignored configuration, not chat or CLI arguments.

```powershell
python tests/gptlive/run_acceptance.py --java-tests all
python -B tests/gptlive/test_acceptance_runner.py
$nodeTests = @(rg --files tests/js -g '*.test.mjs')
node --test @nodeTests
.\mvnw.cmd -q -DskipTests package
git diff --check
```

Run each paid scenario explicitly with `--java-tests none --paid-core coordination
--coordination-scenario escalation-recovery`, `escalation-dialogue` or
`escalation-social`. Each invocation retains the existing caps: one Live session/
360 seconds, 12 visitor turns, 24 backend requests, 2,048 completion tokens and
80,000 input bytes per request, 200,000 reported backend-token stop threshold,
12 visitor synthesis clips/6,000 characters, 420-second child wall deadline.
Committed output additionally has 12 Speech calls/6,000 characters per journey.
The last backend request may cross its token threshold; these are not money caps.
Do not run heavy verification concurrently with paid audio trials.

Retain raw journey JSON, exact PCM hashes, provider usage, independent local ASR,
a separate semantic verdict and source manifest. Automated PASS alone is not
semantic acceptance. A failed critical check keeps that scenario unmet; repair
its demonstrated cause before a paid rerun. Local ASR can err and does not prove
physical speaker audibility.

`target/` evidence, speech caches, helpers and private configuration do not
transfer through Git. **Do not run Maven clean.** Preserve failed runs and remove
only runner-owned resources. Do not commit raw traces, conversations, secrets or
database data. Exact checks, failures and current acceptance are authoritative in
`ESCALATION_RESULTS.md`; physical operator steps are in `ESCALATION_OPERATOR.md`.
