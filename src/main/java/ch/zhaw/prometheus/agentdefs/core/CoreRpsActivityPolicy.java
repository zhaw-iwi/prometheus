package ch.zhaw.prometheus.agentdefs.core;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.*;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.rps.RpsStorageKeys;
import ch.zhaw.prometheus.spi.LanguageModelGateway;
import jakarta.persistence.*;

/** Core result/replay graph; Live wording is based only on this persisted round. */
@Entity
public class CoreRpsActivityPolicy extends Policy implements ExternalVoicePolicy {
    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER) private Storage storage;
    protected CoreRpsActivityPolicy() {}
    public CoreRpsActivityPolicy(Storage storage) { this.storage = storage; }
    public Storage storage() { return storage; }
    @Override public boolean supportsLiveState(State state) { return state.getClass() == State.class || state instanceof Final; }
    @Override public String voiceInstructions() {
        return ValerianCorePrompts.OUTER_STATE + "\nPlay rock-scissor-paper. The backend alone selects signs, opens capture and computes results. "
                + "Use current round facts; never invent readiness, a detected sign, a result or another round. "
                + "Before INTRO there is NO selected round: briefly explain the game and ask the person to say ready. "
                + "Even after they say ready, do not count, announce a sign or invite a hand until updated facts confirm INTRO. Wait silently for that update. "
                + "In INTRO, ask the person to prepare/show one hand, count ONE, TWO, THREE once, then stop speaking. Do not say your chosen sign before the result. "
                + "SPOKEN means wait silently for playback coordination; do not repeat the countdown. WAITING means watch for the person's sign; do not count again. "
                + "RESULT means explain the confirmed winner and briefly offer another round. Yes can request a new round, but wait for its backend confirmation before counting. "
                + "ABORTED has no winner: briefly explain that the round did not finish and offer a fresh attempt. STOPPED means say goodbye, without another invitation. "
                + "A motion request and playback acknowledgement are not proof of physical execution or audibility. Never blame the person for sensing problems.";
    }
    @Override public boolean livePendingAcknowledgements() { return false; }
    @Override public String livePlaybackCompletionEventType() { return CoreRpsRound.PLAYED; }
    @Override public String liveSessionEndEventType() { return CoreRpsRound.ENDED; }
    @Override public boolean liveSpeechCaptured(Event event, ExternalSpeech owner, Instant started, Instant now, List<LiveSpeechPart> parts) {
        return CoreRpsRound.captured(storage, event, owner, started, now, parts);
    }
    @Override public LiveActivityContext liveActivityContext() {
        var r = CoreRpsRound.round(storage); String phase = CoreRpsRound.text(r, "phase"); var now = Instant.now();
        String facts = "Confirmed RPS phase: " + phase + ". " + switch (phase) {
            case "INTRO" -> "A new round is selected, but no motion/capture has started. Prepare the hand and count one, two, three once.";
            case "SPOKEN" -> "Countdown captured; waiting for client playback settling. No hand gesture or capture yet. Stay silent.";
            case "WAITING" -> "The chosen hand gesture was requested. Capture is open for this round; no accepted human sign or result yet.";
            case "RESULT" -> "Confirmed result: " + resultFacts() + ". Explain the result in English and offer another round; do not start it yourself.";
            case "ABORTED" -> "Preparation or the voice session ended before a result. No winner. A new confirmed round is required.";
            case "STOPPED" -> "The user ended the game. No further round is authorized.";
            default -> "No round is ready. Wait for an explicit start request.";
        };
        LiveActivityContext.Continuation continuation = null; Instant expiry = null;
        if (Set.of("INTRO", "RESULT", "ABORTED", "STOPPED").contains(phase)) {
            expiry = Instant.parse(CoreRpsRound.text(r, "INTRO".equals(phase) ? "deadline" : "resultAt"));
            if (!"INTRO".equals(phase)) expiry = expiry.plusSeconds(30);
            if (now.isBefore(expiry)) continuation = new LiveActivityContext.Continuation(
                    UUID.nameUUIDFromBytes((CoreRpsRound.text(r, "id") + ":" + phase).getBytes(StandardCharsets.UTF_8)),
                    Set.of("RESULT", "ABORTED").contains(phase) ? LiveActivityContext.Occasion.PROACTIVE : LiveActivityContext.Occasion.USER_REQUEST,
                    "One confirmed RPS update is ready. Speak once from its current facts in your own words.", expiry);
        }
        var cue = CoreRpsRound.cue(storage, now);
        if (cue != null) expiry = cue.expiresAt();
        return new LiveActivityContext(facts, continuation, expiry != null && now.isBefore(expiry) ? expiry : null, cue);
    }
    @Override public BehaviourPlan onStart(State state, EventHistory events, PromptMessageAssembler assembler, LanguageModelGateway gateway) {
        if (CoreRpsRound.phase(storage).isEmpty()) return startPolicy().onStart(state, events, assembler, gateway);
        if (!storage.containsKey(CoreRpsRound.REPLY)) return null;
        storage.remove(CoreRpsRound.REPLY);
        var r = CoreRpsRound.round(storage); String phase = CoreRpsRound.text(r, "phase"); BehaviourPlan plan;
        switch (phase) {
            case "WAITING" -> {
                plan = new CoreRpsRevealPolicy(storage).onStart(state, events, assembler, gateway);
                if (CoreRpsRound.live(r)) plan.getMotion().getAsJsonObject().remove("timing");
            }
            case "RESULT" -> plan = new CoreRpsResultPolicy(storage).onStart(state, events, assembler, gateway);
            case "ABORTED" -> plan = BehaviourPlan.speechOnly("The round did not finish. Shall we try again?");
            case "STOPPED" -> plan = CoreRpsRound.live(r) ? new BehaviourPlan() :
                    new PromptPolicy(RockScissorPaper.PROMPT_FINAL, ValerianCorePrompts.FINAL_STARTER, PromptPolicy.DEFAULT_SUMMARISE_PROMPT).onStart(state, events, assembler, gateway);
            default -> plan = new BehaviourPlan();
        }
        if (plan == null) plan = new BehaviourPlan();
        if (CoreRpsRound.live(r)) plan.setSpeech(null);
        var display = CoreRpsRound.display(storage);
        if (plan.getDisplay() != null && plan.getDisplay().isJsonObject()) plan.getDisplay().getAsJsonObject().entrySet().forEach(e -> display.add(e.getKey(), e.getValue()));
        plan.setDisplay(display); return plan;
    }
    @Override public BehaviourPlan onRespond(State state, EventHistory events, PromptMessageAssembler assembler, LanguageModelGateway gateway) {
        if (CoreRpsRound.phase(storage).isEmpty()) return startPolicy().onRespond(state, events, assembler, gateway);
        if (!events.isEmpty() && Event.TYPE_USER_UTTERANCE.equals(events.toList().getLast().getType())
                && "RESULT".equals(CoreRpsRound.phase(storage)))
            return new CoreRpsResultPolicy(storage).onRespond(state, events, assembler, gateway);
        return null;
    }
    @Override public String describe() { return voiceInstructions(); }
    @Override public String summarise(State state, EventHistory events, PromptMessageAssembler assembler, LanguageModelGateway gateway) { return liveActivityContext().facts(); }
    @Override public void reset() { CoreRpsRound.reset(storage); }
    private com.google.gson.JsonObject resultFacts() {
        var facts = storage.get(RpsStorageKeys.LAST_ROUND).getAsJsonObject().deepCopy();
        facts.remove("reason"); // Legacy diagnostic reason is German; canonical signs/winner are sufficient.
        return facts;
    }
    private PromptPolicy startPolicy() { return new PromptPolicy(ValerianCorePrompts.OUTER_STATE + "\n" + RockScissorPaper.PROMPT_START,
            RockScissorPaper.PROMPT_STARTER, PromptPolicy.DEFAULT_SUMMARISE_PROMPT); }
}
