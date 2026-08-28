package ch.zhaw.prometheus.agentdefs;

import java.util.Arrays;
import java.util.List;

import ch.zhaw.prometheus.model.Agent;

public interface AgentDefinition {

    String PACKAGE_PREFIX = "ch.zhaw.prometheus.agentdefs.";
    String LANGUAGE_ARABIC = "ar";
    String LANGUAGE_ENGLISH = "en";
    String LANGUAGE_FRENCH = "fr";
    String LANGUAGE_GERMAN = "de";
    String LANGUAGE_ITALIAN = "it";

    String key();

    Agent createAgent();

    default String languageCode() {
        return null;
    }

    default Agent applyDefinitionMetadata(Agent agent) {
        if (agent != null && !isPresent(agent.getLanguageCode()) && isPresent(this.languageCode())) {
            agent.setLanguageCode(this.languageCode());
        }
        return agent;
    }

    default String displayName() {
        return this.createAgent().getName();
    }

    default String description() {
        return this.createAgent().getDescription();
    }

    default List<String> packagePath() {
        String packageName = this.getClass().getPackageName();
        if (packageName == null || !packageName.startsWith(PACKAGE_PREFIX)) {
            return List.of();
        }
        String relativePackage = packageName.substring(PACKAGE_PREFIX.length());
        if (relativePackage.isBlank()) {
            return List.of();
        }
        return Arrays.stream(relativePackage.split("\\."))
                .filter(segment -> !segment.isBlank())
                .toList();
    }

    default AgentCreationResult createInstance(AgentCreationContext context) {
        return AgentCreationResult.created(this.applyDefinitionMetadata(this.createAgent()));
    }

    private static boolean isPresent(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
