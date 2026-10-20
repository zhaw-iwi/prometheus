package ch.zhaw.prometheus.agentdefs.usecases.gala;

import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.Policy;
import ch.zhaw.prometheus.model.policy.ExternalVoicePolicy;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.spi.LanguageModelGateway;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;

/** Publish a validated response once; polling/generate never repeats a game action. */
@Entity
public class GalaDinnerPolicy extends Policy implements ExternalVoicePolicy {
    public static final String LIVE_ENDED = "sys.gala.live_closed";
    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Storage storage;
    protected GalaDinnerPolicy() {}
    public GalaDinnerPolicy(Storage storage) { this.storage = storage; }
    Storage storage() { return storage; }
    @Override public boolean supportsLiveState(State state) { return state.getClass() == GalaDinnerState.class; }
    @Override public String liveSessionEndEventType() { return LIVE_ENDED; }
    @Override public int liveHistoryStartIndex() {
        return Integer.parseInt(GalaDinnerMemory.text(storage, GalaDinnerMemory.SESSION, "0"));
    }
    @Override public void beginLiveConversation(EventHistory history) {
        if (!GalaDinnerMemory.present(storage, java.time.Instant.now())
                || !"RECEPTION".equals(GalaDinnerMemory.mode(storage)))
            throw new IllegalArgumentException("A fresh occupied reception observation is required");
        GalaDinnerMemory.put(storage, GalaDinnerMemory.SESSION, Integer.toString(history.toList().size()));
    }
    @Override public String liveOpeningInstructions() {
        return "At the beginning of this new voice session only, greet the visible visitors once in German: "
                + "\"Grüezi, ich bin djee-djee vom SIRA Lab der ZHAW. Willkommen zum Gala Dinner!\" "
                + "Then listen. Do not repeat this greeting on state changes or refreshed observations.";
    }
    @Override public String voiceInstructions() {
        return describe() + "\n" + GalaDinnerPrompts.LIVE;
    }
    @Override public BehaviourPlan onStart(State state, EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway gateway) {
        if (!storage.containsKey(GalaDinnerMemory.REPLY)) return null;
        var plan = BehaviourPlan.fromJson(storage.get(GalaDinnerMemory.REPLY).toString());
        GalaDinnerMemory.remove(storage, GalaDinnerMemory.REPLY);
        return plan;
    }
    @Override public BehaviourPlan onRespond(State state, EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway gateway) { return null; }
    @Override public String describe() {
        return GalaDinnerPrompts.VOICE + "\n" + GalaDinnerReference.context()
                + "\nCurrent mode: " + GalaDinnerMemory.mode(storage);
    }
    @Override public String summarise(State state, EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway gateway) { return "Gala mode: " + GalaDinnerMemory.mode(storage); }
}
