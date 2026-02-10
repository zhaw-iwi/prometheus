package ch.zhaw.prometheus.model.commons.actions;

import com.google.gson.JsonElement;

import ch.zhaw.prometheus.model.Action;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.model.policy.PromptValueShape;
import ch.zhaw.prometheus.model.Storage;
import jakarta.persistence.Entity;

@Entity
public class DynamicExtractionActionPrimitive extends Action {

    protected DynamicExtractionActionPrimitive() {

    }

    public DynamicExtractionActionPrimitive(String actionPromptTemplate, Storage storage, String storageKeyFrom,
            String storageKeyTo) {
        super(new PromptPolicy(actionPromptTemplate, null, null, storage, java.util.List.of(storageKeyFrom),
                PromptValueShape.PRIMITIVE), storage, storageKeyFrom, storageKeyTo);
    }

    @Override
    public void execute(EventHistory eventHistory, PolicyRuntime runtime) {
        JsonElement result = this.getPolicy().extract(eventHistory, runtime.promptMessageAssembler(),
                runtime.languageModelGateway());
        this.getStorage().put(this.getStorageKeyTo(), result);
    }

    @Override
    public String toString() {
        return "DynamicExtractionActionPrimitive IS-A " + super.toString();
    }
}

