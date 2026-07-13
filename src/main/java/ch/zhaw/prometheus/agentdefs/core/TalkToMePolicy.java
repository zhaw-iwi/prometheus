package ch.zhaw.prometheus.agentdefs.core;

import java.util.List;

import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.Policy;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.spi.LanguageModelGateway;
import jakarta.persistence.Entity;

@Entity
public class TalkToMePolicy extends Policy {
    public static final int MAX_TEXT_CODE_POINTS = 2000;

    @Override
    public BehaviourPlan onStart(State state, EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway languageModelGateway) {
        return speechFromLatestUserUtterance(events);
    }

    @Override
    public BehaviourPlan onRespond(State state, EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway languageModelGateway) {
        return speechFromLatestUserUtterance(events);
    }

    @Override
    public String summarise(State state, EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway languageModelGateway) {
        return null;
    }

    @Override
    public String describe() {
        return "Deterministic Talk to Me policy. Speech is supplied by a persisted PROMETHEUS BehaviourPlan.";
    }

    private static BehaviourPlan speechFromLatestUserUtterance(EventHistory events) {
        if (events == null) {
            return null;
        }
        List<Event> history = events.toList();
        for (int index = history.size() - 1; index >= 0; index--) {
            Event event = history.get(index);
            if (Event.TYPE_USER_UTTERANCE.equals(event.getType())
                    && Event.ACTOR_USER.equals(event.getActor())
                    && Event.KIND_OBSERVATION.equals(event.getKind())) {
                return speechOnlyIfValid(event.getPayload());
            }
        }
        return null;
    }

    private static BehaviourPlan speechOnlyIfValid(String text) {
        if (text == null || text.isBlank()
                || text.codePointCount(0, text.length()) > MAX_TEXT_CODE_POINTS) {
            return null;
        }
        return BehaviourPlan.speechOnly(text);
    }
}
