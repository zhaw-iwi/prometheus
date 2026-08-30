package ch.zhaw.prometheus.definition.validation;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

import ch.zhaw.prometheus.definition.document.AgentDefinitionDocument;
import ch.zhaw.prometheus.definition.document.AtomicStateDefinition;
import ch.zhaw.prometheus.definition.document.ComponentEnvelope;
import ch.zhaw.prometheus.definition.document.CompositeStateDefinition;
import ch.zhaw.prometheus.definition.document.DefinitionResource;
import ch.zhaw.prometheus.definition.document.FinalStateDefinition;
import ch.zhaw.prometheus.definition.document.StateDefinition;
import ch.zhaw.prometheus.definition.document.StorageDefinition;
import ch.zhaw.prometheus.definition.document.TransitionDefinition;

public final class DefinitionSemanticValidator {
    public static final int MAX_PROMPT_SECTION_CHARACTERS = 20_000;
    public static final int MAX_PROMPT_CHARACTERS = 100_000;
    public static final int MAX_DEFINITION_PROMPT_CHARACTERS = 500_000;
    private static final Pattern STABLE_ID = Pattern.compile("^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$");

    private final ComponentSemanticsResolver componentSemanticsResolver;

    public DefinitionSemanticValidator() {
        this(ComponentSemanticsResolver.none());
    }

    public DefinitionSemanticValidator(ComponentSemanticsResolver componentSemanticsResolver) {
        this.componentSemanticsResolver = componentSemanticsResolver == null
                ? ComponentSemanticsResolver.none()
                : componentSemanticsResolver;
    }

    public DefinitionValidationResult validate(AgentDefinitionDocument definition) {
        if (definition == null) {
            throw new IllegalArgumentException("definition must not be null");
        }

        List<ValidationDiagnostic> diagnostics = new ArrayList<>();
        Map<String, StateRef> states = indexStates(definition, diagnostics);
        Map<String, StorageRef> storage = indexStorage(definition, diagnostics);
        Set<String> resources = indexResources(definition, diagnostics);
        List<LocatedComponent> components = locateComponents(definition);

        Map<String, ParentRef> parents = validateContainment(definition, states, diagnostics);
        validateInitialState(definition, states, parents, diagnostics);
        validateTransitions(definition, states, diagnostics);
        validateReachability(definition, states, parents, diagnostics);
        validateValueSchemas(storage, diagnostics);
        validateComponents(definition, components, states, storage, resources, diagnostics);
        validatePrompts(components, diagnostics);

        return new DefinitionValidationResult(diagnostics);
    }

    private static Map<String, StateRef> indexStates(AgentDefinitionDocument definition,
            List<ValidationDiagnostic> diagnostics) {
        Map<String, StateRef> states = new LinkedHashMap<>();
        for (int index = 0; index < definition.states().size(); index++) {
            StateDefinition state = definition.states().get(index);
            String pointer = "/states/" + index;
            StateRef previous = states.putIfAbsent(state.id(), new StateRef(index, state));
            if (previous != null) {
                diagnostics.add(diagnostic(SemanticDiagnosticCode.DUPLICATE_STATE_ID, pointer + "/id",
                        "State ID '" + state.id() + "' is already used.",
                        "Choose a unique stable state ID."));
            }
        }
        return states;
    }

    private static Map<String, StorageRef> indexStorage(AgentDefinitionDocument definition,
            List<ValidationDiagnostic> diagnostics) {
        Map<String, StorageRef> storage = new LinkedHashMap<>();
        for (int index = 0; index < definition.storage().size(); index++) {
            StorageDefinition declaration = definition.storage().get(index);
            StorageRef previous = storage.putIfAbsent(declaration.key(), new StorageRef(index, declaration));
            if (previous != null) {
                diagnostics.add(diagnostic(SemanticDiagnosticCode.DUPLICATE_STORAGE_KEY,
                        "/storage/" + index + "/key",
                        "Storage key '" + declaration.key() + "' is already declared.",
                        "Choose a unique storage key."));
            }
        }
        return storage;
    }

