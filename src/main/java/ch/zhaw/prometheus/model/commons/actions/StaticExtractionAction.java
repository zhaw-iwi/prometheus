package ch.zhaw.prometheus.model.commons.actions;

import com.google.gson.JsonElement;

import ch.zhaw.prometheus.model.Action;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.model.Storage;
import jakarta.persistence.Entity;

@Entity
public class StaticExtractionAction extends Action {

    protected StaticExtractionAction() {

    }

    public StaticExtractionAction(String actionPrompt, Storage storage, String storageKeyTo) {
        super(new PromptPolicy(actionPrompt, null, null), storage, storageKeyTo);
    }

    @Override
    public void execute(EventHistory eventHistory, PolicyRuntime runtime) {
        JsonElement result = this.getPolicy().extract(eventHistory, runtime.promptMessageAssembler(),
                runtime.languageModelGateway());
        this.getStorage().put(this.getStorageKeyTo(), result);
    }

    @Override
    public String toString() {
        return "StaticExtractionAction IS-A " + super.toString();
    }
}

