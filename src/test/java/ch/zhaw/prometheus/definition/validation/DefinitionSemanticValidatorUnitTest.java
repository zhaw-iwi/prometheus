package ch.zhaw.prometheus.definition.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ch.zhaw.prometheus.definition.document.AgentDefinitionDocument;
import ch.zhaw.prometheus.definition.document.AgentDefinitionJson;
import ch.zhaw.prometheus.definition.document.ComponentEnvelope;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;

class DefinitionSemanticValidatorUnitTest {
    private static final String FIXTURE_ROOT = "/agent-definitions/";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentDefinitionJson definitionJson = new AgentDefinitionJson();

    @Test
    void minimalDefinitionIsSemanticallyValid() throws IOException {
        DefinitionValidationResult result = validator().validate(parse("valid/minimal-single-state.json"));

        assertTrue(result.isValid());
        assertEquals(List.of(), result.diagnostics());
    }

    @Test
    void transitionCyclesAndSelfTransitionsAreValidAndReachable() throws IOException {
        ObjectNode document = tree("valid/minimal-single-state.json");
        ObjectNode other = ((ObjectNode) document.at("/states/0")).deepCopy();
        other.put("id", "other");
        other.put("name", "Other");
        ((ArrayNode) document.get("states")).add(other);
        ArrayNode transitions = (ArrayNode) document.get("transitions");
        transitions.add(transition("to_other", "main", "other", 10));
        transitions.add(transition("to_main", "other", "main", 10));
        transitions.add(transition("repeat_other", "other", "other", 20));

        DefinitionValidationResult result = validator().validate(parse(document));

        assertTrue(result.isValid(), () -> result.diagnostics().toString());
        assertEquals(List.of(), result.warnings());
    }

    @Test
    void rejectsContainmentCycleAndMultipleParentsAtStablePointers() throws IOException {
        ObjectNode cycle = tree("valid/minimal-single-state.json");
        ArrayNode states = this.objectMapper.createArrayNode();
        states.add(composite("first", "second"));
        states.add(composite("second", "first"));
        cycle.set("states", states);
        ((ObjectNode) cycle.get("lifecycle")).put("initialStateId", "first");

        ObjectNode multipleParents = tree("valid/composite-flow.json");
        ((ArrayNode) multipleParents.get("states")).add(composite("other_parent", "conversation"));

        DefinitionValidationResult cycleResult = validator().validate(parse(cycle));
        DefinitionValidationResult parentResult = validator().validate(parse(multipleParents));

        assertDiagnostic(cycleResult, SemanticDiagnosticCode.CONTAINMENT_CYCLE,
                "/states/1/childStateIds/0");
        assertDiagnostic(parentResult, SemanticDiagnosticCode.MULTIPLE_STATE_PARENTS,
                "/states/3/childStateIds/0");
    }

    @Test
    void rejectsMissingInitialChildDanglingTargetFinalOutgoingAndDuplicateOrder() throws IOException {
        ObjectNode document = tree("valid/composite-flow.json");
        ((ObjectNode) document.at("/states/0")).put("initialChildStateId", "unknown_child");
        ((ObjectNode) document.at("/transitions/0")).put("targetStateId", "unknown_target");
        ArrayNode transitions = (ArrayNode) document.get("transitions");
        transitions.add(transition("from_final", "done", "conversation", 10));
        transitions.add(transition("duplicate_order", "conversation", "conversation", 10));

        DefinitionValidationResult result = validator().validate(parse(document));

        assertDiagnostic(result, SemanticDiagnosticCode.INVALID_INITIAL_CHILD,
                "/states/0/initialChildStateId");
        assertDiagnostic(result, SemanticDiagnosticCode.MISSING_TRANSITION_TARGET,
                "/transitions/0/targetStateId");
        assertDiagnostic(result, SemanticDiagnosticCode.FINAL_STATE_OUTGOING_TRANSITION,
                "/transitions/1/sourceStateId");
        assertDiagnostic(result, SemanticDiagnosticCode.DUPLICATE_TRANSITION_ORDER,
                "/transitions/2/order");
    }

