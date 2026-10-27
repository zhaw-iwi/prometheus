package ch.zhaw.prometheus.model.guessing;

import java.time.Instant;
import java.util.*;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.spi.LanguageModelGateway;
import jakarta.persistence.*;

@Entity
public class GuessingPolicy extends PromptPolicy implements ExternalVoicePolicy {
    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER) private Storage guessingStorage;
    public static final String RULES = """
        The backend owns the selected role, secret, reveal and new-round decisions.
        Use only the current persisted game record; never independently choose or change a secret.
        USER_GUESSES: you chose the item, the visitor asks yes/no questions. Never reverse these roles.
        AGENT_GUESSES: the visitor chooses; you ask yes/no questions. Their unspoken secret is unknown to you.
        In AGENT_GUESSES, use the visitor's answers and volunteered clues to make a tentative guess when sufficient or requested, then ask whether it is correct. A tentative guess needs no backend secret card; the visitor confirms it. Do not say you have not specified a fact about their item. Never claim a guess is confirmed before the backend marks SOLVED.
        The following fact-card and question-clarification rules apply only in USER_GUESSES:
        Answer an ordinary binary question directly using the current fact card; do not echo it or acknowledge waiting.
        If a clear question asks for an unspecified fact, say briefly that this fact is not specified; do not ask them to rephrase a perfectly clear question.
        Unspecified is NOT false. For example, if no colour was assigned, 'Is it blue?' requires 'I have not specified a colour', never 'No, not blue'. This exception takes priority over the usual yes/no game format.
        If the wording is ambiguous or not a yes/no question, request one short clarification.
        A comparative such as 'Is it bigger?' needs a comparison reference from the conversation. If none was given, ask 'Bigger than what?' rather than silently changing the question to 'Is it big?'.
        In both roles:
        Missing facts never authorize a new secret, a reveal or an exit. Do not invent individual details or memories.
        For a claimed correct guess, give-up or new-round request, wait for the backend phase to confirm it before claiming success, naming the secret or starting again.
        REVEALED/SOLVED: name the confirmed item once, if known, and optionally ask whether to play another round. Do not reverse roles.
        A harmless acknowledgment never ends or restarts an active round. Explicit stop still ends the interaction.
        """;
    protected GuessingPolicy() {}
    public GuessingPolicy(Storage storage, String fullInstructions, String starter, String nonverbal) {
        super(fullInstructions + "\n" + RULES + "\nCurrent private game record: ${guessing_round}", starter,
                DEFAULT_SUMMARISE_PROMPT, storage, List.of(GuessingMemory.KEY), PromptValueShape.OBJECT);
        guessingStorage = storage; setNonVerbalPlanPrompt(nonverbal); setNonVerbalGesturePrompt(DEFAULT_NONVERBAL_GESTURE_PROMPT);
    }
    public Storage storage() { return guessingStorage; }
    // The factory supplies the complete existing outer/inner persona once.
    @Override public Policy withOuterPolicy(Policy outer) { return this; }
    @Override public boolean supportsLiveState(State state) { return state.getClass() == State.class || state instanceof Final; }
    @Override public String voiceInstructions() { return describe(); }
    @Override public boolean livePendingAcknowledgements() { return false; }
    @Override public boolean liveNonMutatingAcknowledgement(String text) { return GuessingMemory.active(storage()) && GuessingMemory.harmless(text); }
    @Override public boolean liveResolvedControlReply(String text, EventHistory history, ExternalSpeech owner) {
        return text.trim().toLowerCase(Locale.ROOT).matches("(yes|no|yes please|no thanks)[.!]?") && GuessingMemory.currentQuestion(history, owner);
    }
    @Override public LiveActivityContext liveActivityContext() {
        var record = GuessingMemory.record(storage()); var updated = GuessingMemory.text(record, "updated");
        var expiry = updated.isEmpty() ? null : Instant.parse(updated).plusSeconds(40);
        boolean pending = expiry != null && Instant.now().isBefore(expiry) && !"KEEP".equals(GuessingMemory.text(record, "operation"));
        return new LiveActivityContext(GuessingMemory.facts(storage()), pending ? new LiveActivityContext.Continuation(
                UUID.fromString(GuessingMemory.text(record, "responseId")), LiveActivityContext.Occasion.USER_REQUEST,
                "One game update is confirmed. Reply once from the current role/phase, in your own words.", expiry) : null,
                pending ? expiry : null);
    }
    @Override public BehaviourPlan onStart(State state, EventHistory events, PromptMessageAssembler assembler, LanguageModelGateway gateway, OutputProfile profile) {
        var record = GuessingMemory.record(storage());
        if (record.has("live") && record.get("live").getAsBoolean()) return null;
        return events.isEmpty() || "START".equals(GuessingMemory.text(record, "operation"))
                ? super.onStart(state, events, assembler, gateway, profile) : super.onRespond(state, events, assembler, gateway, profile);
    }
    @Override public void reset() { GuessingMemory.reset(storage()); }
}
