# Core-agent improvements from Gala

Implementation branch: `feature/gptlive`, based on main 19d2d51c (2026-10-26).
The user authorizes implementing the milestones consecutively, committing and
pushing each before continuing. Integrate into main after acceptance; deployment
catalogs and the standalone Valerian repository are separate integrations.

## Invariants and method

PROMETHEUS owns state, agreements, secrets, outcomes and executable actions.
Live owns wording when explicitly opted in. Preserve Text/Continuous behaviour,
public HTTP/SSE contracts, durable transcripts, immediate access revocation and
existing saved-instance semantics. Do not import Gala's persona, event knowledge,
silence settings or game return graph. Talk to Me remains exact-text.

Use small opt-in helpers at existing policy/action boundaries. Test deterministic
rules with controlled clocks and storage reloads, then real scoped services with
disposable local MySQL. Never test against Heroku's database. Provider tests are
explicitly paid, bounded, fail visibly and retain failure evidence. No automatic
retry-until-pass. Captions and received media cannot establish physical audibility
or robot movement. Stop for an unforeseen substantive behaviour/compatibility
decision; ordinary implementation choices do not require approval.

## Milestones and acceptance

| ID | Deliverable | Testable outcome / gate |
| --- | --- | --- |
| CA-01 | Baseline and reusable acceptance harness | Existing core contracts and local task journeys pass; paid Live Multimodal conversation/sensing/request journey runs through the real app and persistence, with semantic assertions, bounded duration, artifacts and cleanup. |
| CA-02 | Occasional, varied gestures for Generic, Social Space, Live Multimodal | Bounded persisted recent history suppresses decorative bursts/repeats, preserves other channels, and exempts explicitly requested gestures and agreed task actions. Reload/reset, no-gesture output and corrupt optional history are covered. |
| CA-03 | Meaningful sensory reactions | Social Space requires stable count change, cooldown and fresh/current context; active/paused tasks and conversation take priority. Face/social demos distinguish evidence refresh from change, respect corrections and retain a demonstrative cadence. Fake-clock tests cover fluctuation, stale/obsolete results, correction and re-entry; scoped integration and real-provider demo journeys pass. |
| CA-04 | Confirmed task facts with Live wording; contextual short replies | Generic/Social Space expose committed facts rather than backend speech in Live. Text/Continuous retain replies. Harmless acknowledgements preserve tasks; yes resolves only against a relevant confirmed invitation and cannot independently authorize actions. Task proposal/acceptance/cue/completion and reload/short-reply regressions pass with real provider journeys. |
| CA-05 | Core RPS round reliability | Persist round identity, reject stale/low-confidence/duplicate/previous-round signs and clean new rounds. Preserve result/replay states. Optional countdown/playback uses the existing cue contract, with bounded recovery. Two complete real-provider rounds and capture/UI regressions pass; no physical synchronization claim. |
| CA-06 | Explicit guessing roles and secrets | Persist agent-selected secret and role; separate unsupported facts from unclear questions, reveal on give-up, and fresh rounds. Preserve playful role negotiation and healthcare tone. Human-selected secrets remain unknown to the backend. Both role directions, reveal/restart/reload and real-provider happy paths pass. |
| CA-07 | Optional Social Space reception lifecycle | Explicit opt-in encounters start/close with departure grace; transient absence preserves conversation; a new visitor gets fresh dialogue. Persistent tasks are neither silently erased nor reactivated. Stop/revocation stay immediate. Local integration and Playwright lifecycle checks pass. |
| CA-08 | Combined acceptance and handoff | Relevant full Java/Node regressions, local database journeys, Playwright visual/interaction checks and paid happy paths pass. Record every provider failure and remaining limitation; supply manual physical-test script and main/standalone-client integration notes. |

CA-02/03 build on CA-01; CA-04 establishes facts/control conventions for CA-05/06;
CA-07 builds on CA-03/04; CA-08 checks their combination. Intermediate physical
tests are not prerequisites, but physical audio/robot behaviour remains a final
operator check. A provider behavioural failure is not reclassified as a pass
because transport succeeded. Saved prompts are not rewritten implicitly; document
when new instances are required.

## Existing implementation and test anchors

- `model/task`: TaskPolicy, TaskUpdateAction, TaskMemory, TaskSpec, task graph;
  GenericMultimodalTaskUnitTest/IntegrationTest and SocialSpaceLiveJourneySmokeIntegrationTest.
- `agentdefs/core`: LiveMultimodal, SocialSpacePresenceDecision/ResponseAction,
  ValerianCoreAgentFactory; LiveMultimodalUnitTest, SocialSpaceAgentUnitTest,
  ValerianCorePromptContractTest.
- `model/rps` and CoreRpsRevealPolicy/CoreRpsResultPolicy: preserve result/replay.
- RoleClarificationGuessingGame and healthcare SingleStateGuessingGameUserGuess;
  HealthcareUseCasePromptContractTest.
- `ExternalVoicePolicy`, `LiveActivityContext`, current Live ingress/delivery and
  playback-cue contracts; `public/live`, `public/valerian`, existing JS/Playwright.
- `tests/gptlive/run_acceptance.py`: local database ownership and cleanup; extend
  rather than bypass. Gala's provider runner is a reference, not a dependency.

Evidence and milestone status: [COREAGENTIMPROVEMENT_RESULTS.md](COREAGENTIMPROVEMENT_RESULTS.md).

CA-01 through CA-05 are implemented and accepted. Continue with CA-06; the final
combined acceptance remains CA-08. The results record provider failures and the
remaining prompt-only speech limitations as well as successful checks.