    @Test
    void unreachableStateIsANonBlockingWarning() throws IOException {
        ObjectNode document = tree("valid/minimal-single-state.json");
        ObjectNode unused = ((ObjectNode) document.at("/states/0")).deepCopy();
        unused.put("id", "unused");
        unused.put("name", "Unused");
        ((ArrayNode) document.get("states")).add(unused);

        DefinitionValidationResult result = validator().validate(parse(document));

        assertTrue(result.isValid());
        assertDiagnostic(result, SemanticDiagnosticCode.UNREACHABLE_STATE, "/states/1/id");
    }

    @Test
    void unusedCapabilitiesAreNonBlockingWarningsAtCapabilityPointers() throws IOException {
        ObjectNode document = tree("valid/minimal-single-state.json");
        ((ArrayNode) document.at("/interaction/supportedObservations"))
                .add(AgentInteractionProfile.OBS_FACE_EMOTION);
        ((ArrayNode) document.at("/interaction/supportedBehaviourModalities"))
                .add(AgentInteractionProfile.MODALITY_DISPLAY);

        DefinitionValidationResult result = validator().validate(parse(document));

        assertTrue(result.isValid());
        assertDiagnostic(result, SemanticDiagnosticCode.UNUSED_OBSERVATION,
                "/interaction/supportedObservations/0");
        assertDiagnostic(result, SemanticDiagnosticCode.UNUSED_BEHAVIOUR_MODALITY,
                "/interaction/supportedBehaviourModalities/0");
    }

    @Test
    void rejectsDuplicateIdsAndKeysAtTheSecondDeclaration() throws IOException {
        ObjectNode document = tree("valid/minimal-single-state.json");
        ((ArrayNode) document.get("states")).add(document.at("/states/0").deepCopy());
        ((ArrayNode) document.get("transitions")).add(transition("duplicate", "main", "main", 10));
        ((ArrayNode) document.get("transitions")).add(transition("duplicate", "main", "main", 20));
        ((ArrayNode) document.get("storage")).add(storage("value", "string", false, "one"));
        ((ArrayNode) document.get("storage")).add(storage("value", "string", false, "two"));
        ObjectNode resource = this.objectMapper.createObjectNode()
                .put("id", "data")
                .put("kind", "test.resource.inline")
                .put("version", 1);
        resource.set("config", this.objectMapper.createObjectNode());
        ((ArrayNode) document.get("resources")).add(resource);
        ((ArrayNode) document.get("resources")).add(resource.deepCopy());

        DefinitionValidationResult result = validator().validate(parse(document));

        assertDiagnostic(result, SemanticDiagnosticCode.DUPLICATE_STATE_ID, "/states/1/id");
        assertDiagnostic(result, SemanticDiagnosticCode.DUPLICATE_TRANSITION_ID, "/transitions/1/id");
        assertDiagnostic(result, SemanticDiagnosticCode.DUPLICATE_STORAGE_KEY, "/storage/1/key");
        assertDiagnostic(result, SemanticDiagnosticCode.DUPLICATE_RESOURCE_ID, "/resources/1/id");
    }

    @Test
    void rejectsUnknownContainmentChildAndMissingLifecycleInitialState() throws IOException {
        ObjectNode document = tree("valid/minimal-single-state.json");
        document.set("states", this.objectMapper.createArrayNode().add(composite("root", "missing_child")));
        ((ObjectNode) document.get("lifecycle")).put("initialStateId", "missing_initial");

        DefinitionValidationResult result = validator().validate(parse(document));

        assertDiagnostic(result, SemanticDiagnosticCode.MISSING_CHILD_STATE, "/states/0/childStateIds/0");
        assertDiagnostic(result, SemanticDiagnosticCode.MISSING_INITIAL_STATE, "/lifecycle/initialStateId");
    }