    private static Set<String> indexResources(AgentDefinitionDocument definition,
            List<ValidationDiagnostic> diagnostics) {
        Set<String> resources = new HashSet<>();
        for (int index = 0; index < definition.resources().size(); index++) {
            DefinitionResource resource = definition.resources().get(index);
            if (!resources.add(resource.id())) {
                diagnostics.add(diagnostic(SemanticDiagnosticCode.DUPLICATE_RESOURCE_ID,
                        "/resources/" + index + "/id",
                        "Resource ID '" + resource.id() + "' is already used.",
                        "Choose a unique resource ID."));
            }
        }
        return resources;
    }

    private static Map<String, ParentRef> validateContainment(AgentDefinitionDocument definition,
            Map<String, StateRef> states, List<ValidationDiagnostic> diagnostics) {
        Map<String, ParentRef> parents = new HashMap<>();
        for (int stateIndex = 0; stateIndex < definition.states().size(); stateIndex++) {
            StateDefinition state = definition.states().get(stateIndex);
            if (!(state instanceof CompositeStateDefinition composite)) {
                continue;
            }
            for (int childIndex = 0; childIndex < composite.childStateIds().size(); childIndex++) {
                String childId = composite.childStateIds().get(childIndex);
                String pointer = "/states/" + stateIndex + "/childStateIds/" + childIndex;
                if (!states.containsKey(childId)) {
                    diagnostics.add(diagnostic(SemanticDiagnosticCode.MISSING_CHILD_STATE, pointer,
                            "Composite state references unknown child '" + childId + "'.",
                            "Select an existing state ID."));
                    continue;
                }
                ParentRef previous = parents.putIfAbsent(childId, new ParentRef(composite.id()));
                if (previous != null && !previous.parentId().equals(composite.id())) {
                    diagnostics.add(diagnostic(SemanticDiagnosticCode.MULTIPLE_STATE_PARENTS, pointer,
                            "State '" + childId + "' already belongs to composite state '" + previous.parentId()
                                    + "'.",
                            "A state may have only one composite parent."));
                }
            }
            if (!composite.childStateIds().contains(composite.initialChildStateId())) {
                diagnostics.add(diagnostic(SemanticDiagnosticCode.INVALID_INITIAL_CHILD,
                        "/states/" + stateIndex + "/initialChildStateId",
                        "Initial child '" + composite.initialChildStateId() + "' is not a direct child.",
                        "Choose one of this composite state's childStateIds."));
            }
        }

        Map<String, VisitColor> colors = new HashMap<>();
        for (String stateId : states.keySet()) {
            detectContainmentCycles(stateId, states, colors, diagnostics);
        }
        return parents;
    }

    private static void detectContainmentCycles(String stateId, Map<String, StateRef> states,
            Map<String, VisitColor> colors, List<ValidationDiagnostic> diagnostics) {
        VisitColor color = colors.getOrDefault(stateId, VisitColor.WHITE);
        if (color != VisitColor.WHITE) {
            return;
        }
        colors.put(stateId, VisitColor.GRAY);
        StateRef stateRef = states.get(stateId);
        if (stateRef != null && stateRef.state() instanceof CompositeStateDefinition composite) {
            for (int childIndex = 0; childIndex < composite.childStateIds().size(); childIndex++) {
                String childId = composite.childStateIds().get(childIndex);
                if (!states.containsKey(childId)) {
                    continue;
                }
                if (colors.getOrDefault(childId, VisitColor.WHITE) == VisitColor.GRAY) {
                    diagnostics.add(diagnostic(SemanticDiagnosticCode.CONTAINMENT_CYCLE,
                            "/states/" + stateRef.index() + "/childStateIds/" + childIndex,
                            "Composite containment creates a cycle through state '" + childId + "'.",
                            "Remove the cyclic containment edge; transition cycles remain allowed."));
                } else {
                    detectContainmentCycles(childId, states, colors, diagnostics);
                }
            }
        }
        colors.put(stateId, VisitColor.BLACK);
    }

