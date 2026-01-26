package ch.zhaw.prometheus.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;

import jakarta.persistence.CascadeType;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;

@Entity
public abstract class Action extends PersistedNode {

    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Policy policy;

    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Storage storage;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> storageKeysFrom;

    private String storageKeyTo;

    @Transient
    private EventSelector eventSelector;

    protected Action() {
        this.storageKeysFrom = List.of();
        this.storageKeyTo = null;
    }

    public Action(Policy policy) {
        this.policy = policy;
        this.storage = null;
        this.storageKeysFrom = List.of();
        this.storageKeyTo = null;
    }

    public Action(Policy policy, EventSelector eventSelector) {
        this(policy);
        this.eventSelector = eventSelector;
    }

    public Action(Policy policy, Storage storage, String storageKeyTo) {
        this(policy);
        this.storageKeyTo = storageKeyTo;
        this.storage = storage;
        this.storageKeysFrom = List.of();
    }

    public Action(Policy policy, Storage storage, String storageKeyFrom, String storageKeyTo) {
        this(policy);
        this.storageKeyTo = storageKeyTo;
        this.storage = storage;
        this.storageKeysFrom = List.of(storageKeyFrom);
    }

    public Action(Policy policy, Storage storage, List<String> storageKeysFrom, String storageKeyTo) {
        this(policy);
        this.storageKeyTo = storageKeyTo;
        this.storage = storage;
        this.storageKeysFrom = storageKeysFrom == null ? List.of() : new ArrayList<>(storageKeysFrom);
    }

    protected String getStorageKeyTo() {
        if (this.storageKeyTo == null) {
            throw new RuntimeException(
                    "this is not a dynamic action - storageKeyTo is supposed to be null");
        }
        return this.storageKeyTo;
    }

    protected Storage getStorage() {
        if (this.storage == null) {
            throw new RuntimeException(
                    "this action does not have storage attached");
        }
        return this.storage;
    }

    protected List<String> getStorageKeysFrom() {
        if (this.storageKeysFrom == null || this.storageKeysFrom.isEmpty()) {
            throw new RuntimeException(
                    "this action does not have storageKeysFrom attached");
        }
        return this.storageKeysFrom;
    }

    protected Map<String, JsonElement> getValuesForKeys() {
        if (this.storage == null || this.storageKeysFrom == null) {
            throw new RuntimeException(
                    "this action does not have storage and storageKeysFrom attached");
        }
        Map<String, JsonElement> result = new HashMap<>();
        for (String currentKey : this.storageKeysFrom) {
            result.put(currentKey, this.storage.get(currentKey));
        }
        return result;
    }

    public Policy getPolicy() {
        if (this.policy == null) {
            throw new IllegalStateException("action policy not set");
        }
        return this.policy;
    }

    public void setPolicy(Policy policy) {
        this.policy = policy;
    }

    public EventSelector getEventSelector() {
        return this.eventSelector;
    }

    public void setEventSelector(EventSelector eventSelector) {
        this.eventSelector = eventSelector;
    }

    public abstract void execute(EventHistory eventHistory);

    @Override
    public String toString() {
        return "Action with policy " + this.policy;
    }
}
