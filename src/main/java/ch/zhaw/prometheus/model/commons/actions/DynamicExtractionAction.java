package ch.zhaw.prometheus.model.commons.actions;

import com.google.gson.JsonElement;

import ch.zhaw.prometheus.model.Action;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.model.policy.PromptValueShape;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.spi.LanguageModelGateway;
import jakarta.persistence.Entity;

@Entity
public class DynamicExtractionAction extends Action {

    protected DynamicExtractionAction() {

    }

    public DynamicExtractionAction(String actionPromptTemplate, Storage storage, String storageKeyFrom,
            String storageKeyTo) {
        super(new PromptPolicy(actionPromptTemplate, null, null, storage, java.util.List.of(storageKeyFrom),
                PromptValueShape.ARRAY), storage, storageKeyFrom, storageKeyTo);
    }

    @Override
    public void execute(EventHistory eventHistory, PromptMessageAssembler assembler,
            LanguageModelGateway languageModelGateway) {
        JsonElement result = this.getPolicy().extract(eventHistory, assembler, languageModelGateway);
        this.getStorage().put(this.getStorageKeyTo(), result);
    }

    @Override
    public String toString() {
        return "DynamicExtractionAction IS-A " + super.toString();
    }
}