    private static void validateInitialState(AgentDefinitionDocument definition, Map<String, StateRef> states,
            Map<String, ParentRef> parents, List<ValidationDiagnostic> diagnostics) {
        String initialStateId = definition.lifecycle().initialStateId();
        if (!states.containsKey(initialStateId)) {
            diagnostics.add(diagnostic(SemanticDiagnosticCode.MISSING_INITIAL_STATE, "/lifecycle/initialStateId",
                    "Initial state '" + initialStateId + "' does not exist.",
                    "Select an existing root state ID."));
        } else if (parents.containsKey(initialStateId)) {
            diagnostics.add(diagnostic(SemanticDiagnosticCode.INITIAL_STATE_NOT_ROOT, "/lifecycle/initialStateId",
                    "Initial state '" + initialStateId + "' is contained by another state.",
                    "Choose a root state; composite initial children are resolved automatically."));
        }
    }

    private static void validateTransitions(AgentDefinitionDocument definition, Map<String, StateRef> states,
            List<ValidationDiagnostic> diagnostics) {
        Map<String, Integer> transitionIds = new HashMap<>();
        Map<String, Map<Integer, Integer>> ordersBySource = new HashMap<>();
        for (int index = 0; index < definition.transitions().size(); index++) {
            TransitionDefinition transition = definition.transitions().get(index);
            String pointer = "/transitions/" + index;
            Integer previousId = transitionIds.putIfAbsent(transition.id(), index);
            if (previousId != null) {
                diagnostics.add(diagnostic(SemanticDiagnosticCode.DUPLICATE_TRANSITION_ID, pointer + "/id",
                        "Transition ID '" + transition.id() + "' is already used.",
                        "Choose a unique transition ID."));
            }
            StateRef source = states.get(transition.sourceStateId());
            if (source == null) {
                diagnostics.add(diagnostic(SemanticDiagnosticCode.MISSING_TRANSITION_SOURCE,
                        pointer + "/sourceStateId",
                        "Transition source '" + transition.sourceStateId() + "' does not exist.",
                        "Select an existing state ID."));
            } else if (source.state() instanceof FinalStateDefinition) {
                diagnostics.add(diagnostic(SemanticDiagnosticCode.FINAL_STATE_OUTGOING_TRANSITION,
                        pointer + "/sourceStateId",
                        "Final state '" + transition.sourceStateId() + "' cannot have outgoing transitions.",
                        "Remove the transition or use a non-final source."));
            }
            if (!states.containsKey(transition.targetStateId())) {
                diagnostics.add(diagnostic(SemanticDiagnosticCode.MISSING_TRANSITION_TARGET,
                        pointer + "/targetStateId",
                        "Transition target '" + transition.targetStateId() + "' does not exist.",
                        "Select an existing state ID."));
            }
            Map<Integer, Integer> sourceOrders = ordersBySource.computeIfAbsent(transition.sourceStateId(),
                    ignored -> new HashMap<>());
            Integer previousOrder = sourceOrders.putIfAbsent(transition.order(), index);
            if (previousOrder != null) {
                diagnostics.add(diagnostic(SemanticDiagnosticCode.DUPLICATE_TRANSITION_ORDER, pointer + "/order",
                        "Transition order " + transition.order() + " is already used by this source state.",
                        "Give transitions from one source distinct order values."));
            }
        }
    }

    private static void validateReachability(AgentDefinitionDocument definition, Map<String, StateRef> states,
            Map<String, ParentRef> parents, List<ValidationDiagnostic> diagnostics) {
        if (!states.containsKey(definition.lifecycle().initialStateId())) {
            return;
        }
        Map<String, List<TransitionDefinition>> outgoing = new HashMap<>();
        definition.transitions().forEach(transition -> outgoing
                .computeIfAbsent(transition.sourceStateId(), ignored -> new ArrayList<>()).add(transition));

        Set<String> reached = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        activateTarget(definition.lifecycle().initialStateId(), states, parents, reached, queue, new HashSet<>());
        while (!queue.isEmpty()) {
            String source = queue.remove();
            for (TransitionDefinition transition : outgoing.getOrDefault(source, List.of())) {
                activateTarget(transition.targetStateId(), states, parents, reached, queue, new HashSet<>());
            }
        }
        for (StateRef state : states.values()) {
            if (!reached.contains(state.state().id())) {
                diagnostics.add(diagnostic(SemanticDiagnosticCode.UNREACHABLE_STATE,
                        "/states/" + state.index() + "/id",
                        "State '" + state.state().id() + "' is not reachable from the initial state.",
                        "Add a transition path or remove the unused state."));
            }
        }
    }

