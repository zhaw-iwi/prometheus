package ch.zhaw.prometheus.definition.document;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ch.zhaw.prometheus.definition.validation.AgentDefinitionSchemaValidator;
import ch.zhaw.prometheus.definition.prompt.PromptComposer;

public final class AgentDefinitionJson {
    private final ObjectMapper objectMapper;
    private final AgentDefinitionSchemaValidator schemaValidator;

    public AgentDefinitionJson() {
        this(createObjectMapper(), new AgentDefinitionSchemaValidator());
    }

    AgentDefinitionJson(ObjectMapper objectMapper, AgentDefinitionSchemaValidator schemaValidator) {
        this.objectMapper = objectMapper;
        this.schemaValidator = schemaValidator;
    }

    public AgentDefinitionDocument parse(String json) {
        if (json == null || json.isBlank()) {
            throw new AgentDefinitionFormatException("Agent definition JSON must not be blank", null);
        }
        try {
            return parse(this.objectMapper.readTree(json));
        } catch (JsonProcessingException exception) {
            throw new AgentDefinitionFormatException("Agent definition is not valid JSON", exception);
        }
    }

    public AgentDefinitionDocument parse(InputStream input) {
        if (input == null) {
            throw new AgentDefinitionFormatException("Agent definition input must not be null", null);
        }
        try {
            return parse(this.objectMapper.readTree(input));
        } catch (IOException exception) {
            throw new AgentDefinitionFormatException("Unable to read agent definition JSON", exception);
        }
    }

    public String canonicalJson(AgentDefinitionDocument document) {
        try {
            JsonNode tree = this.objectMapper.valueToTree(document);
            return this.objectMapper.writeValueAsString(sortObjectProperties(tree));
        } catch (JsonProcessingException exception) {
            throw new AgentDefinitionFormatException("Unable to serialize agent definition JSON", exception);
        }
    }

    public String contentHash(AgentDefinitionDocument document) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalJson(document).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private AgentDefinitionDocument parse(JsonNode tree) {
        if (tree == null) {
            throw new AgentDefinitionFormatException("Agent definition JSON must contain a document", null);
        }
        this.schemaValidator.requireValid(tree);
        try {
            return this.objectMapper.treeToValue(tree, AgentDefinitionDocument.class);
        } catch (JsonProcessingException exception) {
            throw new AgentDefinitionFormatException("Agent definition does not match the document model", exception);
        }
    }

    private static JsonNode sortObjectProperties(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = JsonNodeFactory.instance.objectNode();
            List<Map.Entry<String, JsonNode>> fields = new ArrayList<>();
            node.fields().forEachRemaining(fields::add);
            fields.sort(Comparator.comparing(Map.Entry::getKey));
            fields.forEach(field -> {
                JsonNode value = field.getValue();
                if ("content".equals(field.getKey()) && value.isTextual() && looksLikePromptSection(node)) {
                    value = JsonNodeFactory.instance.textNode(PromptComposer.normalizeLineEndings(value.asText()));
                }
                sorted.set(field.getKey(), sortObjectProperties(value));
            });
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode sorted = JsonNodeFactory.instance.arrayNode();
            node.forEach(value -> sorted.add(sortObjectProperties(value)));
            return sorted;
        }
        return node;
    }

    private static boolean looksLikePromptSection(JsonNode node) {
        return node.path("id").isTextual() && node.path("kind").isTextual() && node.path("content").isTextual();
    }

    private static ObjectMapper createObjectMapper() {
        return JsonMapper.builder()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
    }
}
