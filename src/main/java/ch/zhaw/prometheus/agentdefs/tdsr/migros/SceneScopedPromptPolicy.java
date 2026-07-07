package ch.zhaw.prometheus.agentdefs.tdsr.migros;

import java.util.List;

import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.OutputProfile;
import ch.zhaw.prometheus.model.policy.Policy;
import ch.zhaw.prometheus.model.policy.PromptMessage;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.spi.LanguageModelGateway;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToOne;

@Entity
class SceneScopedPromptPolicy extends Policy {
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private PromptPolicy delegate;

    @Column(columnDefinition = "TEXT")
    private String relevancePrompt;

    protected SceneScopedPromptPolicy() {
        this(new PromptPolicy(), "");
    }

    SceneScopedPromptPolicy(PromptPolicy delegate, String relevancePrompt) {
        this.delegate = delegate == null ? new PromptPolicy() : delegate;
        this.relevancePrompt = relevancePrompt == null ? "" : relevancePrompt;
    }

    @Override
    public Policy withOuterPolicy(Policy outerPolicy) {
        Policy composed = this.delegate.withOuterPolicy(outerPolicy);
        if (!(composed instanceof PromptPolicy promptPolicy)) {
            throw new IllegalArgumentException("scene-scoped policy requires PromptPolicy delegate");
        }
        return new SceneScopedPromptPolicy(promptPolicy, this.relevancePrompt);
    }

    @Override
    public BehaviourPlan onStart(State state, EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway languageModelGateway) {
        return this.delegate.onStart(state, events, assembler, languageModelGateway);
    }

    @Override
    public BehaviourPlan onStart(State state, EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway languageModelGateway, OutputProfile outputProfile) {
        return this.delegate.onStart(state, events, assembler, languageModelGateway, outputProfile);
    }

    @Override
    public BehaviourPlan onRespond(State state, EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway languageModelGateway) {
        return this.onRespond(state, events, assembler, languageModelGateway, OutputProfile.FULL_PLAN);
    }

    @Override
    public BehaviourPlan onRespond(State state, EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway languageModelGateway, OutputProfile outputProfile) {
        if (!this.latestUserUtteranceIsSceneRelevant(events, assembler, languageModelGateway)) {
            return null;
        }
        return this.delegate.onRespond(state, events, assembler, languageModelGateway, outputProfile);
    }

    @Override
    public String summarise(State state, EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway languageModelGateway) {
        return this.delegate.summarise(state, events, assembler, languageModelGateway);
    }

    @Override
    public String describe() {
        return this.delegate.describe();
    }

    @Override
    public String describe(OutputProfile outputProfile) {
        return this.delegate.describe(outputProfile);
    }

    String getRelevancePrompt() {
        return this.relevancePrompt;
    }

    private boolean latestUserUtteranceIsSceneRelevant(EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway languageModelGateway) {
        return latestUserUtteranceIsSceneRelevant(events, assembler, languageModelGateway, this.relevancePrompt, true);
    }

    static boolean latestUserUtteranceIsSceneRelevant(EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway languageModelGateway, String relevancePrompt, boolean allowNonUserLatest) {
        Event latest = latestEvent(events);
        if (latest == null || !Event.TYPE_USER_UTTERANCE.equals(latest.getType())) {
            return allowNonUserLatest;
        }
        if (relevancePrompt == null || relevancePrompt.isBlank()) {
            return true;
        }
        List<PromptMessage> messages = List.of(
                PromptMessage.system(relevancePrompt),
                assembler.toPromptMessage(latest),
                PromptMessage.system(LanguageModelGateway.REMINDER_DECISION));
        return languageModelGateway.decide(messages);
    }

    private static Event latestEvent(EventHistory events) {
        if (events == null || events.isEmpty()) {
            return null;
        }
        List<Event> allEvents = events.toList();
        return allEvents.isEmpty() ? null : allEvents.get(allEvents.size() - 1);
    }
}
