package ch.zhaw.prometheus.definition.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ch.zhaw.prometheus.definition.compiled.ImmutableJson;

public record RuntimeBehaviour(
        String speech,
        ImmutableJson nonVerbal,
        ImmutableJson motion,
        ImmutableJson display) {

    public static RuntimeBehaviour speechOnly(String speech) {
        return new RuntimeBehaviour(speech, null, null, null);
    }

    public boolean isEmpty() {
        return (this.speech == null || this.speech.isBlank())
                && this.nonVerbal == null && this.motion == null && this.display == null;
    }

    public String toJson() {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        put(result, "speech", this.speech == null ? null : JsonNodeFactory.instance.textNode(this.speech));
        put(result, "nonVerbal", value(this.nonVerbal));
        put(result, "motion", value(this.motion));
        put(result, "display", value(this.display));
        return result.toString();
    }

    private static JsonNode value(ImmutableJson value) {
        return value == null ? null : value.value();
    }

    private static void put(ObjectNode target, String field, JsonNode value) {
        target.set(field, value == null ? JsonNodeFactory.instance.nullNode() : value);
    }
}
