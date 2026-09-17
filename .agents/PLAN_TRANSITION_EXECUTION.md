# Transition actions and speculative behaviour

Requested on 2026-09-17 after the compact-Luna Heroku measurements. Work starts
on features/needforspeed and is merged into agents for the authorized testing
deployment. Main remains unchanged.

## Execution model

Keep transition authority and publication on the existing serialized agent path.
Slow work receives immutable prepared inputs, never live Agent/State/Storage or
a JPA context. Action completion applies a validated result through the same
application serialization/persistence boundary. Speculative behaviour has no
storage, state, SSE or speech side effects before the state machine accepts it.

Use one action contract with an explicit execution mode: non-blocking by default,
blocking when an agent author declares that following work requires its result.
A proposed fluent `action.blocking()` configuration avoids duplicating every
action class into synchronous/asynchronous variants. Concrete actions still
define their own preparation, computation and result application.

## Pending durability decision

The user has been asked whether unfinished actions should survive a Heroku
restart. Recommended: durable jobs committed with the transition, resumed after
restart, with bounded workers and idempotent result application. Alternative:
bounded in-memory work with explicitly acknowledged loss on restart.

This changes the persistence/failure contract, so action execution defaults are
not changed before that answer. Background delivery is not fire-and-forget:
success/failure/cancellation must be visible. Retrying arbitrary external side
effects requires explicit idempotency; do not claim exactly-once external work.

## Milestone 176 - One structured generation path

- Every PromptPolicy generation uses BEHAVIOUR / JSON_OBJECT and returns a
  BehaviourPlan through BehaviourPlanInference.
- Policies without nonverbal instructions request only `{"speech":"..."}`;
  combined policies retain compact nv encoding. All Final constructors inherit
  this shared behavior without replacing stored prompts or adding modalities.
- Deterministic policies continue constructing plans directly with zero LLM
  calls. Decisions, extraction and summarization keep their own typed purposes.
- Tests cover all Final constructors, ordinary speech-only generation, invalid
  output without publication, catalog call counts, persisted closing/reset and
  a real HTTP/SSE transition replay on a disposable local database.

## Milestone 177 - Non-blocking transition actions

- Separate preparation, slow computation and validated result application.
  Freeze selected pre-transition events and resolved storage-dependent prompts.
- Make non-blocking execution the authoring default for any transition, with an
  explicit blocking option. Blocking work must complete successfully before
  continuing the transition; never hide a required-action failure.
- Audit dependencies throughout core, healthcare, common states and the agents
  application catalog. RPS selection/result updates, Gather/Choice data consumed
  by following policies, and extraction followed by DynamicRemoveTopicAction need
  explicit blocking semantics. Preserve ordered action dependencies.
- Migrate summary/outcome recording to non-blocking execution, including
  StaticSummarisationAction AND the actual catalog's StaticExtractionAction
  summary/outcome uses. Changing only the summarisation class would not remove
  the measured closing extraction delay.
- Freeze work before state entry can alter history. Apply results only to the
  correct agent generation; reset/delete must invalidate obsolete work. Preserve
  ordering for successive writes to a destination and avoid lost storage updates.
- Implement the selected durability design with bounded capacity, safe shutdown,
  recorded failures and content-free job/turn correlation. Never pass managed
  entities to a worker or hold the agent lock for background inference.
- Cover persisted existing actions explicitly: an additive execution-mode field
  alone cannot safely change dependency-sensitive stored action graphs. Provide
  a tested migration/default strategy; do not silently require agent recreation.
- Verify asynchronous farewell can start before outcome completion, blocking
  dependencies, action ordering, error handling, reset/delete, stale results and
  transaction rollback. Use latches and isolated-database tests. If durable,
  also verify restart/reclaim and duplicate completion handling.

## Milestone 178 - Speculative behaviour during transition evaluation

- Start at most one eligible current-state behaviour inference after recording
  the new input and before awaiting transition decisions, using an immutable
  request snapshot with resolved nested policy, selected events and storage.
- Only reuse the candidate if no transition occurred, no competing response was
  selected and the effective request/state/event inputs still match. A self-loop
  or nested transition also invalidates it, even if the final state name matches.
- On transition, abandon the candidate immediately and generate from the new
  policy/history after required blocking actions. Never wait for stale output.
  Cancellation is best effort: provider computation/billing may continue.
- Give required decisions/new-state generation priority over speculative work.
  Bound parallelism and queue capacity; saturation skips speculation and follows
  the normal path. A stale request must not reserve the only available slot.
- Preserve acknowledge/generate contracts, explicit sensory generation rules,
  deterministic policies and unknown state/policy extension points. Do not
  generate automatic speech for observations that previously produced none.
  The current separate HTTP acknowledge/generate path needs explicit treatment:
  reuse cannot depend on a future stored in a JPA-loaded entity between requests.
- Preserve regulation precedence and prevent duplicate behaviour publication.
  Speculative failures matter only if that candidate is needed; cancelled/stale
  output must never be parsed into a published response or overwrite newer work.
- Record started/reused/discarded/failed speculative work and observed provider
  dispatches. Distinguish response latency from extra billed work.
- Test no-transition overlap, outer/inner/self transitions, blocking-action
  dependencies, sensory silence, regulation response, saturation, late success
  and failure, reset/delete, client duplicate ingress and other-agent progress.

## Performance hypotheses, not acceptance claims

The latest six ordinary turns have 1.02 s of decisions followed by 1.66 s of
behaviour generation. Ideal overlap replaces their sum (~2.68 s) with their
maximum (~1.66 s), saving ~1.02 s if provider timings remain comparable.
The closing sample contains ~2.05 s of extraction that could leave the response
critical path when independent of the farewell. Resource contention, changed
prompts and provider variability can change these numbers. Unified speech-only
JSON is a consistency improvement, not itself a latency reduction claim.

Retest with the same Heroku agent/preset and inspect live quality, actual timing,
discarded work and background completion. Keep the two-second target unverified.