    private static void activateTarget(String stateId, Map<String, StateRef> states, Map<String, ParentRef> parents,
            Set<String> reached, Queue<String> queue, Set<String> path) {
        if (!path.add(stateId) || !states.containsKey(stateId)) {
            return;
        }
        markAncestors(stateId, states, parents, reached, queue, new HashSet<>());
        if (reached.add(stateId)) {
            queue.add(stateId);
        }
        StateDefinition state = states.get(stateId).state();
        if (state instanceof CompositeStateDefinition composite) {
            activateTarget(composite.initialChildStateId(), states, parents, reached, queue, path);
        }
    }

    private static void markAncestors(String stateId, Map<String, StateRef> states, Map<String, ParentRef> parents,
            Set<String> reached, Queue<String> queue, Set<String> path) {
        ParentRef parent = parents.get(stateId);
        if (parent == null || !path.add(parent.parentId()) || !states.containsKey(parent.parentId())) {
            return;
        }
        markAncestors(parent.parentId(), states, parents, reached, queue, path);
        if (reached.add(parent.parentId())) {
            queue.add(parent.parentId());
        }
    }

    private static void validateValueSchemas(Map<String, StorageRef> storage,
            List<ValidationDiagnostic> diagnostics) {
        for (StorageRef storageRef : storage.values()) {
            String pointer = "/storage/" + storageRef.index() + "/valueSchema";
            validateValueSchemaKeywords(storageRef.declaration().valueSchema(), pointer, diagnostics);
            if (storageRef.declaration().initialValue() != null
                    && !EmbeddedValueSchemas.accepts(storageRef.declaration().valueSchema(),
                            storageRef.declaration().initialValue())) {
                diagnostics.add(diagnostic(SemanticDiagnosticCode.INVALID_STORAGE_INITIAL_VALUE,
                        "/storage/" + storageRef.index() + "/initialValue",
                        "Initial value does not satisfy the declared valueSchema.",
                        "Change the initial value or its storage schema."));
            }
        }
    }

    private static void validateValueSchemaKeywords(JsonNode schema, String pointer,
            List<ValidationDiagnostic> diagnostics) {
        String type = schema.path("type").asText();
        validateKeywordType(schema, pointer, "properties", "object", type, diagnostics);
        validateKeywordType(schema, pointer, "required", "object", type, diagnostics);
        validateKeywordType(schema, pointer, "additionalProperties", "object", type, diagnostics);
        validateKeywordType(schema, pointer, "items", "array", type, diagnostics);
        validateKeywordType(schema, pointer, "minItems", "array", type, diagnostics);
        validateKeywordType(schema, pointer, "maxItems", "array", type, diagnostics);
        validateKeywordType(schema, pointer, "minLength", "string", type, diagnostics);
        validateKeywordType(schema, pointer, "maxLength", "string", type, diagnostics);
        if (schema.has("minimum") || schema.has("maximum")) {
            if (!"integer".equals(type) && !"number".equals(type)) {
                diagnostics.add(diagnostic(SemanticDiagnosticCode.STORAGE_SCHEMA_KEYWORD_MISMATCH, pointer,
                        "minimum/maximum are valid only for integer or number storage.",
                        "Remove the numeric bounds or use a numeric type."));
            }
        }

        if ("object".equals(type)) {
            JsonNode properties = schema.path("properties");
            JsonNode required = schema.path("required");
            if (required.isArray()) {
                for (int index = 0; index < required.size(); index++) {
                    String name = required.get(index).asText();
                    if (!properties.has(name)) {
                        diagnostics.add(diagnostic(
                                SemanticDiagnosticCode.STORAGE_SCHEMA_REQUIRED_PROPERTY_UNDECLARED,
                                pointer + "/required/" + index,
                                "Required property '" + name + "' has no property schema.",
                                "Declare the property or remove it from required."));
                    }
                }
            }
            if (properties.isObject()) {
                properties.fields().forEachRemaining(field -> validateValueSchemaKeywords(field.getValue(),
                        pointer + "/properties/" + escape(field.getKey()), diagnostics));
            }
        }
        if ("array".equals(type) && schema.has("items")) {
            validateValueSchemaKeywords(schema.get("items"), pointer + "/items", diagnostics);
        }
        validateBounds(schema, pointer, "minimum", "maximum", diagnostics);
        validateBounds(schema, pointer, "minLength", "maxLength", diagnostics);
        validateBounds(schema, pointer, "minItems", "maxItems", diagnostics);
        if (schema.has("const") && !EmbeddedValueSchemas.accepts(schemaWithoutConst(schema), schema.get("const"))) {
            diagnostics.add(diagnostic(SemanticDiagnosticCode.STORAGE_SCHEMA_KEYWORD_MISMATCH, pointer + "/const",
                    "const does not match the declared storage type or constraints.",
                    "Use a const value accepted by the rest of the schema."));
        }
        if (schema.has("enum")) {
            for (int index = 0; index < schema.get("enum").size(); index++) {
                if (!EmbeddedValueSchemas.accepts(schemaWithoutEnum(schema), schema.get("enum").get(index))) {
                    diagnostics.add(diagnostic(SemanticDiagnosticCode.STORAGE_SCHEMA_KEYWORD_MISMATCH,
                            pointer + "/enum/" + index,
                            "enum value does not match the declared storage type or constraints.",
                            "Use enum values accepted by the rest of the schema."));
                }
            }
        }
    }

