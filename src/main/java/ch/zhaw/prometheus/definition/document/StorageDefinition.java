package ch.zhaw.prometheus.definition.document;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

public record StorageDefinition(
        String key,
        @JsonInclude(JsonInclude.Include.NON_NULL) String description,
        JsonNode valueSchema,
        boolean required,
        String visibility,
        String reset,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonNode initialValue,
        List<JsonNode> examples) {

    public StorageDefinition {
        examples = DocumentCollections.copyList(examples);
    }
}
