package ch.zhaw.prometheus.agentdefs.tdsr.migros;

import ch.zhaw.prometheus.model.Decision;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.NoOpPolicy;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;

@Entity
class SceneScopedRelevanceDecision extends Decision {
    @Column(columnDefinition = "TEXT")
    private String relevancePrompt;

    protected SceneScopedRelevanceDecision() {
    }

    SceneScopedRelevanceDecision(String relevancePrompt) {
        super(new NoOpPolicy());
        this.relevancePrompt = relevancePrompt == null ? "" : relevancePrompt;
    }

    @Override
    public boolean decide(EventHistory events, PolicyRuntime runtime) {
        return SceneScopedPromptPolicy.latestUserUtteranceIsSceneRelevant(
                events,
                runtime.promptMessageAssembler(),
                runtime.languageModelGateway(),
                this.relevancePrompt,
                false);
    }
}