    @Test
    void validatesComponentCapabilityStorageResourceAndStateRequirements() throws IOException {
        ObjectNode document = tree("valid/minimal-single-state.json");
        ObjectNode policy = (ObjectNode) document.at("/states/0/policy");
        policy.put("kind", "test.component.requirements");
        policy.set("config", this.objectMapper.createObjectNode()
                .put("storageKey", "missing_storage")
                .put("resourceId", "missing_resource")
                .put("stateId", "missing_state"));

        DefinitionValidationResult result = validator().validate(parse(document));

        assertDiagnostic(result, SemanticDiagnosticCode.UNDECLARED_OBSERVATION, "/states/0/policy");
        assertDiagnostic(result, SemanticDiagnosticCode.UNDECLARED_BEHAVIOUR_MODALITY, "/states/0/policy");
        assertDiagnostic(result, SemanticDiagnosticCode.MISSING_STORAGE_BINDING,
                "/states/0/policy/config/storageKey");
        assertDiagnostic(result, SemanticDiagnosticCode.MISSING_RESOURCE_REFERENCE,
                "/states/0/policy/config/resourceId");
        assertDiagnostic(result, SemanticDiagnosticCode.MISSING_STATE_REFERENCE,
                "/states/0/policy/config/stateId");
    }

    @Test
    void validatesInitialValuesStorageShapesAndInitializerOwnership() throws IOException {
        ObjectNode invalidInitial = tree("valid/minimal-single-state.json");
        ((ArrayNode) invalidInitial.get("storage")).add(storage("result", "integer", true, "wrong"));

        ObjectNode incompatible = tree("valid/minimal-single-state.json");
        ((ArrayNode) incompatible.get("storage")).add(storage("result", "string", false, "ready"));
        ObjectNode action = this.objectMapper.createObjectNode();
        action.put("kind", "test.action.read-array");
        action.put("version", 1);
        action.set("config", this.objectMapper.createObjectNode().put("storageKey", "result"));
        ObjectNode transition = transition("repeat", "main", "main", 10);
        ((ArrayNode) transition.get("actions")).add(action);
        ((ArrayNode) incompatible.get("transitions")).add(transition);

        DefinitionValidationResult initialResult = validator().validate(parse(invalidInitial));
        DefinitionValidationResult incompatibleResult = validator().validate(parse(incompatible));

        assertDiagnostic(initialResult, SemanticDiagnosticCode.INVALID_STORAGE_INITIAL_VALUE,
                "/storage/0/initialValue");
        assertDiagnostic(incompatibleResult, SemanticDiagnosticCode.INCOMPATIBLE_STORAGE_SCHEMA,
                "/transitions/0/actions/0/config/storageKey");
    }

    @Test
    void requiredStorageNeedsExactlyOneInitialValueProducer() throws IOException {
        ObjectNode uninitialized = tree("valid/minimal-single-state.json");
        ((ArrayNode) uninitialized.get("storage")).add(storage("required_value", "integer", true, null));

        ObjectNode multiplyInitialized = tree("valid/deterministic-components.json");
        ((ObjectNode) multiplyInitialized.at("/storage/0")).put("initialValue", 0);

        DefinitionValidationResult uninitializedResult = validator().validate(parse(uninitialized));
        DefinitionValidationResult multipleResult = validator().validate(parse(multiplyInitialized));

        assertDiagnostic(uninitializedResult, SemanticDiagnosticCode.REQUIRED_STORAGE_UNINITIALIZED, "/storage/0");
        assertDiagnostic(multipleResult, SemanticDiagnosticCode.MULTIPLE_STORAGE_INITIALIZERS, "/storage/0");
    }

    @Test
    void validatesEmbeddedSchemaKeywordContextRequiredPropertiesAndBounds() throws IOException {
        ObjectNode document = tree("valid/minimal-single-state.json");
        ObjectNode schema = this.objectMapper.createObjectNode()
                .put("type", "string")
                .put("minLength", 10)
                .put("maxLength", 2);
        schema.set("properties", this.objectMapper.createObjectNode().set("known",
                this.objectMapper.createObjectNode().put("type", "string")));
        ObjectNode storage = storage("result", "string", false, null);
        storage.set("valueSchema", schema);
        ((ArrayNode) document.get("storage")).add(storage);

        ObjectNode objectStorage = storage("object_result", "object", false, null);
        ObjectNode objectSchema = (ObjectNode) objectStorage.get("valueSchema");
        objectSchema.set("properties", this.objectMapper.createObjectNode());
        objectSchema.set("required", this.objectMapper.createArrayNode().add("missing"));
        ((ArrayNode) document.get("storage")).add(objectStorage);

        DefinitionValidationResult result = validator().validate(parse(document));

        assertDiagnostic(result, SemanticDiagnosticCode.STORAGE_SCHEMA_KEYWORD_MISMATCH,
                "/storage/0/valueSchema/properties");
        assertDiagnostic(result, SemanticDiagnosticCode.STORAGE_SCHEMA_INVALID_BOUNDS,
                "/storage/0/valueSchema/maxLength");
        assertDiagnostic(result, SemanticDiagnosticCode.STORAGE_SCHEMA_REQUIRED_PROPERTY_UNDECLARED,
                "/storage/1/valueSchema/required/0");
    }

