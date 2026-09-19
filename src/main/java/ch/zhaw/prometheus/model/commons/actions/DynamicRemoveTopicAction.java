package ch.zhaw.prometheus.model.commons.actions;

import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import ch.zhaw.prometheus.model.Action;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.policy.NoOpPolicy;
import ch.zhaw.prometheus.model.Storage;
import jakarta.persistence.Entity;

@Entity
public class DynamicRemoveTopicAction extends Action {

    protected DynamicRemoveTopicAction() {

    }

    public DynamicRemoveTopicAction(String actionPromptTemplate, Storage storage, String storageKeyFrom,
            String storageKeyTo) {
        super(new NoOpPolicy(), storage, storageKeyFrom, storageKeyTo);
    }

    @Override
    public void execute(EventHistory eventHistory, PolicyRuntime runtime) {
        String key = getStorageKeysFrom().get(0);
        getStorage().put(key, com.google.gson.JsonParser.parseString(remove(
                getStorage().get(key).toString(), getStorage().get(getStorageKeyTo()).toString())));
    }

    @Override public ch.zhaw.prometheus.model.PreparedAction prepare(EventHistory events,
            ch.zhaw.prometheus.model.snapshot.ObservationSnapshot snapshot, PolicyRuntime runtime) {
        String key = getStorageKeysFrom().get(0);
        String topics = getStorage().get(key).toString(), choice = getStorage().get(getStorageKeyTo()).toString();
        return prepared(java.util.Set.of(key), gateway -> java.util.Map.of(key, remove(topics, choice)));
    }

    private static String remove(String topicsJson, String choiceJson) {
        JsonElement topicsTo = com.google.gson.JsonParser.parseString(topicsJson);
        JsonElement topicFrom = com.google.gson.JsonParser.parseString(choiceJson);

        if (!(topicsTo instanceof JsonArray)) {
            throw new RuntimeException(
                    "Invalid data in storage. Expected value for key " + "topics"
                            + " to be instance of JsonArray but was " + topicsTo.getClass() + " instead");
        }

        String stringToRemove = null;
        if (topicFrom instanceof JsonPrimitive) {
            stringToRemove = topicFrom.getAsJsonPrimitive().getAsString();
        } else if (topicFrom instanceof JsonObject) {
            JsonObject temporaryObject = topicFrom.getAsJsonObject();
            if (temporaryObject.entrySet().size() != 1) {
                throw new RuntimeException(
                        "Invalid data in storage. Expected JsonObject with only one key value pair but found "
                                + temporaryObject.entrySet().size() + " pairs instead");
            }
            JsonElement temporaryElement = temporaryObject.entrySet().iterator().next().getValue();
            if (!(temporaryElement instanceof JsonPrimitive)) {
                throw new RuntimeException(
                        "Invalid data in storage. Expected JsonObject to have a value that is an instance of JsonPrimitive but found "
                                + temporaryElement.getClass() + " instead");
            }
            stringToRemove = temporaryElement.getAsJsonPrimitive().getAsString();
        } else {
            throw new RuntimeException(
                    "Invalid data in storage. Expected value for key " + "choice"
                            + " to be instance of JsonPrimitive or JsonObject but was " + topicFrom.getClass()
                            + " instead");
        }
        List<String> topicsList = Storage.toListOfString(topicsTo);
        if (!topicsList.contains(stringToRemove)) {
            throw new RuntimeException(
                    "List " + topicsList + " does not contain the item " + stringToRemove + " to be removed");
        }
        topicsList.remove(stringToRemove);
        return Storage.toJsonElement(topicsList).toString();
    }

    @Override
    public String toString() {
        return "DynamicRemoveTopicAction IS-A " + super.toString();
    }
}

