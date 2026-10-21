package ch.zhaw.prometheus.agentdefs.usecases.gala;

import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.Policy;
import ch.zhaw.prometheus.model.policy.ExternalVoicePolicy;
import ch.zhaw.prometheus.model.policy.LiveActivityContext;
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
        // Keep reference and behavioural guidance stable; the Live state section carries activity changes.
        return GalaDinnerPrompts.VOICE + "\n" + GalaDinnerReference.context()
                + "\n" + GalaDinnerPrompts.LIVE + "\n" + GalaDinnerPrompts.TASK_VOICE;
    }
    @Override public long liveUserSilenceMs() { return 2000; }
    @Override public boolean livePendingAcknowledgements() { return false; }
    @Override public LiveActivityContext liveActivityContext() {
        String mode = GalaDinnerMemory.mode(storage);
        String facts = "Gala activity: " + mode + ". ";
        if ("GUESS".equals(mode) && storage.containsKey(GalaDinnerMemory.PERSON)) {
            var person = GalaDinnerPeople.named(GalaDinnerMemory.text(storage, GalaDinnerMemory.PERSON, ""));
            facts += "Secret target (never introduce it): " + person.name() + ". Fact card: " + person.facts();
        } else if ("RPS".equals(mode)) facts += "One round started; awaiting a fresh camera hand sign. Result not yet computed.";
        else if ("TASK".equals(mode)) {
            facts += "Task phase: " + ch.zhaw.prometheus.model.task.TaskMemory.phase(storage)
                + "; revision: " + GalaDinnerMemory.text(storage, ch.zhaw.prometheus.model.task.TaskMemory.REVISION, "0")
                + "; active agreement: " + storage.containsKey(ch.zhaw.prometheus.model.task.TaskMemory.SPEC)
                + "; draft: " + storage.containsKey(ch.zhaw.prometheus.model.task.TaskMemory.DRAFT) + ".";
            for (String key : java.util.List.of("task.spec", "task.draft")) if (storage.containsKey(key))
                facts += "\n" + (key.equals("task.spec") ? "Agreed goal: " : "Proposed goal: ")
                        + ch.zhaw.prometheus.model.task.TaskSpec.parse(storage.get(key)).goal();
        }
        else facts += "No active guessing target or pending RPS round.";
        if ("true".equals(GalaDinnerMemory.text(storage, GalaDinnerMemory.QUIET, ""))) facts += " Visitor requested silence.";
        LiveActivityContext.Continuation continuation = null;
        if (storage.containsKey(GalaDinnerMemory.CONTINUATION_ID))
            continuation = new LiveActivityContext.Continuation(
                    java.util.UUID.fromString(GalaDinnerMemory.text(storage, GalaDinnerMemory.CONTINUATION_ID, "")),
                    LiveActivityContext.Occasion.USER_REQUEST,
                    GalaDinnerMemory.text(storage, GalaDinnerMemory.CONTINUATION_FACTS, ""),
                    storage.containsKey(GalaDinnerMemory.CONTINUATION_EXPIRES)
                            ? GalaDinnerMemory.time(storage, GalaDinnerMemory.CONTINUATION_EXPIRES) : null);
        return new LiveActivityContext(facts, continuation);
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
