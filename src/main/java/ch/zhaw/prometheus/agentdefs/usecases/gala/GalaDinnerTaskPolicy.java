package ch.zhaw.prometheus.agentdefs.usecases.gala;

import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.ExternalVoicePolicy;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.model.task.TaskPolicy;
import ch.zhaw.prometheus.spi.LanguageModelGateway;
import jakarta.persistence.Entity;

/** Reuses task readiness with Gala's Live-owned wording and encounter lifecycle. */
@Entity
public class GalaDinnerTaskPolicy extends TaskPolicy implements ExternalVoicePolicy {
    protected GalaDinnerTaskPolicy() {}
    public GalaDinnerTaskPolicy(Storage storage) { super(storage, GalaDinnerPrompts.TASK_VOICE); }
    public static final String RESPONSE_UNCERTAIN = "int.gala.task_response_uncertain";
    @Override public String liveResponseFailureEventType() { return RESPONSE_UNCERTAIN; }
    private GalaDinnerPolicy reception() { return new GalaDinnerPolicy(storage()); }
    @Override public boolean supportsLiveState(State state) { return reception().supportsLiveState(state); }
    @Override public String liveSessionEndEventType() { return GalaDinnerPolicy.LIVE_ENDED; }
    @Override public int liveHistoryStartIndex() { return reception().liveHistoryStartIndex(); }
    @Override public void beginLiveConversation(EventHistory history) { reception().beginLiveConversation(history); }
    @Override public String liveOpeningInstructions() { return reception().liveOpeningInstructions(); }
    @Override public String voiceInstructions() { return reception().voiceInstructions(); }
    @Override public long liveUserSilenceMs() { return reception().liveUserSilenceMs(); }
    @Override public long liveDepartureGraceMs() { return reception().liveDepartureGraceMs(); }
    @Override public boolean liveFilterIsolatedInputActivity() { return reception().liveFilterIsolatedInputActivity(); }
    @Override public boolean livePendingAcknowledgements() { return reception().livePendingAcknowledgements(); }
    @Override public String livePendingGuidance() { return reception().livePendingGuidance(); }
    @Override public ch.zhaw.prometheus.model.policy.LiveActivityContext liveActivityContext() { return reception().liveActivityContext(); }
    @Override public BehaviourPlan onStart(State state, EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway gateway) { return reception().onStart(state, events, assembler, gateway); }
}
