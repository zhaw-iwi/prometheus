package ch.zhaw.prometheus.controllers.views;

import java.util.UUID;

import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;

public class AgentInfoView {
    private UUID id;
    private String name;
    private String description;
    private boolean isActive;
    private AgentInteractionProfile interactionProfile;

    public AgentInfoView(UUID id, String name, String descripion, boolean isActive) {
        this(id, name, descripion, isActive, AgentInteractionProfile.empty());
    }

    public AgentInfoView(UUID id, String name, String descripion, boolean isActive,
            AgentInteractionProfile interactionProfile) {
        this.id = id;
        this.name = name;
        this.description = descripion;
        this.isActive = isActive;
        this.interactionProfile = interactionProfile == null ? AgentInteractionProfile.empty() : interactionProfile;
    }

    public UUID getID() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    public boolean isActive() {
        return this.isActive;
    }

    public AgentInteractionProfile getInteractionProfile() {
        if (this.interactionProfile == null) {
            this.interactionProfile = AgentInteractionProfile.empty();
        }
        return this.interactionProfile;
    }
}
