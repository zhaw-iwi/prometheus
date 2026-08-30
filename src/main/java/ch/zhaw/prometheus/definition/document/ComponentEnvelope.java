package ch.zhaw.prometheus.definition.document;

import com.fasterxml.jackson.databind.JsonNode;

public record ComponentEnvelope(String kind, int version, JsonNode config) {
}