    private static void validateKeywordType(JsonNode schema, String pointer, String keyword, String expectedType,
            String actualType, List<ValidationDiagnostic> diagnostics) {
        if (schema.has(keyword) && !expectedType.equals(actualType)) {
            diagnostics.add(diagnostic(SemanticDiagnosticCode.STORAGE_SCHEMA_KEYWORD_MISMATCH,
                    pointer + "/" + keyword,
                    "Keyword '" + keyword + "' requires storage type '" + expectedType + "'.",
                    "Remove the keyword or change the storage type."));
        }
    }

    private static void validateBounds(JsonNode schema, String pointer, String minimum, String maximum,
            List<ValidationDiagnostic> diagnostics) {
        if (!schema.has(minimum) || !schema.has(maximum)) {
            return;
        }
        BigDecimal lower = schema.get(minimum).decimalValue();
        BigDecimal upper = schema.get(maximum).decimalValue();
        if (lower.compareTo(upper) > 0) {
            diagnostics.add(diagnostic(SemanticDiagnosticCode.STORAGE_SCHEMA_INVALID_BOUNDS,
                    pointer + "/" + maximum,
                    maximum + " must be greater than or equal to " + minimum + ".",
                    "Correct the schema bounds."));
        }
    }

    private static JsonNode schemaWithoutConst(JsonNode schema) {
        com.fasterxml.jackson.databind.node.ObjectNode copy = schema.deepCopy();
        copy.remove("const");
        return copy;
    }

    private static JsonNode schemaWithoutEnum(JsonNode schema) {
        com.fasterxml.jackson.databind.node.ObjectNode copy = schema.deepCopy();
        copy.remove("enum");
        return copy;
    }

