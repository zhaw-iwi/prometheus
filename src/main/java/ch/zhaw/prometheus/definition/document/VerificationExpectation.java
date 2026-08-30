package ch.zhaw.prometheus.definition.document;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

public record VerificationExpectation(
        List<String> activeStatePath,
        Map<String, JsonNode> storage,
        List<JsonNode> behaviourFragments) {

    public VerificationExpectation {
        activeStatePath = DocumentCollections.copyList(activeStatePath);
        storage = DocumentCollections.copyMap(storage);
        behaviourFragments = DocumentCollections.copyList(behaviourFragments);
    }
}
