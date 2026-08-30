package ch.zhaw.prometheus.definition.document;

import com.fasterxml.jackson.databind.JsonNode;

public record DefinitionResource(String id, String kind, int version, JsonNode config) {
}