    private void validateComponents(AgentDefinitionDocument definition, List<LocatedComponent> components,
            Map<String, StateRef> states, Map<String, StorageRef> storage, Set<String> resources,
            List<ValidationDiagnostic> diagnostics) {
        Set<String> supportedObservations = Set.copyOf(definition.interaction().supportedObservations());
        Set<String> supportedModalities = Set.copyOf(definition.interaction().supportedBehaviourModalities());
        Set<String> consumedObservations = new HashSet<>();
        Set<String> emittedModalities = new HashSet<>();
        Map<String, Integer> initializerWrites = new HashMap<>();

        for (LocatedComponent located : components) {
            ComponentSemantics semantics = this.componentSemanticsResolver.resolve(located.component());
            if (semantics == null) {
                semantics = ComponentSemantics.none();
            }
            for (String observation : semantics.consumedObservations()) {
                if (!supportedObservations.contains(observation)) {
                    diagnostics.add(diagnostic(SemanticDiagnosticCode.UNDECLARED_OBSERVATION, located.pointer(),
                            "Component consumes undeclared observation '" + observation + "'.",
                            "Add the observation to interaction.supportedObservations."));
                }
            }
            consumedObservations.addAll(semantics.consumedObservations());
            for (String modality : semantics.emittedBehaviourModalities()) {
                if (!supportedModalities.contains(modality)) {
                    diagnostics.add(diagnostic(SemanticDiagnosticCode.UNDECLARED_BEHAVIOUR_MODALITY,
                            located.pointer(),
                            "Component emits undeclared behaviour modality '" + modality + "'.",
                            "Add the modality to interaction.supportedBehaviourModalities."));
                }
            }
            emittedModalities.addAll(semantics.emittedBehaviourModalities());
            for (ComponentStorageUse storageUse : semantics.storageUses()) {
                String pointer = located.pointer() + normalizeRelativePointer(storageUse.configPointer());
                StorageRef declaration = storage.get(storageUse.key());
                if (declaration == null) {
                    diagnostics.add(diagnostic(SemanticDiagnosticCode.MISSING_STORAGE_BINDING, pointer,
                            "Component references undeclared storage key '" + storageUse.key() + "'.",
                            "Declare the storage key or change the component configuration."));
                    continue;
                }
                if (storageUse.expectedValueSchema() != null
                        && !storageSchemasAreCompatible(declaration.declaration().valueSchema(), storageUse)) {
                    diagnostics.add(diagnostic(SemanticDiagnosticCode.INCOMPATIBLE_STORAGE_SCHEMA, pointer,
                            "Storage key '" + storageUse.key() + "' is incompatible with the component shape.",
                            "Align the declared valueSchema with the component's typed storage contract."));
                }
                if (located.initializer() && storageUse.access().writes()) {
                    initializerWrites.merge(storageUse.key(), 1, Integer::sum);
                }
            }
            for (ComponentReference reference : semantics.resourceReferences()) {
                if (!resources.contains(reference.id())) {
                    diagnostics.add(diagnostic(SemanticDiagnosticCode.MISSING_RESOURCE_REFERENCE,
                            located.pointer() + normalizeRelativePointer(reference.configPointer()),
                            "Component references unknown resource '" + reference.id() + "'.",
                            "Declare the resource or change the component configuration."));
                }
            }
            for (ComponentReference reference : semantics.stateReferences()) {
                if (!states.containsKey(reference.id())) {
                    diagnostics.add(diagnostic(SemanticDiagnosticCode.MISSING_STATE_REFERENCE,
                            located.pointer() + normalizeRelativePointer(reference.configPointer()),
                            "Component references unknown state '" + reference.id() + "'.",
                            "Select an existing stable state ID."));
                }
            }
        }

        for (int index = 0; index < definition.interaction().supportedObservations().size(); index++) {
            String observation = definition.interaction().supportedObservations().get(index);
            if (!consumedObservations.contains(observation)) {
                diagnostics.add(diagnostic(SemanticDiagnosticCode.UNUSED_OBSERVATION,
                        "/interaction/supportedObservations/" + index,
                        "Declared observation '" + observation + "' is not used by a component.",
                        "Use it in a reaction/prompt component or remove the capability."));
            }
        }
        for (int index = 0; index < definition.interaction().supportedBehaviourModalities().size(); index++) {
            String modality = definition.interaction().supportedBehaviourModalities().get(index);
            if (!emittedModalities.contains(modality)) {
                diagnostics.add(diagnostic(SemanticDiagnosticCode.UNUSED_BEHAVIOUR_MODALITY,
                        "/interaction/supportedBehaviourModalities/" + index,
                        "Declared behaviour modality '" + modality + "' is not emitted by a component.",
                        "Use it in a behaviour component or remove the capability."));
            }
        }

        for (StorageRef storageRef : storage.values()) {
            int writers = initializerWrites.getOrDefault(storageRef.declaration().key(), 0);
            if ((storageRef.declaration().initialValue() != null && writers > 0) || writers > 1) {
                diagnostics.add(diagnostic(SemanticDiagnosticCode.MULTIPLE_STORAGE_INITIALIZERS,
                        "/storage/" + storageRef.index(),
                        "Storage key '" + storageRef.declaration().key()
                                + "' has more than one initial-value producer.",
                        "Use either inline initialValue or exactly one initializer."));
            }
            if (storageRef.declaration().required() && storageRef.declaration().initialValue() == null
                    && writers == 0) {
                diagnostics.add(diagnostic(SemanticDiagnosticCode.REQUIRED_STORAGE_UNINITIALIZED,
                        "/storage/" + storageRef.index(),
                        "Required storage key '" + storageRef.declaration().key() + "' has no initial value.",
                        "Add inline initialValue or one registered initializer."));
            }
        }
    }

