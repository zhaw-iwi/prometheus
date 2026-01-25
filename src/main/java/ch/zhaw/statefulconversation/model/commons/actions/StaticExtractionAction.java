package ch.zhaw.statefulconversation.model.commons.actions;

import com.google.gson.JsonElement;

import ch.zhaw.statefulconversation.model.Action;
import ch.zhaw.statefulconversation.model.Storage;
import ch.zhaw.statefulconversation.model.EventHistory;
import ch.zhaw.statefulconversation.spi.LMOpenAI;
import jakarta.persistence.Entity;

@Entity
public class StaticExtractionAction extends Action {

    protected StaticExtractionAction() {

    }

    public StaticExtractionAction(String actionPrompt, Storage storage, String storageKeyTo) {
        super(actionPrompt, storage, storageKeyTo);
    }

    @Override
    public void execute(EventHistory eventHistory) {
        JsonElement result = LMOpenAI.extract(eventHistory, this.getPrompt());
        this.getStorage().put(this.getStorageKeyTo(), result);
    }

    @Override
    public String toString() {
        return "StaticExtractionAction IS-A " + super.toString();
    }
}
