package ch.zhaw.prometheus.model.commons.decisions;

import ch.zhaw.prometheus.model.Decision;
import ch.zhaw.prometheus.model.PromptPolicy;
import jakarta.persistence.Entity;

@Entity
public class StaticDecision extends Decision {

    protected StaticDecision() {

    }

    public StaticDecision(String decisionPrompt) {
        super(new PromptPolicy(decisionPrompt, null, null));
    }

    @Override
    public String toString() {
        return "StaticDecision IS-A " + super.toString();
    }
}