    private static boolean storageSchemasAreCompatible(JsonNode declared, ComponentStorageUse storageUse) {
        boolean readCompatible = !storageUse.access().reads()
                || EmbeddedValueSchemas.isAssignable(declared, storageUse.expectedValueSchema());
        boolean writeCompatible = !storageUse.access().writes()
                || EmbeddedValueSchemas.isAssignable(storageUse.expectedValueSchema(), declared);
        return readCompatible && writeCompatible;
    }

    private static List<LocatedComponent> locateComponents(AgentDefinitionDocument definition) {
        List<LocatedComponent> components = new ArrayList<>();
        for (int index = 0; index < definition.lifecycle().initializers().size(); index++) {
            components.add(new LocatedComponent(definition.lifecycle().initializers().get(index),
                    "/lifecycle/initializers/" + index, true));
        }
        for (int index = 0; index < definition.states().size(); index++) {
            StateDefinition state = definition.states().get(index);
            if (state instanceof AtomicStateDefinition atomic) {
                addComponent(components, atomic.eventSelector(), "/states/" + index + "/eventSelector", false);
                addComponent(components, atomic.policy(), "/states/" + index + "/policy", false);
            } else if (state instanceof CompositeStateDefinition composite) {
                addComponent(components, composite.eventSelector(), "/states/" + index + "/eventSelector", false);
                addComponent(components, composite.policy(), "/states/" + index + "/policy", false);
            }
        }
        for (int transitionIndex = 0; transitionIndex < definition.transitions().size(); transitionIndex++) {
            TransitionDefinition transition = definition.transitions().get(transitionIndex);
            for (int decisionIndex = 0; decisionIndex < transition.decisions().size(); decisionIndex++) {
                components.add(new LocatedComponent(transition.decisions().get(decisionIndex),
                        "/transitions/" + transitionIndex + "/decisions/" + decisionIndex, false));
            }
            for (int actionIndex = 0; actionIndex < transition.actions().size(); actionIndex++) {
                components.add(new LocatedComponent(transition.actions().get(actionIndex),
                        "/transitions/" + transitionIndex + "/actions/" + actionIndex, false));
            }
        }
        return components;
    }

    private static void addComponent(List<LocatedComponent> components, ComponentEnvelope component, String pointer,
            boolean initializer) {
        if (component != null) {
            components.add(new LocatedComponent(component, pointer, initializer));
            if (component.config() != null && component.config().path("selectors").isArray()) {
                for (int index = 0; index < component.config().path("selectors").size(); index++) {
                    JsonNode nested = component.config().path("selectors").get(index);
                    if (nested.path("kind").isTextual() && nested.path("version").canConvertToInt()
                            && nested.path("config").isObject()) {
                        addComponent(components, new ComponentEnvelope(nested.path("kind").asText(),
                                nested.path("version").asInt(), nested.path("config")),
                                pointer + "/config/selectors/" + index, initializer);
                    }
                }
            }
        }
    }

