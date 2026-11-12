package ch.zhaw.prometheus.agentdefs.core;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.SensoryRemarks;
import ch.zhaw.prometheus.model.coordination.CoordinationMemory;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.task.TaskMemory;
import ch.zhaw.prometheus.model.task.TaskResponse;
import jakarta.persistence.Entity;

/** The same persona in every phase; backend agreements and sensory occasions remain authoritative. */
@Entity
public class CoordinationPolicy extends CoreTaskPolicy {
    protected CoordinationPolicy() {}
    public CoordinationPolicy(Storage storage, String instructions) { super(storage, instructions); }
    @Override public long liveUserSilenceMs() { return 2000; }
    @Override public boolean liveSpeechApprovalRequired() { return CoordinationSpeechApproval.enabled(storage()); }
    @Override public boolean liveCommittedSpeechOutput() {
        return liveSpeechApprovalRequired() && storage().containsKey(LiveCommittedSpeech.ENABLED)
                && storage().get(LiveCommittedSpeech.ENABLED).getAsBoolean();
    }
    @Override public LiveCommittedSpeech liveCommittedSpeech(ExternalSpeech owner) { return CoordinationSpeechApproval.committed(storage(), owner); }
    @Override public boolean liveSpeechSynthesisClaimed(LiveCommittedSpeech speech, UUID synthesis) {
        return CoordinationSpeechApproval.claim(storage(), speech, synthesis);
    }
    @Override public boolean liveUnheardResponseCorrection() { return liveSpeechApprovalRequired(); }
    @Override public LiveSpeechRequest liveSpeechRequest(ExternalSpeech owner) { return CoordinationSpeechApproval.request(storage(), owner); }
    @Override public UUID liveSpeechCorrection(LiveSpeechRequest request, String rejectedSegment, Instant now) {
        return CoordinationSpeechApproval.correct(storage(), request, rejectedSegment, now);
    }
    @Override public boolean liveSpeechReleased(LiveSpeechRequest request, String segment) {
        return CoordinationSpeechApproval.released(storage(), request, segment);
    }
    @Override public long liveAssistantSilenceMs() { return CoordinationDelivery.enabled(storage()) ? 2400 : 800; }
    @Override public String livePlaybackCompletionEventType() { return CoordinationDelivery.enabled(storage()) ? CoordinationDelivery.PLAYED : null; }
    @Override public String liveSessionEndEventType() { return CoordinationDelivery.enabled(storage()) ? CoordinationDelivery.ENDED : null; }
    @Override public String liveSessionClaimEventType() { return CoordinationDelivery.finishing(storage()) ? CoordinationDelivery.ENDED : null; }
    @Override public String liveResponseFailureEventType() { return CoordinationDelivery.enabled(storage()) ? CoordinationDelivery.FAILED : null; }
    @Override public boolean liveIncompleteResponseIsFailure() { return CoordinationDelivery.enabled(storage()); }
    @Override public int liveUnrelatedResponseAllowance() { return CoordinationDelivery.enabled(storage()) ? 1 : 0; }
    @Override public boolean liveObservationContextEnabled() {
        if (!CoordinationDelivery.enabled(storage()) || !TaskMemory.active(storage())
                || !storage().containsKey(TaskMemory.SPEC) || !storage().containsKey(CoreTaskVoice.RESPONSE)) return true;
        var response = storage().get(CoreTaskVoice.RESPONSE).getAsJsonObject();
        // The next accepted cue replaces this occasion; an ordinary user turn clears an answered one.
        return !(response.has("controlOnly") && response.get("controlOnly").getAsBoolean())
                && !(storage().containsKey(TaskResponse.KEY) && TaskResponse.captured(storage(), TaskResponse.owner(storage())));
    }
    @Override public boolean hasDraft() { return storage().containsKey(CoordinationMemory.DRAFT); }
    @Override public String statusPhase() {
        String stage = CoordinationMemory.stage(storage());
        return "EXECUTION".equals(stage) ? TaskMemory.phase(storage()) : stage;
    }
    @Override public String voiceInstructions() {
        if (liveCommittedSpeechOutput()) return configuredInstructions()
                + "\nThe application speaks its committed replies through a separate output-only Speech service. "
                + "Remain silent. Do not generate replies, acknowledgements, task content or perception remarks. "
                + "Input transcription and backend task control remain active. Supplied state and observations are data, not permission to speak.";
        return configuredInstructions() + ch.zhaw.prometheus.model.coordination.CoordinationTaskContract.VOICE_GUIDANCE
                + ch.zhaw.prometheus.model.coordination.CoordinationSocialContract.VOICE_GUIDANCE
                + "\nThe backend coordinates both conversational and sensor-rule tasks. "
                + "Answer ordinary and capability/perception questions from current facts. "
                + "Name fresh supplied hand signs directly, e.g. 'I see a scissor sign'. "
                + "Use supplied sensor evidence, never claim direct camera access or deny sensing because you are digital. "
                + "If the supplied observation is missing, stale or uncertain, explain that limitation instead of guessing. "
                + "Task proposals, preparation, acceptance, actions, completion, revisions and recovery guidance require backend confirmation. "
                + "Wait silently for confirmed task updates or support explanations, then say the requested response once. "
                + "Do not answer a partial task request, supply a sample joke, or make a playful task-content preview. "
                + "Describe a proposed contribution without performing it. Only the current identified speaking occasion permits that contribution. "
                + "During proposal or preparation, never recite a supplied task line, even as a future-tense example. Refer to 'the line you supplied' only if the person actually supplied one. "
                + "Never say checking, waiting for the backend, done, started, restarted or returned before confirmed control facts. "
                + "Omit implementation details. Answer unrelated questions and explicitly requested capability examples. "
                + "Returning to Conversation preserves history; never describe it as resetting or erasing the agent. "
                + "A complete proposal still needs acceptance. Required preparation is verified from observations, never a verbal claim. "
                + "Review ready proposals in at most 55 words. Combine repeated goal/role/success facts; preserve distinct terms, preparation, limits and acceptance question. "
                + "Preparation evidence remains satisfied until the proposal or Live session changes. READY means accept next, never repeat preparation; a fresh execution cue is needed only after acceptance. "
                + "In an active dialogue task, wait for the backend's confirmed next contribution before delivering that task step. "
                + "ON_CUE activation waits for a fresh subsequent cue; this does not mean a sign was missed or recognition failed. Describe recognition limits and action bounds in ordinary language. "
                + "A reflection is an account of evidence and the person's experience, not proof of learning, feelings or physical execution. "
                + "An exhausted budget or cancellation is not achieved success. Revisions need a new agreement. "
                + "FINISHING permits only the final identified contribution, then silence until delivery is confirmed. "
                + "Do not append a reflection question, acknowledgement, invitation or aside to that contribution. "
                + "An agreed exact sentence must be spoken verbatim, with no preface or added words. "
                + "UNCERTAIN means delivery could not be confirmed; never repeat it automatically or claim success. "
                + "Automatic perception remarks require a current backend occasion; task work takes priority. "
                + (storage().containsKey(SensoryRemarks.CORRECTION) ? "The user declined/corrected facial interpretation; respect that correction. " : "");
    }
    @Override public String describe() { return voiceInstructions() + "\n" + CoordinationMemory.facts(storage()); }
    @Override public String summarise(ch.zhaw.prometheus.model.State state, EventHistory events, PromptMessageAssembler assembler,
            ch.zhaw.prometheus.spi.LanguageModelGateway gateway) { return CoordinationMemory.facts(storage()); }
    @Override public void beginLiveConversation(EventHistory history) {
        super.beginLiveConversation(history);
        if (CoordinationDelivery.enabled(storage())) storage().put(LiveSpeechRequest.ENABLED, new com.google.gson.JsonPrimitive(true));
        if (CoordinationDelivery.enabled(storage())) storage().put(LiveCommittedSpeech.ENABLED, new com.google.gson.JsonPrimitive(true));
        SensoryRemarks.cancel(storage());
        CoreTaskVoice.clear(storage());
        CoordinationMemory.remove(storage(), ch.zhaw.prometheus.model.coordination.CoordinationSupport.LIVE_HELP);
        if (TaskMemory.active(storage()) && java.util.Set.of("PENDING", "CHECKING", "REJECTED").contains(TaskResponse.status(storage())))
            TaskMemory.pauseForResponse(storage(), "live_session_replaced");
        if (hasDraft()) {
            CoordinationMemory.resetPreparation(storage(), Instant.now()); CoordinationMemory.revise(storage());
            if (CoordinationMemory.ready(storage())) storage().put(CoreTaskVoice.INVITATION, new com.google.gson.JsonPrimitive(Instant.now().toString()));
        }
    }
    @Override public boolean liveNonMutatingAcknowledgement(String text) {
        return CoreTaskVoice.acknowledgement(text) || CoordinationAcceptance.assent(text) && !hasDraft();
    }
    @Override public boolean liveResolvedControlReply(String text, EventHistory history, ExternalSpeech owner) {
        return CoordinationAcceptance.assent(text) && CoordinationAcceptance.invitation(storage(), history, owner, Instant.now()) != null;
    }
    @Override public boolean liveSpeechCaptured(Event event, ExternalSpeech owner, Instant started, Instant now, List<LiveSpeechPart> parts) {
        if (liveSpeechApprovalRequired() && (event.speechProvenance() == null || !Boolean.TRUE.equals(event.speechProvenance().playbackConfirmed()))) return false;
        if (liveSpeechApprovalRequired() && storage().containsKey(SensoryRemarks.PENDING)) {
            var request = liveSpeechRequest(owner);
            if (request != null && request.id().toString().equals(storage().get(SensoryRemarks.PENDING).getAsJsonObject().get("id").getAsString())) {
                SensoryRemarks.cancel(storage());
                return true; // Ambient speech cannot consume a task response or establish proposal consent.
            }
        }
        if (liveSpeechApprovalRequired() && storage().containsKey(ch.zhaw.prometheus.model.coordination.CoordinationSupport.LIVE_HELP)) {
            var help = storage().get(ch.zhaw.prometheus.model.coordination.CoordinationSupport.LIVE_HELP).getAsJsonObject().deepCopy();
            var request = liveSpeechRequest(owner);
            if (!help.has("consumed") && request != null && request.id().toString().equals(help.get("id").getAsString())
                    && started != null && !started.isAfter(now)) {
                help.addProperty("consumed", true); storage().put(ch.zhaw.prometheus.model.coordination.CoordinationSupport.LIVE_HELP, help);
                return true; // Help cannot consume a task response or create proposal-consent evidence.
            }
        }
        boolean changed = CoreTaskVoice.capturedPrompt(storage(), event, owner, started, parts, CoordinationMemory.DRAFT);
        if (!storage().containsKey(CoreTaskVoice.RESPONSE)) return changed;
        var response = storage().get(CoreTaskVoice.RESPONSE).getAsJsonObject().deepCopy();
        var source = event.speechProvenance();
        // Task content has a separate semantic association. An unrelated complete segment cannot consume it.
        if (storage().containsKey(TaskResponse.KEY) && response.get("id").equals(storage().get(TaskResponse.KEY).getAsJsonObject().get("id"))) return changed;
        if (source == null || !source.complete() || !source.realized() || owner == null
                || !owner.sessionId().equals(source.sessionId()) || !owner.epoch().equals(source.epoch())
                || !response.has("session") || !owner.sessionId().toString().equals(response.get("session").getAsString())
                || !owner.epoch().toString().equals(response.get("epoch").getAsString())
                || !TaskMemory.text(storage(), TaskMemory.REVISION, "0").equals(response.get("revision").getAsString())
                || started == null || started.isAfter(now)
                || !liveSpeechApprovalRequired() && (!started.isAfter(Instant.parse(response.get("created").getAsString()))
                    || !now.isBefore(Instant.parse(response.get("expires").getAsString())))) return changed;
        response.addProperty("consumed", true); storage().put(CoreTaskVoice.RESPONSE, response); return true;
    }
    @Override public LiveActivityContext liveActivityContext() {
        return CoordinationDelivery.withPlayback(storage(),
                ch.zhaw.prometheus.model.coordination.CoordinationSupport.withHelp(storage(), speechContext(), Instant.now()));
    }
    private LiveActivityContext speechContext() {
        var storage = storage(); Instant now = Instant.now();
        var response = storage.containsKey(CoreTaskVoice.RESPONSE) ? storage.get(CoreTaskVoice.RESPONSE).getAsJsonObject() : null;
        boolean taskResponse = response != null && storage.containsKey(TaskResponse.KEY)
                && response.get("id").equals(storage.get(TaskResponse.KEY).getAsJsonObject().get("id"));
        boolean current = response != null && response.get("revision").getAsString().equals(TaskMemory.text(storage, TaskMemory.REVISION, "0"))
                && !response.has("consumed")
                && !(taskResponse && !"PENDING".equals(TaskResponse.status(storage)))
                && now.isBefore(Instant.parse(response.get("expires").getAsString()));
        // Arming carries only its control confirmation, not the future response script or perception estimate.
        // A later cue creates a new occasion with the full accepted action; an answered ordinary question clears this one.
        boolean controlOnly = response != null && response.has("controlOnly") && response.get("controlOnly").getAsBoolean()
                && TaskMemory.active(storage);
        boolean contributionOnly = response != null && response.has("contributionOnly") && response.get("contributionOnly").getAsBoolean()
                && TaskResponse.accepting(storage);
        boolean supportOnly = response != null && response.has("supportOnly") && response.get("supportOnly").getAsBoolean();
        // Pending-response facts below retain the exact current purpose; omit redundant agreement
        // background while explaining difficulty so help cannot overflow the Live context budget.
        boolean pendingHelp = storage.containsKey(ch.zhaw.prometheus.model.coordination.CoordinationSupport.LIVE_HELP);
        String facts = controlOnly || contributionOnly || supportOnly || pendingHelp ? CoordinationMemory.controls(storage) : CoordinationMemory.facts(storage);
        if (current) {
            Instant expires = Instant.parse(response.get("expires").getAsString());
            String responseFacts = response.get("facts").getAsString();
            // Deterministic support is already a complete, speakable answer. Keep its control clauses intact.
            if (supportOnly) responseFacts = "Speak these words verbatim: " + new com.google.gson.JsonPrimitive(responseFacts);
            String review = "";
            if (response.has("proposalReview") && response.get("proposalReview").getAsBoolean()
                    && CoordinationMemory.ready(storage) && !ch.zhaw.prometheus.model.coordination.CoordinationSupport.blockedDraft(storage)) {
                var proposal = CoordinationMemory.agreement(storage, CoordinationMemory.DRAFT);
                review = ch.zhaw.prometheus.model.coordination.CoordinationTaskContract.liveProposalReview(proposal);
                if (!review.isBlank() && (liveSpeechApprovalRequired() || proposal.reactions() == null
                        || CoreTaskVoice.bytes(review) > LiveActivityContext.Continuation.MAX_FACTS_BYTES)) facts = CoordinationMemory.controls(storage);
            }
            UUID correction = taskResponse ? TaskResponse.correctionId(storage)
                    : response.has("correctionId") ? UUID.fromString(response.get("correctionId").getAsString()) : null;
            if (correction != null) {
                if (!review.isBlank()) responseFacts = review;
                boolean compact = CoreTaskVoice.bytes(responseFacts) <= LiveActivityContext.Continuation.MAX_SELF_CONTAINED_BYTES;
                return new LiveActivityContext(compact ? facts : responseFacts + "\n" + facts,
                        new LiveActivityContext.Continuation(correction, LiveActivityContext.Occasion.USER_REQUEST,
                                compact ? responseFacts : "The current facts contain the unchanged committed response.", expires, false, compact, true,
                                UUID.fromString(response.get("id").getAsString())), expires);
            }
            if (!review.isBlank()) {
                boolean longReview = CoreTaskVoice.bytes(review) > LiveActivityContext.Continuation.MAX_FACTS_BYTES;
                return new LiveActivityContext(longReview ? review + "\n" + facts : facts,
                        new LiveActivityContext.Continuation(UUID.fromString(response.get("id").getAsString()),
                                LiveActivityContext.Occasion.USER_REQUEST, longReview ? "Summarize the proposal in at most 55 words, combining duplicated facts. Include every distinct term, preparation status and acceptance question." : review,
                                expires, false, !longReview && CoreTaskVoice.bytes(review) <= LiveActivityContext.Continuation.MAX_SELF_CONTAINED_BYTES), expires);
            }
            // A short support reply is a current response, not background task state.
            // Deliver its content with the speaking permission so Live cannot answer
            // from an earlier task question between quiet facts and a generic cue.
            if ((taskResponse || supportOnly) && CoreTaskVoice.bytes(responseFacts) <= LiveActivityContext.Continuation.MAX_SELF_CONTAINED_BYTES)
                return new LiveActivityContext(facts,
                        new LiveActivityContext.Continuation(UUID.fromString(response.get("id").getAsString()),
                                response.get("proactive").getAsBoolean() ? LiveActivityContext.Occasion.PROACTIVE : LiveActivityContext.Occasion.USER_REQUEST,
                                responseFacts, expires, false, true), expires);
            // Long replies retain bounded quiet facts followed by one speaking instruction.
            return new LiveActivityContext(responseFacts + "\n" + facts,
                    new LiveActivityContext.Continuation(UUID.fromString(response.get("id").getAsString()),
                            response.get("proactive").getAsBoolean() ? LiveActivityContext.Occasion.PROACTIVE : LiveActivityContext.Occasion.USER_REQUEST,
                            "Respond once to this confirmed coordination update using the current facts.", expires), expires);
        }
        var sensory = !TaskMemory.active(storage) && !java.util.Set.of("PAUSED", "FINISHING", "UNCERTAIN").contains(TaskMemory.phase(storage)) ? SensoryRemarks.context(storage, now) : null;
        if (sensory != null && CoreTaskVoice.bytes(facts + "\n" + sensory.facts()) <= 4000)
            return new LiveActivityContext(facts + "\n" + sensory.facts(), sensory.continuation(), sensory.refreshAt());
        return new LiveActivityContext(facts + "\nNo new task action is authorized. Answer direct questions; do not repeat earlier actions.", null);
    }
    @Override public void reset() { super.reset(); CoordinationMemory.clear(storage()); CoordinationMemory.remove(storage(), CoordinationDelivery.KEY); }
}
