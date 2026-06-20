package ch.zhaw.prometheus.model.policy;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;

@Component
public class PromptMessageAssembler {
    private final List<PromptEventContentAdapter> eventContentAdapters;
    private final List<PromptContextAugmenter> contextAugmenters;

    public PromptMessageAssembler() {
        this(List.of(
                new BehaviourPlanPromptEventContentAdapter(),
                new FaceEmotionPromptEventContentAdapter(),
                new SocialSituationChangePromptEventContentAdapter(),
                new WeatherPromptEventContentAdapter(),
                new DefaultPayloadPromptEventContentAdapter()),
                List.of(new NonverbalSummaryPromptContextAugmenter()));
    }

    public PromptMessageAssembler(List<PromptEventContentAdapter> eventContentAdapters,
            List<PromptContextAugmenter> contextAugmenters) {
        this.eventContentAdapters = eventContentAdapters == null ? List.of() : List.copyOf(eventContentAdapters);
        this.contextAugmenters = contextAugmenters == null ? List.of() : List.copyOf(contextAugmenters);
    }

    public List<PromptMessage> compose(EventHistory eventHistory, String systemPrepend) {
        List<PromptMessage> messages = new ArrayList<>();
        requireSystem(systemPrepend);
        messages.add(PromptMessage.system(systemPrepend));
        if (eventHistory == null) {
            return messages;
        }
        for (Event event : eventHistory.toList()) {
            messages.add(toPromptMessage(event));
        }
        for (PromptContextAugmenter augmenter : this.contextAugmenters) {
            if (augmenter == null) {
                continue;
            }
            messages.addAll(augmenter.augment(eventHistory));
        }
        return messages;
    }

    public List<PromptMessage> compose(EventHistory eventHistory, String systemPrepend, String systemAppend) {
        List<PromptMessage> messages = compose(eventHistory, systemPrepend);
        if (systemAppend != null) {
            messages.add(PromptMessage.system(systemAppend));
        }
        return messages;
    }

    public List<PromptMessage> composeCondensed(EventHistory eventHistory, String systemPrepend) {
        requireSystem(systemPrepend);
        if (eventHistory == null || eventHistory.isEmpty()) {
            throw new RuntimeException("cannot compose condensed prompt from empty events");
        }
        List<PromptMessage> messages = new ArrayList<>();
        messages.add(PromptMessage.system(systemPrepend));
        messages.add(PromptMessage.system("<eventhistory>" + eventHistory.toString() + "</eventhistory>"));
        return messages;
    }

    public List<PromptMessage> composeCondensed(EventHistory eventHistory, String systemPrepend,
            String systemAppend) {
        if (systemAppend == null) {
            throw new NullPointerException("systemAppend cannot be null.");
        }
        List<PromptMessage> messages = composeCondensed(eventHistory, systemPrepend);
        messages.add(PromptMessage.system(systemAppend));
        return messages;
    }

    public PromptMessage toPromptMessage(Event event) {
        return PromptMessage.of(mapRole(event), toPromptContent(event));
    }

    public String mapRole(Event event) {
        if (event == null) {
            return "user";
        }
        if (Event.TYPE_SYSTEM_PROMPT.equals(event.getType())
                || Event.KIND_SYSTEM.equals(event.getKind())
                || Event.ACTOR_SYSTEM.equals(event.getActor())) {
            return "system";
        }
        if (Event.ACTOR_ASSISTANT.equals(event.getActor()) || Event.KIND_RESPONSE.equals(event.getKind())) {
            return "assistant";
        }
        if (Event.ACTOR_USER.equals(event.getActor()) || Event.KIND_OBSERVATION.equals(event.getKind())) {
            return "user";
        }
        return "user";
    }

    private String toPromptContent(Event event) {
        for (PromptEventContentAdapter adapter : this.eventContentAdapters) {
            if (adapter == null || !adapter.supports(event)) {
                continue;
            }
            return adapter.toPromptContent(event);
        }
        return "";
    }

    private static void requireSystem(String systemPrepend) {
        if (systemPrepend == null) {
            throw new NullPointerException("systemPrepend (Decision prompt) cannot be null.");
        }
    }
}

