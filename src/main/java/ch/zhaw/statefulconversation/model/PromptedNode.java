package ch.zhaw.statefulconversation.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class PromptedNode extends PersistedNode {
    @Column(length = 3000)
    private String prompt;

    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Storage storage;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> storageKeysFrom;

    protected PromptedNode() {

    }

    public PromptedNode(String prompt) {
        this.prompt = prompt;
        this.storage = null;
        this.storageKeysFrom = List.of();
    }

    public PromptedNode(String prompt, Storage storage, List<String> storageKeysFrom) {
        this(prompt);
        this.storage = storage;
        this.storageKeysFrom = new ArrayList<>(storageKeysFrom);
    }

    protected String getPrompt() {
        return this.prompt;
    }

    protected Storage getStorage() {
        if (this.storage == null) {
            throw new RuntimeException(
                    "this is not a dynamic prompt - storage and storageKeysFrom are supposed to be null and empty");
        }
        return this.storage;
    }

    protected List<String> getStorageKeysFrom() {
        if (this.storageKeysFrom.isEmpty()) {
            throw new RuntimeException(
                    "this is not a dynamic prompt - storage and storageKeysFrom are supposed to be null and empty");
        }
        return this.storageKeysFrom;
    }

    protected Map<String, JsonElement> getValuesForKeys() {
        if (this.storage == null || this.storageKeysFrom == null) {
            throw new RuntimeException(
                    "this is not a dynamic prompt - storage and storageKeysFrom are supposed to be null");
        }
        Map<String, JsonElement> result = new HashMap<>();
        for (String currentKey : this.storageKeysFrom) {
            result.put(currentKey, this.storage.get(currentKey));
        }
        return result;
    }

    @Override
    public String toString() {
        return "Prompt with value \"" + this.getPrompt() + "\"";
    }
}
