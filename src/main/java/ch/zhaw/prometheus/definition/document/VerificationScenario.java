package ch.zhaw.prometheus.definition.document;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

public record VerificationScenario(
        String name,
        @JsonInclude(JsonInclude.Include.NON_NULL) String description,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long initializerSeed,
        Map<String, JsonNode> initialStorage,
        List<VerificationEvent> events,
        VerificationExpectation expected) {

    public VerificationScenario {
        initialStorage = DocumentCollections.copyMap(initialStorage);
        events = DocumentCollections.copyList(events);
    }
}
