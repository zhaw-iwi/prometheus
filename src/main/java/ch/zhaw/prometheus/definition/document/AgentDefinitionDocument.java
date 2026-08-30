package ch.zhaw.prometheus.definition.document;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public record AgentDefinitionDocument(
        @JsonProperty("$schema") String schema,
        int schemaVersion,
        String key,
        int revision,
        AgentMetadata metadata,
        AgentInteraction interaction,
        AgentLifecycle lifecycle,
        List<StorageDefinition> storage,
        List<DefinitionResource> resources,
        List<StateDefinition> states,
        List<TransitionDefinition> transitions,
        @JsonInclude(JsonInclude.Include.NON_NULL) VerificationDefinition verification) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String SCHEMA_REFERENCE = "/agent-definitions/schema/agent-definition.schema.json";

    public AgentDefinitionDocument {
        storage = DocumentCollections.copyList(storage);
        resources = DocumentCollections.copyList(resources);
        states = DocumentCollections.copyList(states);
        transitions = DocumentCollections.copyList(transitions);
    }
}
