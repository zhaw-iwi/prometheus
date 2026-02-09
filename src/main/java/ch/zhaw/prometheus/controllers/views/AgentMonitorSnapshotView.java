package ch.zhaw.prometheus.controllers.views;

import java.util.List;
import java.util.UUID;

public class AgentMonitorSnapshotView {
    private UUID agentId;
    private String name;
    private String description;
    private boolean active;
    private String stateName;
    private String innerName;
    private List<String> innerNames;
    private List<String> states;
    private List<StorageEntryView> storage;

    public AgentMonitorSnapshotView(UUID agentId, String name, String description, boolean active,
            String stateName, String innerName, List<String> innerNames, List<String> states, List<StorageEntryView> storage) {
        this.agentId = agentId;
        this.name = name;
        this.description = description;
        this.active = active;
        this.stateName = stateName;
        this.innerName = innerName;
        this.innerNames = innerNames;
        this.states = states;
        this.storage = storage;
    }

    public UUID getAgentId() {
        return agentId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }

    public String getStateName() {
        return stateName;
    }

    public String getInnerName() {
        return innerName;
    }

    public List<String> getInnerNames() {
        return innerNames;
    }

    public List<String> getStates() {
        return states;
    }

    public List<StorageEntryView> getStorage() {
        return storage;
    }
}
