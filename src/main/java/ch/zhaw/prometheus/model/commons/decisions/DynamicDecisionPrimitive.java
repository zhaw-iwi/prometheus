package ch.zhaw.prometheus.model.commons.decisions;

import ch.zhaw.prometheus.model.Decision;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.model.policy.PromptValueShape;
import ch.zhaw.prometheus.model.Storage;
import jakarta.persistence.Entity;

@Entity
public class DynamicDecisionPrimitive extends Decision {

    protected DynamicDecisionPrimitive() {

    }

    public DynamicDecisionPrimitive(String decisionPromptTemplate, Storage storage, String storageKeyFrom) {
        super(new PromptPolicy(decisionPromptTemplate, null, null, storage, java.util.List.of(storageKeyFrom),
                PromptValueShape.PRIMITIVE));
    }

    @Override
    public String toString() {
        return "DynamicDecisionPrimitive IS-A " + super.toString();
    }
}
