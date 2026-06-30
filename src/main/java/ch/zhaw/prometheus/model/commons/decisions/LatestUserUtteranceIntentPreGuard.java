package ch.zhaw.prometheus.model.commons.decisions;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

import ch.zhaw.prometheus.model.Decision;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.NoOpPolicy;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Entity
public class LatestUserUtteranceIntentPreGuard extends Decision {
    public enum Mode {
        SESSION_CLOSURE,
        TASK_OUTCOME
    }

    @Enumerated(EnumType.STRING)
    private Mode mode;

    protected LatestUserUtteranceIntentPreGuard() {
    }

    public LatestUserUtteranceIntentPreGuard(Mode mode) {
        super(new NoOpPolicy());
        this.mode = mode == null ? Mode.TASK_OUTCOME : mode;
    }

    @Override
    public boolean decide(EventHistory events, PolicyRuntime runtime) {
        Event latest = latestEvent(events);
        if (latest == null || !Event.TYPE_USER_UTTERANCE.equals(latest.getType())
                || !Event.ACTOR_USER.equals(latest.getActor())) {
            return false;
        }
        String utterance = normalize(latest.getPayload());
        if (utterance.isBlank()) {
            return false;
        }
        if (hasSessionClosureIntent(utterance)) {
            return true;
        }
        if (this.mode == Mode.SESSION_CLOSURE) {
            return false;
        }
        return hasTaskOutcomeIntent(utterance, latestAssistantSpeech(events));
    }

    private static Event latestEvent(EventHistory events) {
        if (events == null || events.isEmpty()) {
            return null;
        }
        List<Event> source = events.toList();
        return source.get(source.size() - 1);
    }

    private static boolean hasSessionClosureIntent(String utterance) {
        return containsAny(utterance,
                "bye",
                "goodbye",
                "stop",
                "quit",
                "cancel",
                "end this",
                "end the conversation",
                "leave me alone",
                "go away",
                "no more",
                "that's all",
                "that is all",
                "we are done",
                "i am done",
                "enough now");
    }

    private static boolean hasTaskOutcomeIntent(String utterance, String previousAssistantSpeech) {
        if (hasClosingConfirmation(utterance) && assistantAskedClosingQuestion(previousAssistantSpeech)) {
            return true;
        }
        return containsAny(utterance,
                "let's go",
                "lets go",
                "i will go",
                "i'll go",
                "i agree to go",
                "i will attend",
                "i'll attend",
                "i guessed it",
                "that was correct",
                "i got it right",
                "that's my final answer",
                "that is my final answer");
    }

    private static boolean hasClosingConfirmation(String utterance) {
        String compact = " " + utterance + " ";
        if (utterance.length() <= 28 && containsAny(compact,
                " yes ",
                " yep ",
                " yeah ",
                " ok ",
                " okay ",
                " fine ",
                " sure ",
                " agreed ",
                " correct ",
                " exactly ",
                " right ")) {
            return true;
        }
        return containsAny(utterance,
                "that works",
                "let us do that",
                "let's do that",
                "lets do that",
                "leave it there",
                "hold it that way",
                "keep it that way",
                "that is okay",
                "that's okay");
    }

    private static boolean assistantAskedClosingQuestion(String assistantSpeech) {
        String speech = normalize(assistantSpeech);
        if (speech.isBlank()) {
            return false;
        }
        return containsAny(speech,
                "leave it there",
                "hold it that way",
                "hold it there",
                "keep it that way",
                "keep it there",
                "stop here",
                "end here",
                "that way",
                "shall we",
                "should we",
                "would you like me to hold",
                "should i hold",
                "ready to go",
                "go there now",
                "head there now",
                "attend the appointment",
                "go to the appointment");
    }

    private static String latestAssistantSpeech(EventHistory events) {
        if (events == null || events.isEmpty()) {
            return "";
        }
        List<Event> history = events.toList();
        for (int i = history.size() - 1; i >= 0; i--) {
            Event event = history.get(i);
            if (event == null || !Event.ACTOR_ASSISTANT.equals(event.getActor())) {
                continue;
            }
            if (Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(event.getType())) {
                BehaviourPlan plan = BehaviourPlan.fromJson(event.getPayload());
                if (plan != null && plan.getSpeech() != null) {
                    return plan.getSpeech();
                }
            }
            if (event.getPayload() != null) {
                return event.getPayload();
            }
        }
        return "";
    }

    private static boolean containsAny(String source, String... needles) {
        for (String needle : needles) {
            if (source.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(raw, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "");
        return decomposed.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9' ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    @Override
    public String toString() {
        return "LatestUserUtteranceIntentPreGuard(" + this.mode + ")";
    }
}
