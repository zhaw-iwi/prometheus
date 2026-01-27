package ch.zhaw.prometheus.model.commons.actions;

import com.google.gson.JsonElement;

import ch.zhaw.prometheus.model.Action;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.model.Storage;
import jakarta.persistence.Entity;

@Entity
public class StaticSummarisationAction extends Action {

    protected StaticSummarisationAction() {

    }

    public StaticSummarisationAction(String actionPrompt, Storage storage, String storageKeyTo) {
        super(new PromptPolicy(actionPrompt, null, null), storage, storageKeyTo);
    }

    @Override
    public void execute(EventHistory eventHistory) {
        JsonElement result = this.getPolicy().summarise(eventHistory);
        this.getStorage().put(this.getStorageKeyTo(), result);
    }

    @Override
    public String toString() {
        return "StaticSummarisationAction IS-A " + super.toString();
    }
}
