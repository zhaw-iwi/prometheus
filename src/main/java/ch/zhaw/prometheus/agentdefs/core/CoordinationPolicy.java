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
import jakarta.persistence.Entity;

/** The same persona in every phase; backend agreements and sensory occasions remain authoritative. */
@Entity
public class CoordinationPolicy extends CoreTaskPolicy {
    protected CoordinationPolicy() {}
    public CoordinationPolicy(Storage storage, String instructions) { super(storage, instructions); }
    @Override public long liveUserSilenceMs() { return 2000; }
    @Override public boolean hasDraft() { return storage().containsKey(CoordinationMemory.DRAFT); }
    @Override public String statusPhase() {
        String stage = CoordinationMemory.stage(storage());
        return "EXECUTION".equals(stage) ? TaskMemory.phase(storage()) : stage;
    }
    @Override public String voiceInstructions() {
        return configuredInstructions() + "\nThe backend coordinates both conversational and sensor-rule tasks. "
                + "Answer ordinary conversation and capability/perception questions naturally from current facts. "
                + "Task proposals, preparation, acceptance, actions, completion and revisions require backend confirmation. "
                + "Wait silently for confirmed task updates, then phrase the requested response once. "
                + "A complete proposal still needs acceptance. Required preparation is verified from observations, never a verbal claim. "
                + "Explain what remains unresolved or unrecognised. Successful preparation authorizes no task action by itself. "
                + "In an active dialogue task, wait for the backend's confirmed next contribution before delivering that task step. "
                + "ON_CUE activation waits for a fresh subsequent cue. Describe recognition limits and action bounds in ordinary language. "
                + "A reflection is an account of evidence and the person's experience, not proof of learning, feelings or physical execution. "
                + "An exhausted budget or cancellation is not achieved success. Revisions need a new agreement. "
                + "Automatic perception remarks require a current backend occasion; task work takes priority. "
                + (storage().containsKey(SensoryRemarks.CORRECTION) ? "The user declined/corrected facial interpretation; respect that correction. " : "");
    }
    @Override public String describe() { return voiceInstructions() + "\n" + CoordinationMemory.facts(storage()); }
    @Override public String summarise(ch.zhaw.prometheus.model.State state, EventHistory events, PromptMessageAssembler assembler,
            ch.zhaw.prometheus.spi.LanguageModelGateway gateway) { return CoordinationMemory.facts(storage()); }
    @Override public void beginLiveConversation(EventHistory history) {
        super.beginLiveConversation(history);
        SensoryRemarks.cancel(storage());
        if (hasDraft()) {
            CoreTaskVoice.clear(storage());
            CoordinationMemory.resetPreparation(storage(), Instant.now()); CoordinationMemory.revise(storage());
        }
    }
    @Override public boolean liveNonMutatingAcknowledgement(String text) {
        return CoreTaskVoice.acknowledgement(text) || CoreTaskVoice.assent(text) && !hasDraft();
    }
    @Override public boolean liveResolvedControlReply(String text, EventHistory history, ExternalSpeech owner) {
        return CoreTaskVoice.assent(text) && CoreTaskVoice.prompt(storage(), history, owner, CoordinationMemory.DRAFT) != null;
    }
    @Override public boolean liveSpeechCaptured(Event event, ExternalSpeech owner, Instant started, Instant now, List<LiveSpeechPart> parts) {
        CoreTaskVoice.capturedPrompt(storage(), event, owner, started, parts, CoordinationMemory.DRAFT); return false;
    }
    @Override public LiveActivityContext liveActivityContext() {
        var storage = storage(); Instant now = Instant.now();
        var response = storage.containsKey(CoreTaskVoice.RESPONSE) ? storage.get(CoreTaskVoice.RESPONSE).getAsJsonObject() : null;
        boolean current = response != null && response.get("revision").getAsString().equals(TaskMemory.text(storage, TaskMemory.REVISION, "0"))
                && now.isBefore(Instant.parse(response.get("expires").getAsString()));
        String facts = CoordinationMemory.facts(storage);
        if (current) {
            Instant expires = Instant.parse(response.get("expires").getAsString());
            return new LiveActivityContext(facts + "\n" + response.get("facts").getAsString(),
                    new LiveActivityContext.Continuation(UUID.fromString(response.get("id").getAsString()),
                            response.get("proactive").getAsBoolean() ? LiveActivityContext.Occasion.PROACTIVE : LiveActivityContext.Occasion.USER_REQUEST,
                            "Respond once to this confirmed coordination update using the current facts.", expires), expires);
        }
        var sensory = !TaskMemory.active(storage) && !"PAUSED".equals(TaskMemory.phase(storage)) ? SensoryRemarks.context(storage, now) : null;
        if (sensory != null && CoreTaskVoice.bytes(facts + "\n" + sensory.facts()) <= 4000)
            return new LiveActivityContext(facts + "\n" + sensory.facts(), sensory.continuation(), sensory.refreshAt());
        return new LiveActivityContext(facts + "\nNo new task action is authorized. Answer direct questions; do not repeat earlier actions.", null);
    }
    @Override public void reset() { super.reset(); CoordinationMemory.clear(storage()); }
}
