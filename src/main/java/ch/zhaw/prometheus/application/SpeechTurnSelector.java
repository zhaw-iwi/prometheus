package ch.zhaw.prometheus.application;

import java.util.List;
import java.util.Optional;

import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;

final class SpeechTurnSelector {
    private SpeechTurnSelector() {
    }

    static Optional<String> latestAssistantSpeechIfLatestUtterance(List<Event> history) {
        if (history == null || history.isEmpty()) {
            return Optional.empty();
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            Event event = history.get(i);
            if (event == null) {
                continue;
            }
            if (Event.TYPE_USER_UTTERANCE.equals(event.getType())) {
                return Optional.empty();
            }
            String speech = speechFromEvent(event);
            if (isPresent(speech)) {
                return Optional.of(speech.trim());
            }
        }
        return Optional.empty();
    }

    private static String speechFromEvent(Event event) {
        if (event == null || !Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(event.getType())) {
            return null;
        }
        BehaviourPlan plan = BehaviourPlan.fromJson(event.getPayload());
        return plan == null ? null : plan.getSpeech();
    }

    private static boolean isPresent(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
