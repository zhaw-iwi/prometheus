package ch.zhaw.prometheus.definition.validation;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

final class EmbeddedValueSchemas {
    private static final SchemaRegistry SCHEMA_REGISTRY = SchemaRegistry
            .withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

    private EmbeddedValueSchemas() {
    }

    static boolean accepts(JsonNode schemaNode, JsonNode value) {
        Schema schema = SCHEMA_REGISTRY.getSchema(schemaNode);
        return schema.validate(value).isEmpty();
    }

    /** Returns true when every value accepted by source is compatible with target. */
    static boolean isAssignable(JsonNode source, JsonNode target) {
        if (source == null || target == null) {
            return true;
        }
        if (source.has("const")) {
            return accepts(target, source.get("const"));
        }
        if (source.has("enum")) {
            for (JsonNode value : source.get("enum")) {
                if (!accepts(target, value)) {
                    return false;
                }
            }
            return true;
        }
        if (target.has("const") || target.has("enum")) {
            return false;
        }

        String sourceType = source.path("type").asText();
        String targetType = target.path("type").asText();
        if (!sourceType.equals(targetType) && !("integer".equals(sourceType) && "number".equals(targetType))) {
            return false;
        }
        if ("array".equals(sourceType)) {
            if (target.has("items") && !source.has("items")) {
                return false;
            }
            if (source.has("items") && target.has("items")
                    && !isAssignable(source.get("items"), target.get("items"))) {
                return false;
            }
            return boundsAreAssignable(source, target);
        }
        if (!"object".equals(sourceType)) {
            return boundsAreAssignable(source, target);
        }

        Set<String> sourceRequired = stringSet(source.get("required"));
        Set<String> targetRequired = stringSet(target.get("required"));
        if (!sourceRequired.containsAll(targetRequired)) {
            return false;
        }
        JsonNode sourceProperties = source.path("properties");
        JsonNode targetProperties = target.path("properties");
        for (String property : targetRequired) {
            if (!sourceProperties.has(property) || !targetProperties.has(property)
                    || !isAssignable(sourceProperties.get(property), targetProperties.get(property))) {
                return false;
            }
        }
        Iterator<Map.Entry<String, JsonNode>> targetFields = targetProperties.fields();
        while (targetFields.hasNext()) {
            Map.Entry<String, JsonNode> field = targetFields.next();
            if (sourceProperties.has(field.getKey())
                    && !isAssignable(sourceProperties.get(field.getKey()), field.getValue())) {
                return false;
            }
        }
        if (target.path("additionalProperties").isBoolean()
                && !target.path("additionalProperties").asBoolean()) {
            if (!source.path("additionalProperties").isBoolean()
                    || source.path("additionalProperties").asBoolean()) {
                return false;
            }
            Iterator<Map.Entry<String, JsonNode>> sourceFields = sourceProperties.fields();
            while (sourceFields.hasNext()) {
                Map.Entry<String, JsonNode> field = sourceFields.next();
                if (!targetProperties.has(field.getKey())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean boundsAreAssignable(JsonNode source, JsonNode target) {
        if (target.has("minimum")
                && (!source.has("minimum") || source.get("minimum").decimalValue()
                        .compareTo(target.get("minimum").decimalValue()) < 0)) {
            return false;
        }
        if (target.has("maximum")
                && (!source.has("maximum") || source.get("maximum").decimalValue()
                        .compareTo(target.get("maximum").decimalValue()) > 0)) {
            return false;
        }
        if (target.has("minLength")
                && (!source.has("minLength") || source.get("minLength").asInt() < target.get("minLength").asInt())) {
            return false;
        }
        if (target.has("maxLength")
                && (!source.has("maxLength") || source.get("maxLength").asInt() > target.get("maxLength").asInt())) {
            return false;
        }
        if (target.has("minItems")
                && (!source.has("minItems") || source.get("minItems").asInt() < target.get("minItems").asInt())) {
            return false;
        }
        if (target.has("maxItems")
                && (!source.has("maxItems") || source.get("maxItems").asInt() > target.get("maxItems").asInt())) {
            return false;
        }
        return true;
    }

    private static Set<String> stringSet(JsonNode values) {
        if (values == null || !values.isArray()) {
            return Set.of();
        }
        Set<String> result = new HashSet<>();
        values.forEach(value -> result.add(value.asText()));
        return result;
    }
}
