# Generic multimodal interaction refinement

Authorized 2026-10-03: implement six milestones on feature/gptlive, verify and
commit/push each, then integrate main and agents for Heroku deployment. This
authorization supersedes the ordinary stop after each milestone. Preserve user
changes, deployment personas, saved agreements and conversation history.

The backend owns the agreement and task state. Sensing supplies evidence, Live
communicates committed outcomes, and the existing activity footer explains work
and waiting. Extend these mechanisms rather than creating a second controller.

## Milestone 208 Generic configuration

Remove demonstration-specific built-in prompts and shortcuts. Describe the
schema neutrally, default lightweight tasks to one sample, distinguish draft
acceptance from execution and retain explicit authorization. Verify prompt
contracts and varied task fixtures. Existing stored policies are upgraded in 213.

## Milestone 209 Executable agreement semantics

Distinguish resumable waiting and pause from terminal completion/cancellation.
Validate rule combinations and confidence semantics, including observed absence
versus unknown evidence. Verify guards, transitions, limits and MySQL reload.

## Milestone 210 Responsive cues and visible acceptance

Accept one fresh qualifying cue with duplicate and response-completion guards.
Use narrow armed-cue emission rather than increasing all sensor traffic. Extend
the existing footer for acceptance, activation waiting and task lifecycle. Verify
controlled clocks, query/event budgets, accessibility and Playwright visuals.

## Milestone 211 Live authority and lifecycle

Announcements follow committed state; acknowledgments cannot establish task
completion. Correlate response completion conservatively. Pause associated
interactive Generic tasks on Live stop/loss, preserve the agreement for explicit
resumption, fence late work and distinguish intentional shutdown. Verify close,
reconnect, delayed activation, fragments and lifecycle races.

## Milestone 212 Telemetry and delivery

Add content-free phase/revision/cue correlation and fixed failure categories.
Measure and reduce announcement queuing by prioritizing results and coalescing
superseded sensory updates without weakening freshness. Preserve query budgets,
clock separation, bounded collection and combined model requests.

## Milestone 213 Saved instances and integrated rollout

Provide a previewable idempotent upgrade of recognized built-in policies.
Preserve custom prompts, task meaning and history; identify incompatible saved
agreements for focused clarification. Verify migration and integrated journeys
on isolated MySQL and Playwright, merge feature/gptlive to main and main to agents,
verify Heroku and apply the scoped upgrade. Record actual provider/physical trial
coverage and leave unsupported acoustic claims explicitly unverified.

## Verification and boundaries

Use tests/gptlive/run_acceptance.py with --database-properties
src/main/resources/application-test.properties. Never launch Maven/application
against ignored production credentials. Tests use disposable schemas and synthetic
providers; a small provider configuration trial may check interpretation, but
physical camera/microphone/speaker acceptance requires the user's participation.
No extra model calls or database polling for telemetry. No inferred playback
completion from captions or append acknowledgements. Historical audit and real
user agreements retain their original content.

Evidence: GENERIC_REFINEMENT_RESULTS.md. Stages 208-211 complete; 212 follows.
