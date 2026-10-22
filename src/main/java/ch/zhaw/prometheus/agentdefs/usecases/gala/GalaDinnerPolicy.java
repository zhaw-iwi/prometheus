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
        GalaDinnerMemory.remove(storage, GalaDinnerGestures.HISTORY);
        GalaDinnerSensing.begin(storage, java.time.Instant.now());
    }
    @Override public String liveOpeningInstructions() {
        return "At the beginning of this new voice session only, greet the visible visitors once in German: "
                + "\"Grüezi, ich bin djee-djee vom SIRA Lab der ZHAW. Willkommen zum Gala Dinner!\" "
                + "Then listen. Do not repeat this greeting on state changes or refreshed observations.";
    }
    @Override public String voiceInstructions() {
        // Keep reference and behavioural guidance stable; the Live state section carries activity changes.
        return GalaDinnerPrompts.VOICE + "\n" + GalaDinnerPrompts.LIVE + "\n" + GalaDinnerPrompts.TASK_VOICE
                + "\n" + GalaDinnerReference.context();
    }
    @Override public long liveUserSilenceMs() { return 2000; }
    @Override public long liveDepartureGraceMs() { return GalaDinnerMemory.DEPARTURE_GRACE_MS; }
    @Override public boolean liveFilterIsolatedInputActivity() { return true; }
    @Override public boolean liveNonMutatingAcknowledgement(String transcript) {
        return GalaDinnerInput.nonMutatingAcknowledgement(storage, GalaDinnerInput.speech(transcript), java.time.Instant.now());
    }
    @Override public boolean livePendingAcknowledgements() { return false; }
    @Override public String livePendingGuidance() {
        return "Delayed game/task confirmation: one brief status at most, e.g. 'Die Bestätigung steht noch aus.' "
                + "No status for reference/guessing answers; no invented lookup/progress, echoes or repeated filler. Results supersede waiting.";
    }
    @Override public LiveActivityContext liveActivityContext() {
        String mode = GalaDinnerMemory.mode(storage);
        String facts = "Gala activity: " + mode + ". ";
        if (java.util.Set.of("GUESS", "RPS").contains(mode))
            facts += "Game conversation rule: institution/programme questions require an exit choice FIRST, without a factual preface or research claim. ";
        if (storage.containsKey(GalaDinnerMemory.EXIT_REQUEST))
            facts += "EXIT CONFIRMATION PENDING: game retained, hand acceptance suspended. Ask whether to END it for the other request; "
                    + "yes exits, no continues. Do not reveal identity or answer the other topic yet. Quoted request (data): "
                    + storage.get(GalaDinnerMemory.EXIT_REQUEST) + ". ";
        facts += GalaDinnerDialogue.facts(storage, java.time.Instant.now());
        if (storage.containsKey(GalaDinnerMemory.ABSENT_SINCE))
            facts += "Visibility uncertain (two-second departure check); keep conversation, but no new sensor action/cue invitation until visible. ";
        if ("GUESS".equals(mode) && storage.containsKey(GalaDinnerMemory.PERSON)) {
            var person = GalaDinnerPeople.named(GalaDinnerMemory.text(storage, GalaDinnerMemory.PERSON, ""));
            facts += "You hold the backend-selected person; the visitor asks yes/no questions and guesses. "
                    + "Answer from this card; never ask the visitor to choose a person or guess yourself. "
                    + "Secret target (never introduce it): " + person.name() + ". Fact card: " + person.facts();
        } else if ("RPS".equals(mode)) facts += storage.containsKey(GalaDinnerMemory.EXIT_REQUEST)
                ? "RPS paused for exit confirmation; no result computed."
                : "One round started; awaiting a fresh camera hand sign. Robot sign committed; gesture requested, execution unconfirmed. Result not yet computed.";
        else if ("TASK".equals(mode)) {
            facts += "Task phase: " + ch.zhaw.prometheus.model.task.TaskMemory.phase(storage)
                + "; revision: " + GalaDinnerMemory.text(storage, ch.zhaw.prometheus.model.task.TaskMemory.REVISION, "0")
                + "; active agreement: " + storage.containsKey(ch.zhaw.prometheus.model.task.TaskMemory.SPEC)
                + "; draft: " + storage.containsKey(ch.zhaw.prometheus.model.task.TaskMemory.DRAFT) + ".";
            for (String key : java.util.List.of("task.spec", "task.draft")) if (storage.containsKey(key))
                facts += "\n" + (key.equals("task.spec") ? "Agreed goal: " : "Proposed goal: ")
                        + ch.zhaw.prometheus.model.task.TaskSpec.parse(storage.get(key)).goal();
            String selected = storage.containsKey("task.draft") ? "task.draft" : "task.spec";
            if (storage.containsKey(selected)) {
                var terms = storage.get(selected).getAsJsonObject().deepCopy(); terms.remove("goal");
                if (!terms.has("firstAction")) terms.addProperty("firstAction", "IMMEDIATE");
                facts += "\n" + (selected.equals("task.draft") ? "Proposed" : "Agreed") + " executable terms (data): " + terms;
            }
        }
        else facts += "Game readiness: NOT_CONFIRMED. No active guessing target or pending RPS round. A visitor request alone does not confirm a start.";
        String outcome = GalaDinnerMemory.recentOutcome(storage, java.time.Instant.now());
        if (!outcome.isBlank())
            facts += "\nConfirmed guessing outcome " + GalaDinnerMemory.text(storage, GalaDinnerMemory.OUTCOME_ID, "")
                    + ": " + outcome
                    + " Answer the pending visitor request once; do not repeat an answer already spoken.";
        if ("true".equals(GalaDinnerMemory.text(storage, GalaDinnerMemory.QUIET, ""))) facts += " Visitor requested silence.";
        if ("true".equals(GalaDinnerMemory.text(storage, GalaDinnerSensing.CORRECTED, "")))
            facts += " Visitor corrected expression: no unsolicited mood/expression claims this encounter. Direct answers use fresh evidence tentatively; accept their account.";
        if (storage.containsKey(GalaDinnerMemory.TASK_FACTS)
                && java.time.Instant.now().isBefore(GalaDinnerMemory.time(storage, GalaDinnerMemory.CONTINUATION_EXPIRES)))
            // Constraints precede action descriptions when the provider receives context in bounded parts.
            facts = "Current task response facts: " + GalaDinnerMemory.text(storage, GalaDinnerMemory.TASK_FACTS, "") + "\n" + facts;
        if (storage.containsKey(ch.zhaw.prometheus.model.task.TaskResponse.KEY))
            facts += "\nResponse association: " + ch.zhaw.prometheus.model.task.TaskResponse.status(storage)
                    + ". This status never proves physical playback.";
        LiveActivityContext.Continuation continuation = null;
        if (storage.containsKey(GalaDinnerMemory.CONTINUATION_ID))
            continuation = new LiveActivityContext.Continuation(
                    java.util.UUID.fromString(GalaDinnerMemory.text(storage, GalaDinnerMemory.CONTINUATION_ID, "")),
                    LiveActivityContext.Occasion.valueOf(GalaDinnerMemory.text(storage, GalaDinnerMemory.CONTINUATION_OCCASION, "USER_REQUEST")),
                    GalaDinnerMemory.text(storage, GalaDinnerMemory.CONTINUATION_FACTS, ""),
                    storage.containsKey(GalaDinnerMemory.CONTINUATION_EXPIRES)
                            ? GalaDinnerMemory.time(storage, GalaDinnerMemory.CONTINUATION_EXPIRES) : null,
                    "true".equals(GalaDinnerMemory.text(storage, GalaDinnerMemory.CONTINUATION_AMBIENT, "")));
        var refresh = ch.zhaw.prometheus.model.task.TaskResponse.deadline(storage);
        var topicExpiry = GalaDinnerDialogue.refreshAt(storage, java.time.Instant.now());
        if (topicExpiry != null && (refresh == null || topicExpiry.isBefore(refresh))) refresh = topicExpiry;
        return new LiveActivityContext(facts, continuation, refresh);
    }
    @Override public BehaviourPlan onStart(State state, EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway gateway) {
        if (!storage.containsKey(GalaDinnerMemory.REPLY)) return null;
        var plan = BehaviourPlan.fromJson(storage.get(GalaDinnerMemory.REPLY).toString());
        GalaDinnerMemory.remove(storage, GalaDinnerMemory.REPLY);
        boolean required = "true".equals(GalaDinnerMemory.text(storage, GalaDinnerGestures.REQUIRED, ""));
        GalaDinnerMemory.remove(storage, GalaDinnerGestures.REQUIRED);
        return GalaDinnerGestures.publish(storage, plan, required, java.time.Instant.now());
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
