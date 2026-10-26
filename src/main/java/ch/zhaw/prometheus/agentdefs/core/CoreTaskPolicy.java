package ch.zhaw.prometheus.agentdefs.core;

import java.time.Instant;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.behaviour.SensoryRemarks;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.task.*;
import jakarta.persistence.Entity;

/** Core opt-in extension; saved TaskPolicy instances retain their original contract. */
@Entity
public class CoreTaskPolicy extends TaskPolicy implements ExternalVoicePolicy {
    protected CoreTaskPolicy() {}
    public CoreTaskPolicy(Storage storage, String instructions) { super(storage, instructions); }
    @Override public boolean supportsLiveState(State state) { return state instanceof TaskState; }
    @Override public boolean livePendingAcknowledgements() { return !CoreTaskVoice.enabled(storage()); }
    @Override public String voiceInstructions() {
        if (!CoreTaskVoice.enabled(storage())) return super.voiceInstructions();
        return configuredInstructions() + "\nSIRA stands for " + ValerianCorePrompts.SIRA_NAME
                + ". The ZHAW lab researches multimodal digital agents and human-AI collaboration and develops PROMETHEUS. "
                + "\nConversational task configuration is implemented by the backend. Live owns the wording of replies. Answer ordinary questions directly using current facts. "
                + "Task changes and agreed actions require backend confirmation; wait silently for that confirmation, without echoing the question. "
                + "A proposal is not active. Explain its rules, first-action timing, action limit and session lifecycle before asking to start it. "
                + "Explain lifecycle in ordinary language: a session-bound task pauses when Live closes and needs explicit resume. Do not speak field names or the phrase session bound. "
                + "Accepting an ON_CUE proposal activates its waiting rule now; the action waits for the later cue. Ask whether to activate the proposal now, not whether to start later when told. Do not perform the agreed spoken action during activation. "
                + "Perform spoken task actions only for a current confirmed speaking occasion, once. Do not narrate silent gesture-only actions. "
                + "COMPLETED ends the task, not conversation. PAUSED preserves it and requires explicit resume. "
                + "Harmless acknowledgements do not stop, restart or revise tasks. Resolve yes only against the current invitation. "
                + "A motion request is not proof of physical completion. Missing evidence means unknown.";
    }
    @Override public boolean liveNonMutatingAcknowledgement(String text) {
        return CoreTaskVoice.enabled(storage()) && (CoreTaskVoice.acknowledgement(text) || CoreTaskVoice.assent(text) && !storage().containsKey(TaskMemory.DRAFT));
    }
    @Override public boolean liveResolvedControlReply(String text, ch.zhaw.prometheus.model.event.EventHistory history, ExternalSpeech owner) {
        // Establish which completed utterance the reply addresses; the action still interprets acceptance.
        return CoreTaskVoice.enabled(storage()) && CoreTaskVoice.assent(text) && CoreTaskVoice.prompt(storage(), history, owner) != null;
    }
    @Override public boolean liveSpeechCaptured(ch.zhaw.prometheus.model.event.Event event, ExternalSpeech owner,
            Instant started, Instant now, java.util.List<LiveSpeechPart> parts) {
        if (CoreTaskVoice.enabled(storage())) CoreTaskVoice.capturedPrompt(storage(), event, owner, started, parts);
        return false; // Stored in the native-speech transaction; this creates no playback cue or urgent refresh.
    }
    @Override public LiveActivityContext liveActivityContext() {
        if (!CoreTaskVoice.enabled(storage())) return SensoryRemarks.context(storage(), Instant.now());
        var task = CoreTaskVoice.context(storage());
        var pending = SensoryRemarks.context(storage(), Instant.now());
        if (pending == null || task.continuation() != null || CoreTaskVoice.bytes(task.facts() + pending.facts()) > 4000) return task;
        return new LiveActivityContext(task.facts() + pending.facts(), pending.continuation(), pending.refreshAt());
    }
    @Override public void reset() { super.reset(); SensoryRemarks.reset(storage()); CoreTaskVoice.clear(storage()); }
}
