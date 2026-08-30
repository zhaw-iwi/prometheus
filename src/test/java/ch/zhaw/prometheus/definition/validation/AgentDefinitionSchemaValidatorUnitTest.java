package ch.zhaw.prometheus.definition.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;

class AgentDefinitionSchemaValidatorUnitTest {
    private static final String FIXTURE_ROOT = "/agent-definitions/";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentDefinitionSchemaValidator validator = new AgentDefinitionSchemaValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "valid/minimal-single-state.json",
            "valid/composite-flow.json",
            "valid/deterministic-components.json"
    })
    void validSchemaVersionOneFixturesPass(String fixture) throws IOException {
        assertEquals(List.of(), this.validator.validate(readFixture(fixture)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "$schema", "schemaVersion", "key", "revision", "metadata", "interaction", "lifecycle", "storage",
            "resources", "states", "transitions"
    })
    void everyRequiredRootSectionIsEnforced(String property) throws IOException {
        ObjectNode document = (ObjectNode) readFixture("valid/minimal-single-state.json");
        document.remove(property);

        List<SchemaViolation> violations = this.validator.validate(document);

        assertTrue(violations.stream().anyMatch(violation -> violation.pointer().equals("")
                && violation.keyword().equals("required")), () -> "violations were " + violations);
    }

    @Test
    void acceptsEveryExistingObservationAndBehaviourModalityIdentifier() throws IOException {
        ObjectNode document = (ObjectNode) readFixture("valid/minimal-single-state.json");
        ObjectNode interaction = (ObjectNode) document.get("interaction");
        interaction.set("supportedObservations", this.objectMapper.valueToTree(List.of(
                AgentInteractionProfile.OBS_USER_UTTERANCE,
                AgentInteractionProfile.OBS_FACE_EMOTION,
                AgentInteractionProfile.OBS_HUMAN_PRESENCE,
                AgentInteractionProfile.OBS_SOCIAL_GROUPING,
                AgentInteractionProfile.OBS_SOCIAL_CONTEXT,
                AgentInteractionProfile.OBS_SOCIAL_SITUATION_CHANGE,
                AgentInteractionProfile.OBS_HAND_SIGN,
                AgentInteractionProfile.OBS_WEATHER_CURRENT,
                AgentInteractionProfile.OBS_WEATHER_FORECAST)));
        interaction.set("supportedBehaviourModalities", this.objectMapper.valueToTree(List.of(
                AgentInteractionProfile.MODALITY_SPEECH,
                AgentInteractionProfile.MODALITY_NONVERBAL_GESTURE,
                AgentInteractionProfile.MODALITY_NONVERBAL_FACIAL_EXPRESSION,
                AgentInteractionProfile.MODALITY_NONVERBAL_GAZE,
                AgentInteractionProfile.MODALITY_NONVERBAL_MOTION,
                AgentInteractionProfile.MODALITY_MOTION_HAND_SIGN,
                AgentInteractionProfile.MODALITY_DISPLAY)));

        assertEquals(List.of(), this.validator.validate(document));
    }

    @Test
    void revisionAndComponentVersionsMustBePositiveIntegers() throws IOException {
        ObjectNode badRevision = (ObjectNode) readFixture("valid/minimal-single-state.json");
        badRevision.put("revision", 0);
        ObjectNode badComponentVersion = (ObjectNode) readFixture("valid/minimal-single-state.json");
        ((ObjectNode) badComponentVersion.at("/states/0/policy")).put("version", 0);

        assertTrue(this.validator.validate(badRevision).stream()
                .anyMatch(violation -> violation.pointer().equals("/revision")
                        && violation.keyword().equals("minimum")));
        assertTrue(this.validator.validate(badComponentVersion).stream()
                .anyMatch(violation -> violation.pointer().equals("/states/0/policy/version")
                        && violation.keyword().equals("minimum")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidFixtures")
    void invalidFixturesFailAtTheirStructuralBoundary(String fixture, String expectedPointer,
            String expectedKeyword) throws IOException {
        List<SchemaViolation> violations = this.validator.validate(readFixture(fixture));

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(violation -> violation.pointer().equals(expectedPointer)
                && violation.keyword().equals(expectedKeyword)), () -> "violations were " + violations);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "class", "CLASS", "className", "ClassName", "bean", "beanName", "script", "Scripts", "sourceCode"
    })
    void componentConfigRejectsExecutableOrImplementationEscapeHatches(String forbiddenField) throws IOException {
        JsonNode document = readFixture("valid/minimal-single-state.json");
        ObjectNode config = (ObjectNode) document.at("/states/0/policy/config");
        config.put(forbiddenField, "untrusted.Value");

        List<SchemaViolation> violations = this.validator.validate(document);

        assertTrue(violations.stream().anyMatch(violation -> violation.pointer().equals("/states/0/policy/config")
                && violation.keyword().equals("propertyNames")), () -> "violations were " + violations);
    }

    @Test
    void checkedInSchemaLoadsWithoutSpringDatabaseOrProviderConfiguration() throws IOException {
        assertEquals(List.of(), this.validator.validate(readFixture("valid/minimal-single-state.json")));
    }

    private JsonNode readFixture(String fixture) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(FIXTURE_ROOT + fixture)) {
            if (input == null) {
                throw new IOException("Missing fixture " + fixture);
            }
            return this.objectMapper.readTree(input);
        }
    }

    private static Stream<Arguments> invalidFixtures() {
        return Stream.of(
                Arguments.of("invalid/missing-root-section.json", "", "required"),
                Arguments.of("invalid/unknown-root-field.json", "", "additionalProperties"),
                Arguments.of("invalid/unsupported-schema-version.json", "/schemaVersion", "const"),
                Arguments.of("invalid/malformed-atomic-state.json", "/states/0", "oneOf"),
                Arguments.of("invalid/malformed-composite-state.json", "/states/0", "oneOf"),
                Arguments.of("invalid/malformed-final-state.json", "/states/0", "oneOf"),
                Arguments.of("invalid/malformed-storage-schema.json", "/storage/0/valueSchema/type", "enum"),
                Arguments.of("invalid/escape-hatch-component.json", "/states/0/policy/config", "propertyNames"));
    }
}