    @Test
    void validatesAllPromptRolesWithoutCollapsingThem() throws IOException {
        ObjectNode document = tree("valid/minimal-single-state.json");
        ObjectNode config = this.objectMapper.createObjectNode();
        config.set("responsePrompt", prompt("duplicate", "first", "duplicate", "second"));
        config.set("starterPrompt", prompt("blank", "   "));
        config.set("summaryPrompt", this.objectMapper.createObjectNode().put("sections", "not-an-array"));
        ((ObjectNode) document.at("/states/0/policy")).set("config", config);

        DefinitionValidationResult result = validator().validate(parse(document));

        assertDiagnostic(result, SemanticDiagnosticCode.DUPLICATE_PROMPT_SECTION_ID,
                "/states/0/policy/config/responsePrompt/sections/1/id");
        assertDiagnostic(result, SemanticDiagnosticCode.BLANK_PROMPT_SECTION,
                "/states/0/policy/config/starterPrompt/sections/0/content");
        assertDiagnostic(result, SemanticDiagnosticCode.INVALID_PROMPT_STRUCTURE,
                "/states/0/policy/config/summaryPrompt/sections");
    }

    @Test
    void rejectsOversizedPromptSectionAtItsContentPointer() throws IOException {
        ObjectNode document = tree("valid/minimal-single-state.json");
        ObjectNode config = this.objectMapper.createObjectNode();
        config.set("responsePrompt", prompt("large", "x".repeat(
                DefinitionSemanticValidator.MAX_PROMPT_SECTION_CHARACTERS + 1)));
        ((ObjectNode) document.at("/states/0/policy")).set("config", config);

        DefinitionValidationResult result = validator().validate(parse(document));

        assertDiagnostic(result, SemanticDiagnosticCode.PROMPT_SECTION_TOO_LARGE,
                "/states/0/policy/config/responsePrompt/sections/0/content");
    }

    @Test
    void deterministicFixtureIsValidWhenItsComponentContractsAreSupplied() throws IOException {
        DefinitionValidationResult result = validator().validate(parse("valid/deterministic-components.json"));

        assertTrue(result.isValid(), () -> result.diagnostics().toString());
    }

    private DefinitionSemanticValidator validator() {
        return new DefinitionSemanticValidator(this::semanticsFor);
    }

    private ComponentSemantics semanticsFor(ComponentEnvelope component) {
        JsonNode config = component.config();
        return switch (component.kind()) {
            case "test.component.requirements" -> new ComponentSemantics(
                    Set.of(AgentInteractionProfile.OBS_FACE_EMOTION),
                    Set.of(AgentInteractionProfile.MODALITY_DISPLAY),
                    List.of(new ComponentStorageUse(config.path("storageKey").asText(), ComponentStorageAccess.READ,
                            schema("string"), "/config/storageKey")),
                    List.of(new ComponentReference(config.path("resourceId").asText(), "/config/resourceId")),
                    List.of(new ComponentReference(config.path("stateId").asText(), "/config/stateId")));
            case "test.action.read-array" -> new ComponentSemantics(Set.of(), Set.of(),
                    List.of(new ComponentStorageUse(config.path("storageKey").asText(), ComponentStorageAccess.READ,
                            schema("array"), "/config/storageKey")),
                    List.of(), List.of());
            case "prometheus.initializer.constant" -> new ComponentSemantics(Set.of(), Set.of(),
                    List.of(new ComponentStorageUse(config.path("storageKey").asText(), ComponentStorageAccess.WRITE,
                            schema("integer"), "/config/storageKey")),
                    List.of(), List.of());
            case "prometheus.action.increment" -> new ComponentSemantics(Set.of(), Set.of(),
                    List.of(new ComponentStorageUse(config.path("targetStorageKey").asText(),
                            ComponentStorageAccess.READ_WRITE, schema("integer"), "/config/targetStorageKey")),
                    List.of(), List.of());
            case "prometheus.decision.latest-event-type" -> new ComponentSemantics(
                    Set.of(config.path("eventType").asText()), Set.of(), List.of(), List.of(), List.of());
            default -> ComponentSemantics.none();
        };
    }

