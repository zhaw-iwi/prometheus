package ch.zhaw.prometheus.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;

/** Lightweight read model for one mutable, revision-pinned declarative instance. */
public final class Agent {
    private final UUID id;
    private final long definitionRevisionId;
    private final String definitionKey;
    private final String name;
    private final String description;
    private final boolean active;
    private final AgentInteractionProfile interactionProfile;
    private final String languageCode;
    private final List<String> activeStateIds;
    private final List<String> activeStateNames;
    private final List<String> states;
    private final Map<String, JsonNode> storage;
    private final List<Event> eventHistory;

    public Agent(UUID id, long definitionRevisionId, String definitionKey, String name, String description,
            boolean active, AgentInteractionProfile interactionProfile, String languageCode,
            List<String> activeStateIds, List<String> activeStateNames, List<String> states,
            Map<String, JsonNode> storage, List<Event> eventHistory) {
        this.id = id;
        this.definitionRevisionId = definitionRevisionId;
        this.definitionKey = definitionKey;
        this.name = name;
        this.description = description;
        this.active = active;
        this.interactionProfile = interactionProfile == null ? AgentInteractionProfile.empty() : interactionProfile;
        this.languageCode = languageCode;
        this.activeStateIds = List.copyOf(activeStateIds);
        this.activeStateNames = List.copyOf(activeStateNames);
        this.states = List.copyOf(states);
        this.storage = Collections.unmodifiableMap(new LinkedHashMap<>(storage));
        this.eventHistory = List.copyOf(eventHistory);
    }

    public UUID getId() { return this.id; }
    public long getDefinitionRevisionId() { return this.definitionRevisionId; }
    public String getDefinitionKey() { return this.definitionKey; }
    public String getName() { return this.name; }
    public String getDescription() { return this.description; }
    public boolean isActive() { return this.active; }
    public AgentInteractionProfile getInteractionProfile() { return this.interactionProfile; }
    public String getLanguageCode() { return this.languageCode; }
    public List<String> getActiveStateIds() { return this.activeStateIds; }
    public List<String> getActiveStateNames() { return this.activeStateNames; }
    public List<String> listStates() { return this.states; }
    public Map<String, JsonNode> getStorage() { return this.storage; }
    public List<Event> getEventHistory() { return this.eventHistory; }

    public List<Event> getCurrentStateEventHistory() {
        if (this.activeStateIds.isEmpty()) {
            return List.of();
        }
        String leaf = this.activeStateIds.getLast();
        return this.eventHistory.stream().filter(event -> event.getStatePath().contains(leaf)).toList();
    }
}
