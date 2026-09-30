package ch.zhaw.prometheus.model.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import ch.zhaw.prometheus.model.AgentEmbodiment;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.interaction.AgentCapabilityDescription;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;

@Component
public class PromptMessageAssembler {
    private static final Pattern LEGACY_PERSONA_NAME = Pattern.compile("\\b(?:Valerian|Gigi)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final List<PromptEventContentAdapter> eventContentAdapters;
    private final List<PromptContextAugmenter> contextAugmenters;
    private final boolean speculativeComposition;
    private final AgentEmbodiment embodiment;

    public PromptMessageAssembler() {
        this(List.of(
                new BehaviourPlanPromptEventContentAdapter(),
                new FaceEmotionPromptEventContentAdapter(),
                new SocialSituationChangePromptEventContentAdapter(),
                new SocialContextPromptEventContentAdapter(),
                new WeatherPromptEventContentAdapter(),
                new DefaultPayloadPromptEventContentAdapter()),
                List.of(new NonverbalSummaryPromptContextAugmenter()), true, AgentEmbodiment.COCKPIT);
    }

    public PromptMessageAssembler(List<PromptEventContentAdapter> eventContentAdapters,
            List<PromptContextAugmenter> contextAugmenters) {
        this(eventContentAdapters, contextAugmenters, false, AgentEmbodiment.COCKPIT);
    }

    private PromptMessageAssembler(List<PromptEventContentAdapter> eventContentAdapters,
            List<PromptContextAugmenter> contextAugmenters, boolean speculativeComposition,
            AgentEmbodiment embodiment) {
        this.eventContentAdapters = eventContentAdapters == null ? List.of() : List.copyOf(eventContentAdapters);
        this.contextAugmenters = contextAugmenters == null ? List.of() : List.copyOf(contextAugmenters);
        this.speculativeComposition = speculativeComposition;
        this.embodiment = embodiment == null ? AgentEmbodiment.COCKPIT : embodiment;
    }

    public boolean supportsSpeculativeComposition() { return getClass() == PromptMessageAssembler.class && speculativeComposition; }

    public PromptMessageAssembler forEmbodiment(AgentEmbodiment embodiment) {
        AgentEmbodiment resolved = embodiment == null ? AgentEmbodiment.COCKPIT : embodiment;
        if (resolved == this.embodiment) {
            return this;
        }
        return new PromptMessageAssembler(
                this.eventContentAdapters,
                this.contextAugmenters,
                this.speculativeComposition,
                resolved);
    }

    public boolean supportsGuardComposition() { return getClass() == PromptMessageAssembler.class; }

    /** Immutable binding; never mutate the shared Spring assembler or its extension points. */
    public PromptMessageAssembler forCapabilities(AgentInteractionProfile profile) {
        String context = AgentCapabilityDescription.context(profile);
        return context.isEmpty() ? this : new CapabilityAssembler(this, context);
    }

    private static final class CapabilityAssembler extends PromptMessageAssembler {
        private final PromptMessageAssembler delegate;
        private final String context;

        private CapabilityAssembler(PromptMessageAssembler delegate, String context) {
            super(List.of(), List.of());
            this.delegate = delegate;
            this.context = context;
        }

        @Override public PromptMessageAssembler forCapabilities(AgentInteractionProfile profile) {
            String updated = AgentCapabilityDescription.context(profile);
            return context.equals(updated) ? this : delegate.forCapabilities(profile);
        }
        @Override public PromptMessageAssembler forEmbodiment(AgentEmbodiment embodiment) {
            PromptMessageAssembler updated = delegate.forEmbodiment(embodiment);
            return updated == delegate ? this : new CapabilityAssembler(updated, context);
        }
        @Override public String resolveSystemPrompt(String prompt) { return delegate.resolveSystemPrompt(prompt); }
        @Override public boolean supportsSpeculativeComposition() { return delegate.supportsSpeculativeComposition(); }
        @Override public boolean supportsGuardComposition() { return delegate.supportsGuardComposition(); }
        @Override public List<PromptMessage> compose(EventHistory history, String prepend) {
            return withContext(delegate.compose(history, prepend));
        }
        @Override public List<PromptMessage> compose(EventHistory history, String prepend, String append) {
            return withContext(delegate.compose(history, prepend, append));
        }
        // Decisions, extraction and summaries retain their existing selected-history contract.
        @Override public List<PromptMessage> composeCondensed(EventHistory history, String prepend) {
            return delegate.composeCondensed(history, prepend);
        }
        @Override public List<PromptMessage> composeCondensed(EventHistory history, String prepend, String append) {
            return delegate.composeCondensed(history, prepend, append);
        }
        @Override public PromptMessage toPromptMessage(Event event) { return delegate.toPromptMessage(event); }
        @Override public String mapRole(Event event) { return delegate.mapRole(event); }
        private List<PromptMessage> withContext(List<PromptMessage> original) {
            var result = new ArrayList<>(original);
            result.add(Math.min(1, result.size()), PromptMessage.system(context));
            return result;
        }
    }

    public List<PromptMessage> compose(EventHistory eventHistory, String systemPrepend) {
        List<PromptMessage> messages = new ArrayList<>();
        requireSystem(systemPrepend);
        messages.add(PromptMessage.system(resolveSystemPrompt(systemPrepend)));
        if (eventHistory == null) {
            return messages;
        }
        for (Event event : eventHistory.toList()) {
            if (ch.zhaw.prometheus.model.event.ConversationProjection.isIntent(event)) continue;
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
            messages.add(PromptMessage.system(resolveSystemPrompt(systemAppend)));
        }
        return messages;
    }

    public List<PromptMessage> composeCondensed(EventHistory eventHistory, String systemPrepend) {
        requireSystem(systemPrepend);
        if (eventHistory == null || eventHistory.isEmpty()) {
            throw new RuntimeException("cannot compose condensed prompt from empty events");
        }
        List<PromptMessage> messages = new ArrayList<>();
        messages.add(PromptMessage.system(resolveSystemPrompt(systemPrepend)));
        messages.add(PromptMessage.system("<eventhistory>" + eventHistory.toString() + "</eventhistory>"));
        return messages;
    }

    public List<PromptMessage> composeCondensed(EventHistory eventHistory, String systemPrepend,
            String systemAppend) {
        if (systemAppend == null) {
            throw new NullPointerException("systemAppend cannot be null.");
        }
        List<PromptMessage> messages = composeCondensed(eventHistory, systemPrepend);
        messages.add(PromptMessage.system(resolveSystemPrompt(systemAppend)));
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

    public String resolveSystemPrompt(String prompt) {
        if (prompt == null) {
            return prompt;
        }
        return LEGACY_PERSONA_NAME.matcher(prompt)
                .replaceAll(Matcher.quoteReplacement(this.embodiment.personaName()));
    }
}
