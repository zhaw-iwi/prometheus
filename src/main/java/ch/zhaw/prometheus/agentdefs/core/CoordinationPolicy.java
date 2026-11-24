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
        return configuredInstructions() + ch.zhaw.prometheus.model.coordination.CoordinationTaskContract.VOICE_GUIDANCE
                + ch.zhaw.prometheus.model.coordination.CoordinationSocialContract.VOICE_GUIDANCE
                + "\nBackend owns task control. "
                + "Answer ordinary/capability/perception questions from current facts. "
                + "Name fresh supplied hand signs directly. "
                + "Use supplied evidence; explain unknown/stale readings. Never claim camera access or deny supported sensors. "
                + "All task proposals, transitions, actions, edits and recovery require backend confirmation. "
                + "Use 'you' for human duties/preparation/consent, 'I' for agent duties; never swap. "
                + "Wait silently for confirmed task/support facts; say the requested response once. "
                + "Never complete a partial task request. "
                + "Describe proposals only. Contribute solely on the current identified occasion. "
                + "During preparation/proposal, never preview task content, including sample jokes. Say 'the line you supplied' only for user-supplied text. "
                + "Before confirmed control facts, never say checking, waiting for backend, done, started, restarted or returned. "
                + "Omit implementation details. Answer unrelated questions and explicitly requested capability examples. "
                + "Conversation preserves history and the agent. "
                + "Proposals need acceptance; preparation needs observed evidence, never verbal claims. "
                + "Review ready proposals in at most 55 words, preserving distinct terms, preparation, limits and acceptance question. "
                + "Preparation holds until proposal/session changes. READY needs acceptance, not repeated preparation; execution needs a fresh post-acceptance cue. "
                + "Each dialogue-task step needs backend confirmation. "
                + "ON_CUE needs a fresh cue; recognition failure is no cue. Explain recognition/action limits. "
                + "Reflection reports evidence/user experience, not learning, feelings or physical proof. Exhaustion/cancellation is not success; revisions need agreement. "
                + "FINISHING permits only the final identified contribution, then silence until delivery is confirmed. "
                + "Append no reflection, acknowledgement, invitation or aside. "
                + "Speak agreed exact sentences verbatim, without preface or additions. "
                + "UNCERTAIN is unconfirmed delivery: never repeat it automatically or claim success. "
                + "Automatic perception remarks need a current backend occasion; tasks take priority. "
                + (storage().containsKey(SensoryRemarks.CORRECTION) ? "The user declined/corrected facial interpretation; respect that correction. " : "");
    }
    @Override public String describe() { return voiceInstructions() + "\n" + CoordinationMemory.facts(storage()); }
    @Override public String summarise(ch.zhaw.prometheus.model.State state, EventHistory events, PromptMessageAssembler assembler,
            ch.zhaw.prometheus.spi.LanguageModelGateway gateway) { return CoordinationMemory.facts(storage()); }
    @Override public void beginLiveConversation(EventHistory history) {
        super.beginLiveConversation(history);
        // Local compatibility on claim, before the startup projection; no graph/history migration.
        CoordinationMemory.remove(storage(), "live.speechApproval", "live.committedSpeech");
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
        var source = event.speechProvenance();
        if (source == null || source.segmentId() == null || !source.complete() || !source.realized() || Boolean.FALSE.equals(source.playbackConfirmed())
                || owner == null || !owner.sessionId().equals(source.sessionId()) || !owner.epoch().equals(source.epoch())
                || started == null || started.isAfter(now)) return false;
        var occasion = liveActivityContext().continuation();
        if (storage().containsKey(SensoryRemarks.PENDING)) {
            var pending = storage().get(SensoryRemarks.PENDING).getAsJsonObject();
            if (occasion != null && occasion.id().toString().equals(pending.get("id").getAsString())
                    && pending.has("created") && started.isAfter(Instant.parse(pending.get("created").getAsString()))) {
                SensoryRemarks.cancel(storage());
                return true; // Ambient speech cannot consume a task response or establish proposal consent.
            }
        }
        if (storage().containsKey(ch.zhaw.prometheus.model.coordination.CoordinationSupport.LIVE_HELP)) {
            var help = storage().get(ch.zhaw.prometheus.model.coordination.CoordinationSupport.LIVE_HELP).getAsJsonObject().deepCopy();
            if (help.has("segment") && source.segmentId().equals(help.get("segment").getAsString())) return false;
            if (!help.has("consumed") && occasion != null && occasion.id().toString().equals(help.get("id").getAsString())
                    && owner.sessionId().toString().equals(help.get("session").getAsString())
                    && owner.epoch().toString().equals(help.get("epoch").getAsString())
                    && started.isAfter(Instant.parse(help.get("at").getAsString()))) {
                help.addProperty("consumed", true); help.addProperty("segment", source.segmentId());
                storage().put(ch.zhaw.prometheus.model.coordination.CoordinationSupport.LIVE_HELP, help);
                return true; // Help cannot consume a task response or create proposal-consent evidence.
            }
        }
        boolean supportOnly = storage().containsKey(CoreTaskVoice.RESPONSE)
                && storage().get(CoreTaskVoice.RESPONSE).getAsJsonObject().has("supportOnly")
                && storage().get(CoreTaskVoice.RESPONSE).getAsJsonObject().get("supportOnly").getAsBoolean();
        boolean changed = !supportOnly && CoreTaskVoice.capturedPrompt(storage(), event, owner, started, parts, CoordinationMemory.DRAFT);
        if (!storage().containsKey(CoreTaskVoice.RESPONSE)) return changed;
        var response = storage().get(CoreTaskVoice.RESPONSE).getAsJsonObject().deepCopy();
        // Task content has a separate semantic association. An unrelated complete segment cannot consume it.
        if (storage().containsKey(TaskResponse.KEY) && response.get("id").equals(storage().get(TaskResponse.KEY).getAsJsonObject().get("id"))) return changed;
        if (source == null || !source.complete() || !source.realized() || owner == null
                || !owner.sessionId().equals(source.sessionId()) || !owner.epoch().equals(source.epoch())
                || !response.has("session") || !owner.sessionId().toString().equals(response.get("session").getAsString())
                || !owner.epoch().toString().equals(response.get("epoch").getAsString())
                || !TaskMemory.text(storage(), TaskMemory.REVISION, "0").equals(response.get("revision").getAsString())
                || started == null || started.isAfter(now)
                || (!(started.isAfter(Instant.parse(response.get("created").getAsString()))
                        || (supportOnly || response.has("proposalReview") && response.get("proposalReview").getAsBoolean())
                            && CoreTaskVoice.freshEnding(event, parts, Instant.parse(response.get("created").getAsString()), now))
                    || !now.isBefore(Instant.parse(response.get("expires").getAsString())))) return changed;
        response.addProperty("consumed", true); storage().put(CoreTaskVoice.RESPONSE, response); return true;
    }
    @Override public LiveActivityContext liveActivityContext() {
        var context = CoordinationDelivery.withPlayback(storage(),
                ch.zhaw.prometheus.model.coordination.CoordinationSupport.withHelp(storage(), speechContext(), Instant.now()));
        var continuation = context.continuation();
        var response = storage().containsKey(TaskResponse.KEY) ? storage().get(TaskResponse.KEY).getAsJsonObject() : null;
        boolean contribution = continuation != null && response != null
                && continuation.responseId().toString().equals(response.get("id").getAsString())
                && (!response.has("purpose") || !"ARMING".equals(response.get("purpose").getAsString()));
        // The persisted occasion already binds revision/run and phase preconditions. Its identity
        // changes on control transitions, but not on unrelated sensory/context updates.
        if (continuation != null && !continuation.interruptible() && !contribution)
            return new LiveActivityContext(context.facts(), continuation.whileCurrent(), context.refreshAt(), context.playbackCue());
        return context;
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
        String facts = controlOnly || contributionOnly || supportOnly || pendingHelp ? CoordinationMemory.controls(storage) : CoordinationMemory.voiceFacts(storage);
        if (current) {
            Instant expires = Instant.parse(response.get("expires").getAsString());
            String responseFacts = response.get("facts").getAsString();
            // Support may be paraphrased, but its control facts must remain intact.
            if (supportOnly) responseFacts = "Convey these confirmed facts once, preserving their meaning: " + new com.google.gson.JsonPrimitive(responseFacts);
            String review = "";
            boolean navigationReview = false;
            if (response.has("proposalReview") && response.get("proposalReview").getAsBoolean()
                    && CoordinationMemory.ready(storage) && !ch.zhaw.prometheus.model.coordination.CoordinationSupport.blockedDraft(storage)) {
                var proposal = CoordinationMemory.agreement(storage, CoordinationMemory.DRAFT);
                review = ch.zhaw.prometheus.model.coordination.CoordinationTaskContract.liveProposalReview(proposal);
                if (CoreTaskVoice.bytes(review + "\n" + CoordinationMemory.controls(storage)) > 3900) {
                    navigationReview = true;
                    review = "The proposal is saved and preparation is complete. Review the full roles, success terms and conditions in the cockpit task details before accepting. "
                            + CoordinationMemory.voiceNavigation(storage) + " This proposal is not active. Shall we start?";
                }
                if (!review.isBlank() && (proposal.reactions() == null
                        || CoreTaskVoice.bytes(review) > LiveActivityContext.Continuation.MAX_FACTS_BYTES)) facts = CoordinationMemory.controls(storage);
            }
            if (!review.isBlank()) {
                boolean longReview = CoreTaskVoice.bytes(review) > LiveActivityContext.Continuation.MAX_FACTS_BYTES;
                return new LiveActivityContext(longReview ? review + "\n" + facts : facts,
                        new LiveActivityContext.Continuation(UUID.fromString(response.get("id").getAsString()),
                                LiveActivityContext.Occasion.USER_REQUEST, navigationReview
                                    ? "Explain the saved proposal status; ask the person to review its full terms in the cockpit before accepting. Do not invent omitted details."
                                    : longReview ? "Summarize the proposal in at most 55 words, combining duplicated facts. Include every distinct term, preparation status and acceptance question." : review,
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
            if (CoreTaskVoice.bytes(responseFacts + "\n" + facts) > 4000) facts = CoordinationMemory.controls(storage);
            return new LiveActivityContext(responseFacts + "\n" + facts,
                    new LiveActivityContext.Continuation(UUID.fromString(response.get("id").getAsString()),
                            response.get("proactive").getAsBoolean() ? LiveActivityContext.Occasion.PROACTIVE : LiveActivityContext.Occasion.USER_REQUEST,
                            "Respond once to this confirmed coordination update using the current facts.", expires), expires);
        }
        var sensory = !TaskMemory.active(storage) && !java.util.Set.of("PAUSED", "FINISHING", "UNCERTAIN").contains(TaskMemory.phase(storage)) ? SensoryRemarks.context(storage, now) : null;
        if (sensory != null && CoreTaskVoice.bytes(facts + "\n" + sensory.facts()) <= 4000)
            return new LiveActivityContext(facts + "\n" + sensory.facts(), sensory.continuation(), sensory.refreshAt());
        return new LiveActivityContext(facts + "\nNo speech or task action is authorized by this update. Wait silently for a user turn or a confirmed speaking occasion.", null);
    }
    @Override public void reset() { super.reset(); CoordinationMemory.clear(storage()); CoordinationMemory.remove(storage(), CoordinationDelivery.KEY); }
}
