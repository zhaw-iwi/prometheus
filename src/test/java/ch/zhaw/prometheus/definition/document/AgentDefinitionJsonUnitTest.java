package ch.zhaw.prometheus.definition.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ch.zhaw.prometheus.definition.validation.AgentDefinitionSchemaException;

class AgentDefinitionJsonUnitTest {
    private static final String FIXTURE_ROOT = "/agent-definitions/";

    private final AgentDefinitionJson definitionJson = new AgentDefinitionJson();

    @Test
    void mapsEveryStateVariantToItsTypedRecord() throws IOException {
        AgentDefinitionDocument document = parseFixture("valid/composite-flow.json");

        assertEquals(3, document.states().size());
        CompositeStateDefinition composite = assertInstanceOf(CompositeStateDefinition.class,
                document.states().get(0));
        assertEquals(List.of("conversation", "done"), composite.childStateIds());
        assertInstanceOf(AtomicStateDefinition.class, document.states().get(1));
        assertInstanceOf(FinalStateDefinition.class, document.states().get(2));
    }

    @Test
    void mapsStorageResourcesPromptSectionsAndVerificationRecords() throws IOException {
        AgentDefinitionDocument document = parseFixture("valid/deterministic-components.json");

        assertEquals("round_count", document.storage().get(0).key());
        assertEquals("signs", document.resources().get(0).id());
        PromptDefinition responsePrompt = new com.fasterxml.jackson.databind.ObjectMapper().treeToValue(
                document.states().stream()
                        .map(AtomicStateDefinition.class::cast)
                        .findFirst()
                        .orElseThrow()
                        .policy()
                        .config()
                        .get("responsePrompt"),
                PromptDefinition.class);
        assertEquals("response.objective", responsePrompt.sections().get(0).id());
        assertEquals("One deterministic round", document.verification().scenarios().get(0).name());
    }

    @Test
    void canonicalizesSetLikeCapabilitiesAndTagsInFirstOccurrenceOrder() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode source = (ObjectNode) objectMapper.readTree(readFixture("valid/minimal-single-state.json"));
        source.withObject("/metadata").withArray("tags").add("test.fixture");
        source.withObject("/interaction").withArray("supportedObservations")
                .add("obs.user_utterance").add("obs.user_utterance");
        source.withObject("/interaction").withArray("supportedBehaviourModalities").add("speech").add("speech");
        source.withObject("/interaction").withArray("profileTags").add("test.fixture").add("test.fixture");

        AgentDefinitionDocument document = this.definitionJson.parse(objectMapper.writeValueAsString(source));

        assertEquals(List.of("test.fixture"), document.metadata().tags());
        assertEquals(List.of("obs.user_utterance"), document.interaction().supportedObservations());
        assertEquals(List.of("speech"), document.interaction().supportedBehaviourModalities());
        assertEquals(List.of("test.fixture"), document.interaction().profileTags());
    }

    @Test
    void canonicalSerializationRoundTripsAndProducesAStableSha256Hash() throws IOException {
        String original = readFixture("valid/minimal-single-state.json");
        AgentDefinitionDocument document = this.definitionJson.parse(original);

        String canonical = this.definitionJson.canonicalJson(document);
        AgentDefinitionDocument reparsed = this.definitionJson.parse(canonical);
        String reordered = reverseRootPropertyOrder(original);

        assertEquals(document, reparsed);
        assertEquals(this.definitionJson.contentHash(document),
                this.definitionJson.contentHash(this.definitionJson.parse(reordered)));
        assertTrue(this.definitionJson.contentHash(document).matches("[0-9a-f]{64}"));
        assertTrue(canonical.indexOf("\"$schema\"") < canonical.indexOf("\"interaction\""));
    }

    @Test
    void canonicalHashNormalizesPromptSectionLineEndings() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode crlfTree = (ObjectNode) objectMapper.readTree(
                readFixture("valid/deterministic-components.json"));
        ObjectNode lfTree = crlfTree.deepCopy();
        ((ObjectNode) crlfTree.at("/states/0/policy/config/responsePrompt/sections/0"))
                .put("content", "Describe the deterministic\r\nresult.");
        ((ObjectNode) lfTree.at("/states/0/policy/config/responsePrompt/sections/0"))
                .put("content", "Describe the deterministic\nresult.");
        AgentDefinitionDocument crlf = this.definitionJson.parse(objectMapper.writeValueAsString(crlfTree));
        AgentDefinitionDocument lf = this.definitionJson.parse(objectMapper.writeValueAsString(lfTree));

        assertEquals(this.definitionJson.contentHash(crlf), this.definitionJson.contentHash(lf));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "valid/minimal-single-state.json",
            "valid/composite-flow.json",
            "valid/deterministic-components.json"
    })
    void canonicalSerializationOfEveryValidFixtureRemainsSchemaValid(String fixture) throws IOException {
        AgentDefinitionDocument document = parseFixture(fixture);

        assertEquals(document, this.definitionJson.parse(this.definitionJson.canonicalJson(document)));
    }

    @Test
    void rejectsUnknownFieldsAndUnsupportedSchemaVersionsBeforeMapping() throws IOException {
        assertThrows(AgentDefinitionSchemaException.class,
                () -> this.definitionJson.parse(readFixture("invalid/unknown-root-field.json")));
        assertThrows(AgentDefinitionSchemaException.class,
                () -> this.definitionJson.parse(readFixture("invalid/unsupported-schema-version.json")));
    }

    @Test
    void rejectsMalformedAndTrailingJson() {
        assertThrows(AgentDefinitionFormatException.class, () -> this.definitionJson.parse((String) null));
        assertThrows(AgentDefinitionFormatException.class, () -> this.definitionJson.parse("  "));
        assertThrows(AgentDefinitionFormatException.class, () -> this.definitionJson.parse("{"));
        assertThrows(AgentDefinitionFormatException.class, () -> this.definitionJson.parse("{} {}"));
    }

    private AgentDefinitionDocument parseFixture(String fixture) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(FIXTURE_ROOT + fixture)) {
            if (input == null) {
                throw new IOException("Missing fixture " + fixture);
            }
            return this.definitionJson.parse(input);
        }
    }

    private String readFixture(String fixture) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(FIXTURE_ROOT + fixture)) {
            if (input == null) {
                throw new IOException("Missing fixture " + fixture);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String reverseRootPropertyOrder(String json) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode source = objectMapper.readTree(json);
        List<String> propertyNames = new ArrayList<>();
        source.fieldNames().forEachRemaining(propertyNames::add);
        Collections.reverse(propertyNames);
        ObjectNode reversed = objectMapper.createObjectNode();
        propertyNames.forEach(name -> reversed.set(name, source.get(name)));
        return objectMapper.writeValueAsString(reversed);
    }
}