    private JsonNode schema(String type) {
        ObjectNode schema = this.objectMapper.createObjectNode().put("type", type);
        if ("array".equals(type)) {
            schema.set("items", this.objectMapper.createObjectNode().put("type", "string"));
        } else if ("integer".equals(type)) {
            schema.put("minimum", 0);
        }
        return schema;
    }

    private AgentDefinitionDocument parse(String fixture) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(FIXTURE_ROOT + fixture)) {
            if (input == null) {
                throw new IOException("Missing fixture " + fixture);
            }
            return this.definitionJson.parse(input);
        }
    }

    private AgentDefinitionDocument parse(ObjectNode document) throws IOException {
        return this.definitionJson.parse(this.objectMapper.writeValueAsString(document));
    }

    private ObjectNode tree(String fixture) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(FIXTURE_ROOT + fixture)) {
            if (input == null) {
                throw new IOException("Missing fixture " + fixture);
            }
            return (ObjectNode) this.objectMapper.readTree(input);
        }
    }

    private ObjectNode transition(String id, String source, String target, int order) {
        ObjectNode transition = this.objectMapper.createObjectNode();
        transition.put("id", id);
        transition.put("sourceStateId", source);
        transition.put("targetStateId", target);
        transition.put("order", order);
        transition.set("decisions", this.objectMapper.createArrayNode());
        transition.set("actions", this.objectMapper.createArrayNode());
        return transition;
    }

    private ObjectNode composite(String id, String childId) {
        ObjectNode composite = this.objectMapper.createObjectNode();
        composite.put("id", id);
        composite.put("name", id);
        composite.put("kind", "composite");
        composite.put("entryMode", "start");
        composite.put("oblivious", false);
        composite.putNull("eventSelector");
        composite.putNull("policy");
        composite.set("childStateIds", this.objectMapper.createArrayNode().add(childId));
        composite.put("initialChildStateId", childId);
        return composite;
    }

    private ObjectNode storage(String key, String type, boolean required, String initialValue) {
        ObjectNode storage = this.objectMapper.createObjectNode();
        storage.put("key", key);
        storage.set("valueSchema", this.objectMapper.createObjectNode().put("type", type));
        storage.put("required", required);
        storage.put("visibility", "internal");
        storage.put("reset", "initial");
        if (initialValue != null) {
            storage.put("initialValue", initialValue);
        }
        return storage;
    }

    private ObjectNode prompt(String id, String content) {
        ObjectNode prompt = this.objectMapper.createObjectNode();
        ArrayNode sections = this.objectMapper.createArrayNode();
        sections.add(section(id, content));
        prompt.set("sections", sections);
        return prompt;
    }

    private ObjectNode prompt(String firstId, String firstContent, String secondId, String secondContent) {
        ObjectNode prompt = prompt(firstId, firstContent);
        ((ArrayNode) prompt.get("sections")).add(section(secondId, secondContent));
        return prompt;
    }

    private ObjectNode section(String id, String content) {
        return this.objectMapper.createObjectNode()
                .put("id", id)
                .put("kind", "objective")
                .put("content", content);
    }

    private static void assertDiagnostic(DefinitionValidationResult result, SemanticDiagnosticCode code,
            String pointer) {
        assertFalse(result.diagnostics().isEmpty());
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code() == code && diagnostic.pointer().equals(pointer)),
                () -> "diagnostics were " + result.diagnostics());
    }
}
