package ch.zhaw.prometheus.controllers.views;

import java.util.UUID;

import ch.zhaw.prometheus.model.AgentEmbodiment;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;

public class AgentInfoView {
    private UUID id;
    private String name;
    private String description;
    private boolean isActive;
    private AgentInteractionProfile interactionProfile;
    private String languageCode;
    private String embodiment;
    private String personaName;

    public AgentInfoView(UUID id, String name, String descripion, boolean isActive) {
        this(id, name, descripion, isActive, AgentInteractionProfile.empty(), null);
    }

    public AgentInfoView(UUID id, String name, String descripion, boolean isActive,
            AgentInteractionProfile interactionProfile) {
        this(id, name, descripion, isActive, interactionProfile, null);
    }

    public AgentInfoView(UUID id, String name, String descripion, boolean isActive,
            AgentInteractionProfile interactionProfile, String languageCode) {
        this(id, name, descripion, isActive, interactionProfile, languageCode, AgentEmbodiment.COCKPIT);
    }

    public AgentInfoView(UUID id, String name, String descripion, boolean isActive,
            AgentInteractionProfile interactionProfile, String languageCode, AgentEmbodiment embodiment) {
        this.id = id;
        this.name = name;
        this.description = descripion;
        this.isActive = isActive;
        this.interactionProfile = interactionProfile == null ? AgentInteractionProfile.empty() : interactionProfile;
        this.languageCode = normalizeLanguageCode(languageCode);
        AgentEmbodiment resolved = embodiment == null ? AgentEmbodiment.COCKPIT : embodiment;
        this.embodiment = resolved.name();
        this.personaName = resolved.personaName();
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

    public String getLanguageCode() {
        return this.languageCode;
    }

    public String getEmbodiment() {
        return this.embodiment;
    }

    public String getPersonaName() {
        return this.personaName;
    }

    private static String normalizeLanguageCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
