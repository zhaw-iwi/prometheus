package ch.zhaw.prometheus.model.commons.decisions;

import ch.zhaw.prometheus.model.Decision;
import ch.zhaw.prometheus.model.PromptPolicy;
import ch.zhaw.prometheus.model.PromptValueShape;
import ch.zhaw.prometheus.model.Storage;
import jakarta.persistence.Entity;

@Entity
public class DynamicDecision extends Decision {

    protected DynamicDecision() {

    }

    public DynamicDecision(String decisionPromptTemplate, Storage storage, String storageKeyFrom) {
        super(new PromptPolicy(decisionPromptTemplate, null, null, storage, java.util.List.of(storageKeyFrom),
                PromptValueShape.ARRAY));
    }

    @Override
    public String toString() {
        return "DynamicDecision IS-A " + super.toString();
    }
}