    private static void validatePrompts(List<LocatedComponent> components,
            List<ValidationDiagnostic> diagnostics) {
        List<LocatedPrompt> prompts = new ArrayList<>();
        for (LocatedComponent component : components) {
            locatePrompts(component.component().config(), component.pointer() + "/config", prompts);
        }
        int definitionCharacters = 0;
        for (LocatedPrompt prompt : prompts) {
            JsonNode sections = prompt.node().get("sections");
            if (sections == null || !sections.isArray()) {
                diagnostics.add(diagnostic(SemanticDiagnosticCode.INVALID_PROMPT_STRUCTURE,
                        prompt.pointer() + "/sections",
                        "Prompt sections must be an ordered array.",
                        "Provide an array of typed prompt sections."));
                continue;
            }
            Set<String> sectionIds = new HashSet<>();
            int promptCharacters = 0;
            for (int index = 0; index < sections.size(); index++) {
                JsonNode section = sections.get(index);
                String pointer = prompt.pointer() + "/sections/" + index;
                if (!section.isObject() || !section.path("id").isTextual() || !section.path("kind").isTextual()
                        || !section.path("content").isTextual()) {
                    diagnostics.add(diagnostic(SemanticDiagnosticCode.INVALID_PROMPT_STRUCTURE, pointer,
                            "Prompt section requires textual id, kind, and content fields.",
                            "Use the schema-version-1 prompt section shape."));
                    continue;
                }
                String id = section.get("id").asText();
                String kind = section.get("kind").asText();
                if (!STABLE_ID.matcher(id).matches() || !STABLE_ID.matcher(kind).matches()) {
                    diagnostics.add(diagnostic(SemanticDiagnosticCode.INVALID_PROMPT_STRUCTURE, pointer,
                            "Prompt section id and kind must be nonblank.",
                            "Provide stable nonblank id and kind values."));
                }
                if (!sectionIds.add(id)) {
                    diagnostics.add(diagnostic(SemanticDiagnosticCode.DUPLICATE_PROMPT_SECTION_ID,
                            pointer + "/id",
                            "Prompt section ID '" + id + "' is already used in this prompt.",
                            "Choose a unique section ID within this prompt role."));
                }
                String content = section.get("content").asText();
                if (content.isBlank()) {
                    diagnostics.add(diagnostic(SemanticDiagnosticCode.BLANK_PROMPT_SECTION,
                            pointer + "/content",
                            "Prompt section content must not be blank.",
                            "Remove the section or add explicit content."));
                    continue;
                }
                int length = ch.zhaw.prometheus.definition.prompt.PromptComposer.normalizeLineEndings(content)
                        .length();
                promptCharacters += length;
                if (length > MAX_PROMPT_SECTION_CHARACTERS) {
                    diagnostics.add(diagnostic(SemanticDiagnosticCode.PROMPT_SECTION_TOO_LARGE,
                            pointer + "/content",
                            "Prompt section exceeds " + MAX_PROMPT_SECTION_CHARACTERS + " characters.",
                            "Split or shorten the section."));
                }
            }
            definitionCharacters += promptCharacters;
            if (promptCharacters > MAX_PROMPT_CHARACTERS) {
                diagnostics.add(diagnostic(SemanticDiagnosticCode.PROMPT_TOO_LARGE, prompt.pointer(),
                        "Prompt exceeds " + MAX_PROMPT_CHARACTERS + " characters.",
                        "Split or shorten the prompt sections."));
            }
        }
        if (definitionCharacters > MAX_DEFINITION_PROMPT_CHARACTERS) {
            diagnostics.add(diagnostic(SemanticDiagnosticCode.DEFINITION_PROMPTS_TOO_LARGE, "",
                    "Definition prompts exceed " + MAX_DEFINITION_PROMPT_CHARACTERS + " characters.",
                    "Reduce repeated or oversized prompt content."));
        }
    }

    private static void locatePrompts(JsonNode node, String pointer, List<LocatedPrompt> prompts) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            if (node.has("sections")) {
                prompts.add(new LocatedPrompt(node, pointer));
                return;
            }
            node.fields().forEachRemaining(field -> locatePrompts(field.getValue(),
                    pointer + "/" + escape(field.getKey()), prompts));
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                locatePrompts(node.get(index), pointer + "/" + index, prompts);
            }
        }
    }

    private static ValidationDiagnostic diagnostic(SemanticDiagnosticCode code, String pointer, String message,
            String hint) {
        return ValidationDiagnostic.of(code, pointer, message, hint);
    }

    private static String normalizeRelativePointer(String pointer) {
        if (pointer == null || pointer.isBlank()) {
            return "";
        }
        return pointer.startsWith("/") ? pointer : "/" + pointer;
    }

    private static String escape(String segment) {
        return segment.replace("~", "~0").replace("/", "~1");
    }

    private enum VisitColor {
        WHITE,
        GRAY,
        BLACK
    }

    private record StateRef(int index, StateDefinition state) {
    }

    private record StorageRef(int index, StorageDefinition declaration) {
    }

    private record ParentRef(String parentId) {
    }

    private record LocatedComponent(ComponentEnvelope component, String pointer, boolean initializer) {
    }

    private record LocatedPrompt(JsonNode node, String pointer) {
    }
}
