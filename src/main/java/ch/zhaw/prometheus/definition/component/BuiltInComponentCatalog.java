package ch.zhaw.prometheus.definition.component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ch.zhaw.prometheus.definition.compiled.ImmutableJson;
import ch.zhaw.prometheus.definition.compiled.CompiledStorageBinding;
import ch.zhaw.prometheus.definition.component.builtin.ConstantInitializerComponent;
import ch.zhaw.prometheus.definition.component.builtin.CompositeSelectorComponent;
import ch.zhaw.prometheus.definition.component.builtin.CompositeSelectorComponent.Mode;
import ch.zhaw.prometheus.definition.component.builtin.ExtractionActionComponent;
import ch.zhaw.prometheus.definition.component.builtin.ExactTextPolicyComponent;
import ch.zhaw.prometheus.definition.component.builtin.IncrementActionComponent;
import ch.zhaw.prometheus.definition.component.builtin.LatestEventTypeDecisionComponent;
import ch.zhaw.prometheus.definition.component.builtin.NoOpPolicyComponent;
import ch.zhaw.prometheus.definition.component.builtin.PromptDecisionComponent;
import ch.zhaw.prometheus.definition.component.builtin.PromptBehaviourActionComponent;
import ch.zhaw.prometheus.definition.component.builtin.PromptPolicyComponent;
import ch.zhaw.prometheus.definition.component.builtin.RandomChoiceInitializerComponent;
import ch.zhaw.prometheus.definition.component.builtin.ResourceChoiceInitializerComponent;
import ch.zhaw.prometheus.definition.component.builtin.RpsEvaluateRoundActionComponent;
import ch.zhaw.prometheus.definition.component.builtin.RpsResultPolicyComponent;
import ch.zhaw.prometheus.definition.component.builtin.RpsRevealPolicyComponent;
import ch.zhaw.prometheus.definition.component.builtin.RpsSelectSignActionComponent;
import ch.zhaw.prometheus.definition.component.builtin.SelectorComponent;
import ch.zhaw.prometheus.definition.component.builtin.SelectorComponent.SelectorKind;
import ch.zhaw.prometheus.definition.component.builtin.TypedChoicesResourceComponent;
import ch.zhaw.prometheus.definition.prompt.PromptComposer;
import ch.zhaw.prometheus.definition.validation.ComponentReference;
import ch.zhaw.prometheus.definition.validation.ComponentSemantics;
import ch.zhaw.prometheus.definition.validation.ComponentStorageAccess;
import ch.zhaw.prometheus.definition.validation.ComponentStorageUse;

/** Framework-owned schema-version-independent component registrations. */
public final class BuiltInComponentCatalog {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String EMPTY_SCHEMA = """
            {"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object","additionalProperties":false}
            """;
    private static final String STRING_ARRAY_SCHEMA = """
            {"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object",
             "properties":{"%s":{"type":"array","minItems":1,"uniqueItems":true,"items":{"type":"string","minLength":1}}},
             "required":["%s"],"additionalProperties":false}
            """;
    private static final String PROMPT_SCHEMA = """
            {"type":"object","properties":{"sections":{"type":"array","items":{"type":"object",
             "properties":{"id":{"type":"string","minLength":1},"kind":{"type":"string","minLength":1},
             "content":{"type":"string","minLength":1}},"required":["id","kind","content"],"additionalProperties":false}}},
             "required":["sections"],"additionalProperties":false}
            """;
    private static final String STORAGE_BINDINGS_SCHEMA = """
            {"type":"array","items":{"type":"object","properties":{
             "key":{"type":"string","minLength":1},"access":{"enum":["read","write","read-write"]},
             "expectedValueSchema":{"type":"object"}},"required":["key","access"],"additionalProperties":false}}
            """;
    private static final JsonNode RPS_SIGN_SCHEMA = tree(
            "{\"type\":\"string\",\"enum\":[\"rock\",\"scissor\",\"paper\"]}");
    private static final JsonNode POSITIVE_INTEGER_SCHEMA = tree("{\"type\":\"integer\",\"minimum\":1}");
    private static final JsonNode RPS_ROUND_SCHEMA = tree("""
            {"type":"object","required":["round","agentSign","userSign","outcome","winner","reason"],
             "properties":{"round":{"type":"integer","minimum":1},
             "agentSign":{"type":"string","enum":["rock","scissor","paper"]},
             "userSign":{"type":"string","enum":["rock","scissor","paper"]},
             "outcome":{"type":"string","enum":["agent_win","user_win","draw"]},
             "winner":{"type":"string","enum":["agent","user","draw"]},"reason":{"type":"string"},
             "userConfidence":{"type":"number"},"userDetectionMode":{"type":"string"},
             "userHand":{"type":"string"}},"additionalProperties":false}
            """);
    private static final JsonNode RPS_ROUNDS_SCHEMA = arrayValueSchema(RPS_ROUND_SCHEMA);
    private static final Set<String> RPS_REVEAL_MODALITIES = Set.of("speech", "nonVerbal.gesture",
            "nonVerbal.facialExpression", "nonVerbal.gaze", "nonVerbal.motion", "motion.handSign", "display");
    private static final Set<String> RPS_RESULT_MODALITIES = Set.of("speech", "nonVerbal.gesture",
            "nonVerbal.facialExpression", "nonVerbal.gaze", "nonVerbal.motion", "display");

    private BuiltInComponentCatalog() {
    }

    public static ComponentRegistry createRegistry() {
        return new ComponentRegistry(definitions());
    }

    public static List<AgentComponentDefinition> definitions() {
        List<AgentComponentDefinition> definitions = new ArrayList<>();
        definitions.add(registered("prometheus.policy.no-op", ComponentCategory.POLICY, EMPTY_SCHEMA,
                "No-op policy", "Produces no behaviour.", ignored -> ComponentSemantics.none(),
                ignored -> new NoOpPolicyComponent()));
        definitions.add(registered("prometheus.policy.exact-text", ComponentCategory.POLICY, exactTextSchema(),
                "Exact text", "Emits the latest matching event payload verbatim without a model.",
                config -> new ComponentSemantics(Set.of(config.path("eventType").asText()), Set.of("speech"),
                        List.of(), List.of(), List.of()),
                config -> new ExactTextPolicyComponent(config.path("eventType").asText(),
                        config.path("actor").asText(), config.path("eventKind").asText(),
                        config.path("maxTextCodePoints").asInt())));
        definitions.add(registered("prometheus.policy.prompt", ComponentCategory.POLICY, promptPolicySchema(),
                "Prompt policy", "Produces behaviour from typed prompt roles.", BuiltInComponentCatalog::promptSemantics,
                config -> new PromptPolicyComponent(prompt(config, "responsePrompt"), prompt(config, "starterPrompt"),
                        prompt(config, "summaryPrompt"), prompt(config, "nonverbalPlanPrompt"),
                        prompt(config, "gesturePrompt"), compiledStorageBindings(config), strings(config, "consumedObservations"),
                        strings(config, "emittedModalities"))));
        definitions.add(registered("prometheus.policy.rps-reveal", ComponentCategory.POLICY,
                requiredStringsSchema("currentAgentSignStorageKey", "currentRoundNumberStorageKey"),
                "RPS reveal", "Emits the deterministic English reveal speech, hand sign, and display.",
                BuiltInComponentCatalog::rpsRevealSemantics,
                config -> new RpsRevealPolicyComponent(config.path("currentAgentSignStorageKey").asText(),
                        config.path("currentRoundNumberStorageKey").asText())));
        definitions.add(registered("prometheus.policy.rps-result", ComponentCategory.POLICY,
                requiredStringsSchema("lastRoundStorageKey"), "RPS result",
                "Emits the deterministic English round result and display.",
                BuiltInComponentCatalog::rpsResultSemantics,
                config -> new RpsResultPolicyComponent(config.path("lastRoundStorageKey").asText())));

        definitions.add(selector("prometheus.selector.any", EMPTY_SCHEMA, "Any event", SelectorKind.ANY, null));
        definitions.add(selector("prometheus.selector.state-path", EMPTY_SCHEMA, "Active state path",
                SelectorKind.ACTIVE_STATE_PATH, null));
        definitions.add(selector("prometheus.selector.event-type", arraySchema("types"), "Event type",
                SelectorKind.EVENT_TYPE, "types"));
        definitions.add(selector("prometheus.selector.actor", arraySchema("actors"), "Actor", SelectorKind.ACTOR,
                "actors"));
        definitions.add(selector("prometheus.selector.event-kind", arraySchema("kinds"), "Event kind",
                SelectorKind.EVENT_KIND, "kinds"));
        definitions.add(registered("prometheus.selector.state-id", ComponentCategory.SELECTOR, arraySchema("stateIds"),
                "State ID", "Matches events associated with stable state IDs.", config -> new ComponentSemantics(
                        Set.of(), Set.of(), List.of(), List.of(), references(config, "stateIds")),
                config -> new SelectorComponent(SelectorKind.STATE_ID, stringList(config, "stateIds"))));
        definitions.add(compositeSelector("prometheus.selector.all", "All selectors", Mode.ALL));
        definitions.add(compositeSelector("prometheus.selector.any-of", "Any selector", Mode.ANY));

        definitions.add(registered("prometheus.decision.latest-event-type", ComponentCategory.DECISION,
                requiredStringSchema("eventType"), "Latest event type", "Accepts when the latest event has the type.",
                config -> new ComponentSemantics(Set.of(config.path("eventType").asText()), Set.of(), List.of(),
                        List.of(), List.of()),
                config -> new LatestEventTypeDecisionComponent(config.path("eventType").asText())));
        definitions.add(registered("prometheus.decision.prompt", ComponentCategory.DECISION, promptDecisionSchema(),
                "Prompt decision", "Evaluates a typed decision prompt.", BuiltInComponentCatalog::promptSemantics,
                config -> new PromptDecisionComponent(prompt(config, "decisionPrompt"), compiledStorageBindings(config),
                        strings(config, "consumedObservations"))));

        definitions.add(registered("prometheus.action.extract", ComponentCategory.ACTION, extractionSchema(),
                "Extract value", "Extracts structured data into declared storage.",
                config -> withPrimaryStorageUse(config, "targetStorageKey", ComponentStorageAccess.WRITE),
                config -> new ExtractionActionComponent(prompt(config, "extractionPrompt"),
                        config.path("targetStorageKey").asText(), immutableOrNull(config.get("outputSchema")),
                        compiledStorageBindings(config))));
        definitions.add(registered("prometheus.action.increment", ComponentCategory.ACTION,
                requiredStringSchema("targetStorageKey"), "Increment", "Increments integer storage by one.",
                config -> new ComponentSemantics(Set.of(), Set.of(), List.of(new ComponentStorageUse(
                        config.path("targetStorageKey").asText(), ComponentStorageAccess.READ_WRITE,
                        tree("{\"type\":\"integer\",\"minimum\":0}"), "/config/targetStorageKey")), List.of(), List.of()),
                config -> new IncrementActionComponent(config.path("targetStorageKey").asText())));
        definitions.add(registered("prometheus.action.prompt-behaviour", ComponentCategory.ACTION,
                promptPolicySchema(), "Prompt behaviour", "Emits completion or transition behaviour from prompts.",
                BuiltInComponentCatalog::promptSemantics,
                config -> new PromptBehaviourActionComponent(new PromptPolicyComponent(
                        prompt(config, "responsePrompt"), prompt(config, "starterPrompt"),
                        prompt(config, "summaryPrompt"), prompt(config, "nonverbalPlanPrompt"),
                        prompt(config, "gesturePrompt"), compiledStorageBindings(config),
                        strings(config, "consumedObservations"), strings(config, "emittedModalities")))));
        definitions.add(registered("prometheus.action.rps-select-sign", ComponentCategory.ACTION,
                requiredStringsSchema("roundsStorageKey", "currentAgentSignStorageKey",
                        "currentRoundNumberStorageKey"),
                "Select RPS sign", "Selects rock, scissor, and paper deterministically by completed round count.",
                BuiltInComponentCatalog::rpsSelectSemantics,
                config -> new RpsSelectSignActionComponent(config.path("roundsStorageKey").asText(),
                        config.path("currentAgentSignStorageKey").asText(),
                        config.path("currentRoundNumberStorageKey").asText())));
        definitions.add(registered("prometheus.action.rps-evaluate-round", ComponentCategory.ACTION,
                requiredStringsSchema("handSignEventType", "currentAgentSignStorageKey",
                        "currentRoundNumberStorageKey", "lastRoundStorageKey", "roundsStorageKey"),
                "Evaluate RPS round", "Evaluates the latest hand sign and appends the deterministic round result.",
                BuiltInComponentCatalog::rpsEvaluateSemantics,
                config -> new RpsEvaluateRoundActionComponent(config.path("handSignEventType").asText(),
                        config.path("currentAgentSignStorageKey").asText(),
                        config.path("currentRoundNumberStorageKey").asText(),
                        config.path("lastRoundStorageKey").asText(), config.path("roundsStorageKey").asText())));

        definitions.add(registered("prometheus.initializer.constant", ComponentCategory.INITIALIZER,
                constantInitializerSchema(), "Constant initializer", "Writes a fixed initial storage value.",
                config -> withPrimaryStorageUse(config, "storageKey", ComponentStorageAccess.WRITE),
                config -> new ConstantInitializerComponent(config.path("storageKey").asText(),
                        new ImmutableJson(config.get("value")))));
        definitions.add(registered("prometheus.initializer.random-choice", ComponentCategory.INITIALIZER,
                randomInitializerSchema(), "Random choice initializer", "Selects an initial value using an injected RNG.",
                BuiltInComponentCatalog::randomInitializerSemantics,
                config -> config.has("choicesResourceId")
                        ? new ResourceChoiceInitializerComponent(config.path("storageKey").asText(),
                                config.path("choicesResourceId").asText())
                        : new RandomChoiceInitializerComponent(config.path("storageKey").asText(),
                                immutableList(config.path("choices")))));
        definitions.add(registered("prometheus.resource.typed-choices", ComponentCategory.RESOURCE,
                typedChoicesSchema(), "Typed choices", "Reusable immutable typed choices.",
                ignored -> ComponentSemantics.none(),
                config -> new TypedChoicesResourceComponent(immutableList(config.path("values")))));
        return List.copyOf(definitions);
    }

    private static AgentComponentDefinition selector(String kind, String schema, String label, SelectorKind selectorKind,
            String arrayField) {
        return registered(kind, ComponentCategory.SELECTOR, schema, label, "Selects matching runtime events.",
                config -> arrayField != null && selectorKind == SelectorKind.EVENT_TYPE
                        ? new ComponentSemantics(strings(config, arrayField), Set.of(), List.of(), List.of(), List.of())
                        : ComponentSemantics.none(),
                config -> new SelectorComponent(selectorKind,
                        arrayField == null ? List.of() : stringList(config, arrayField)));
    }

    private static AgentComponentDefinition compositeSelector(String kind, String label, Mode mode) {
        return registeredContextual(kind, ComponentCategory.SELECTOR, compositeSelectorSchema(), label,
                "Composes nested selectors without executable expressions.",
                ignored -> ComponentSemantics.none(),
                (config, registry) -> new CompositeSelectorComponent(mode,
                        compileSelectorList(config.path("selectors"), registry)));
    }

    private static List<CompiledSelector> compileSelectorList(JsonNode selectors, ComponentRegistry registry) {
        List<CompiledSelector> compiled = new ArrayList<>();
        selectors.forEach(selector -> compiled.add((CompiledSelector) registry.compile(new ch.zhaw.prometheus.definition.document.ComponentEnvelope(
                selector.path("kind").asText(), selector.path("version").asInt(), selector.path("config")))));
        return List.copyOf(compiled);
    }

    private static AgentComponentDefinition registered(String kind, ComponentCategory category, String schema,
            String label, String description,
            java.util.function.Function<JsonNode, ComponentSemantics> semantics,
            java.util.function.Function<JsonNode, CompiledComponent> compiler) {
        ComponentUiMetadata uiMetadata = uiMetadata(kind, label, description);
        return new RegisteredComponent(new ComponentKey(kind, 1), category,
                authoringSchema(tree(schema), uiMetadata), uiMetadata, semantics,
                compiler);
    }

    private static AgentComponentDefinition registeredContextual(String kind, ComponentCategory category, String schema,
            String label, String description,
            java.util.function.Function<JsonNode, ComponentSemantics> semantics,
            java.util.function.BiFunction<JsonNode, ComponentRegistry, CompiledComponent> compiler) {
        ComponentUiMetadata uiMetadata = uiMetadata(kind, label, description);
        return new RegisteredComponent(new ComponentKey(kind, 1), category,
                authoringSchema(tree(schema), uiMetadata), uiMetadata, semantics,
                compiler);
    }

    private static ComponentUiMetadata uiMetadata(String kind, String label, String description) {
        JsonNode defaultConfig = tree(switch (kind) {
            case "prometheus.policy.no-op", "prometheus.selector.any", "prometheus.selector.state-path" -> "{}";
            case "prometheus.policy.exact-text" -> """
                    {"eventType":"obs.user_utterance","actor":"user","eventKind":"observation",
                     "maxTextCodePoints":2000}
                    """;
            case "prometheus.policy.prompt" -> promptExample("response.objective", "objective",
                    "Describe the agent's response objective.", "responsePrompt");
            case "prometheus.policy.rps-reveal" -> """
                    {"currentAgentSignStorageKey":"rps_current_agent_sign",
                     "currentRoundNumberStorageKey":"rps_current_round_number"}
                    """;
            case "prometheus.policy.rps-result" ->
                "{\"lastRoundStorageKey\":\"rps_last_round\"}";
            case "prometheus.selector.event-type" -> "{\"types\":[\"obs.user_utterance\"]}";
            case "prometheus.selector.actor" -> "{\"actors\":[\"user\"]}";
            case "prometheus.selector.event-kind" -> "{\"kinds\":[\"observation\"]}";
            case "prometheus.selector.state-id" -> "{\"stateIds\":[\"state\"]}";
            case "prometheus.selector.all", "prometheus.selector.any-of" -> """
                    {"selectors":[{"kind":"prometheus.selector.any","version":1,"config":{}}]}
                    """;
            case "prometheus.decision.latest-event-type" ->
                "{\"eventType\":\"obs.user_utterance\"}";
            case "prometheus.decision.prompt" -> promptExample("decision.criterion", "transition-criterion",
                    "Return true only when the transition criterion is satisfied.", "decisionPrompt");
            case "prometheus.action.extract" -> "{\"targetStorageKey\":\"outcome\"}";
            case "prometheus.action.increment" -> "{\"targetStorageKey\":\"counter\"}";
            case "prometheus.action.prompt-behaviour" -> promptExample("completion.response", "completion",
                    "Give one brief completion response.", "responsePrompt");
            case "prometheus.action.rps-select-sign" -> """
                    {"roundsStorageKey":"rps_rounds","currentAgentSignStorageKey":"rps_current_agent_sign",
                     "currentRoundNumberStorageKey":"rps_current_round_number"}
                    """;
            case "prometheus.action.rps-evaluate-round" -> """
                    {"handSignEventType":"obs.hand.sign","currentAgentSignStorageKey":"rps_current_agent_sign",
                     "currentRoundNumberStorageKey":"rps_current_round_number",
                     "lastRoundStorageKey":"rps_last_round","roundsStorageKey":"rps_rounds"}
                    """;
            case "prometheus.initializer.constant" -> "{\"storageKey\":\"value\",\"value\":null}";
            case "prometheus.initializer.random-choice" ->
                "{\"storageKey\":\"choice\",\"choices\":[\"one\",\"two\"]}";
            case "prometheus.resource.typed-choices" -> "{\"values\":[\"one\",\"two\"]}";
            default -> throw new IllegalStateException("Missing component UI metadata for " + kind);
        });
        AuthoringSpec authoring = authoringSpec(kind);
        return new ComponentUiMetadata(label, description, new ImmutableJson(defaultConfig),
                List.of(new ImmutableJson(defaultConfig)), authoring.role(), authoring.exposure(),
                authoring.capabilityGroup(), authoring.advancedReason());
    }

    private static AuthoringSpec authoringSpec(String kind) {
        return switch (kind) {
            case "prometheus.policy.no-op" -> AuthoringSpec.advanced(ComponentAuthoringRole.RESPONSE_STRATEGY,
                    "Used only for technical situations that intentionally have no ordinary response.");
            case "prometheus.policy.exact-text" -> AuthoringSpec.guided(
                    ComponentAuthoringRole.RESPONSE_STRATEGY, "exact-text-response");
            case "prometheus.policy.prompt" -> AuthoringSpec.guided(
                    ComponentAuthoringRole.RESPONSE_STRATEGY, "prompt-response");
            case "prometheus.policy.rps-reveal", "prometheus.policy.rps-result",
                    "prometheus.action.rps-select-sign", "prometheus.action.rps-evaluate-round" ->
                AuthoringSpec.guided(ComponentAuthoringRole.DETERMINISTIC_OPERATION, "rock-scissor-paper");
            case "prometheus.selector.any", "prometheus.selector.state-path",
                    "prometheus.selector.event-type", "prometheus.selector.actor",
                    "prometheus.selector.event-kind", "prometheus.selector.state-id",
                    "prometheus.selector.all", "prometheus.selector.any-of" ->
                AuthoringSpec.advanced(ComponentAuthoringRole.TECHNICAL_SELECTOR,
                        "Event-history selection is derived by guided authoring and remains inspectable in Advanced.");
            case "prometheus.decision.latest-event-type" -> AuthoringSpec.generated(
                    ComponentAuthoringRole.RULE_TRIGGER,
                    "Generated from a rule's selected event trigger and inspectable in Advanced.");
            case "prometheus.decision.prompt" -> AuthoringSpec.guided(
                    ComponentAuthoringRole.RULE_CONDITION, "semantic-condition");
            case "prometheus.action.extract" -> AuthoringSpec.guided(
                    ComponentAuthoringRole.OUTCOME_EXTRACTION, "outcome-report");
            case "prometheus.action.increment" -> AuthoringSpec.guided(
                    ComponentAuthoringRole.DATA_UPDATE, "increment-value");
            case "prometheus.action.prompt-behaviour" -> AuthoringSpec.guided(
                    ComponentAuthoringRole.RULE_RESPONSE, "prompt-response");
            case "prometheus.initializer.constant", "prometheus.initializer.random-choice" ->
                AuthoringSpec.guided(ComponentAuthoringRole.DATA_INITIALIZER, "starting-context");
            case "prometheus.resource.typed-choices" -> AuthoringSpec.guided(
                    ComponentAuthoringRole.DATA_RESOURCE, "starting-context");
            default -> throw new IllegalStateException("Missing component authoring metadata for " + kind);
        };
    }

    private static JsonNode authoringSchema(JsonNode source, ComponentUiMetadata uiMetadata) {
        ObjectNode schema = (ObjectNode) source.deepCopy();
        schema.put("title", uiMetadata.label());
        schema.put("description", uiMetadata.description());
        schema.set("default", uiMetadata.defaultConfig().value());
        ArrayNode examples = schema.putArray("examples");
        uiMetadata.examples().forEach(example -> examples.add(example.value()));

        JsonNode properties = schema.path("properties");
        if (properties.isObject()) {
            properties.fields().forEachRemaining(field -> {
                if (!(field.getValue() instanceof ObjectNode property)) {
                    return;
                }
                property.put("title", fieldTitle(field.getKey()));
                property.put("description", fieldDescription(field.getKey()));
                JsonNode defaultValue = uiMetadata.defaultConfig().value().get(field.getKey());
                if (defaultValue != null) {
                    property.set("default", defaultValue);
                }
                ArrayNode fieldExamples = property.putArray("examples");
                uiMetadata.examples().stream().map(example -> example.value().get(field.getKey()))
                        .filter(java.util.Objects::nonNull).forEach(fieldExamples::add);
            });
        }
        return schema;
    }

    private static String fieldTitle(String field) {
        String words = field.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('-', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private static String fieldDescription(String field) {
        return switch (field) {
            case "eventType", "handSignEventType" -> "Canonical observation event type used by this component.";
            case "actor" -> "Actor whose matching event may be used.";
            case "eventKind" -> "Runtime event family that may be used.";
            case "maxTextCodePoints" -> "Maximum accepted text length in Unicode code points.";
            case "responsePrompt" -> "Ordered guidance for ordinary or transition response behaviour.";
            case "starterPrompt" -> "Ordered guidance used when a situation begins.";
            case "summaryPrompt" -> "Ordered guidance for summarizing the interaction.";
            case "nonverbalPlanPrompt" -> "Ordered guidance for coordinated nonverbal behaviour.";
            case "gesturePrompt" -> "Ordered guidance for gesture-only behaviour.";
            case "decisionPrompt" -> "Ordered plain-language criterion and examples for this condition.";
            case "extractionPrompt" -> "Ordered guidance for producing the outcome structure.";
            case "storageBindings" -> "Declared data values read or written by this component.";
            case "consumedObservations" -> "Observation capabilities this component may consume.";
            case "emittedModalities" -> "Output modalities this component may emit.";
            case "types", "actors", "kinds", "stateIds", "selectors" ->
                "Technical event-history selection values.";
            case "targetStorageKey", "storageKey", "roundsStorageKey", "currentAgentSignStorageKey",
                    "currentRoundNumberStorageKey", "lastRoundStorageKey" ->
                "Stable key of the definition-owned data used by this component.";
            case "outputSchema" -> "Strict JSON shape produced for the outcome value.";
            case "value" -> "Fixed starting value written during initialization.";
            case "choices" -> "Inline values from which one deterministic seeded choice is made.";
            case "choicesResourceId" -> "Definition resource containing the available starting values.";
            case "values" -> "Reusable typed values owned by this definition resource.";
            default -> "Typed configuration value for this registered component.";
        };
    }

    private record AuthoringSpec(ComponentAuthoringRole role, ComponentAuthoringExposure exposure,
            String capabilityGroup, String advancedReason) {

        private static AuthoringSpec guided(ComponentAuthoringRole role, String capabilityGroup) {
            return new AuthoringSpec(role, ComponentAuthoringExposure.GUIDED, capabilityGroup, null);
        }

        private static AuthoringSpec advanced(ComponentAuthoringRole role, String reason) {
            return new AuthoringSpec(role, ComponentAuthoringExposure.ADVANCED, null, reason);
        }

        private static AuthoringSpec generated(ComponentAuthoringRole role, String reason) {
            return new AuthoringSpec(role, ComponentAuthoringExposure.GENERATED_INTERNAL, null, reason);
        }
    }

    private static String promptExample(String sectionId, String sectionKind, String content, String field) {
        return "{\"" + field + "\":{\"sections\":[{\"id\":\"" + sectionId + "\",\"kind\":\""
                + sectionKind + "\",\"content\":" + JSON.valueToTree(content) + "}]}}";
    }

    private static ComponentSemantics promptSemantics(JsonNode config) {
        return new ComponentSemantics(strings(config, "consumedObservations"), strings(config, "emittedModalities"),
                storageUses(config), List.of(), List.of());
    }

    private static ComponentSemantics rpsRevealSemantics(JsonNode config) {
        return new ComponentSemantics(Set.of(), RPS_REVEAL_MODALITIES, List.of(
                storageUse(config, "currentAgentSignStorageKey", ComponentStorageAccess.READ, RPS_SIGN_SCHEMA),
                storageUse(config, "currentRoundNumberStorageKey", ComponentStorageAccess.READ,
                        POSITIVE_INTEGER_SCHEMA)), List.of(), List.of());
    }

    private static ComponentSemantics rpsResultSemantics(JsonNode config) {
        return new ComponentSemantics(Set.of(), RPS_RESULT_MODALITIES, List.of(
                storageUse(config, "lastRoundStorageKey", ComponentStorageAccess.READ, RPS_ROUND_SCHEMA)),
                List.of(), List.of());
    }

    private static ComponentSemantics rpsSelectSemantics(JsonNode config) {
        return new ComponentSemantics(Set.of(), Set.of(), List.of(
                storageUse(config, "roundsStorageKey", ComponentStorageAccess.READ, RPS_ROUNDS_SCHEMA),
                storageUse(config, "currentAgentSignStorageKey", ComponentStorageAccess.WRITE, RPS_SIGN_SCHEMA),
                storageUse(config, "currentRoundNumberStorageKey", ComponentStorageAccess.WRITE,
                        POSITIVE_INTEGER_SCHEMA)), List.of(), List.of());
    }

    private static ComponentSemantics rpsEvaluateSemantics(JsonNode config) {
        return new ComponentSemantics(Set.of(config.path("handSignEventType").asText()), Set.of(), List.of(
                storageUse(config, "currentAgentSignStorageKey", ComponentStorageAccess.READ, RPS_SIGN_SCHEMA),
                storageUse(config, "currentRoundNumberStorageKey", ComponentStorageAccess.READ,
                        POSITIVE_INTEGER_SCHEMA),
                storageUse(config, "lastRoundStorageKey", ComponentStorageAccess.WRITE, RPS_ROUND_SCHEMA),
                storageUse(config, "roundsStorageKey", ComponentStorageAccess.READ_WRITE, RPS_ROUNDS_SCHEMA)),
                List.of(), List.of());
    }

    private static ComponentStorageUse storageUse(JsonNode config, String field, ComponentStorageAccess access,
            JsonNode expectedSchema) {
        return new ComponentStorageUse(config.path(field).asText(), access, expectedSchema, "/config/" + field);
    }

    private static ComponentSemantics withPrimaryStorageUse(JsonNode config, String field,
            ComponentStorageAccess access) {
        List<ComponentStorageUse> uses = new ArrayList<>(storageUses(config));
        uses.add(new ComponentStorageUse(config.path(field).asText(), access, config.get("outputSchema"),
                "/config/" + field));
        return new ComponentSemantics(Set.of(), Set.of(), uses, List.of(), List.of());
    }

    private static ComponentSemantics randomInitializerSemantics(JsonNode config) {
        ComponentSemantics storage = withPrimaryStorageUse(config, "storageKey", ComponentStorageAccess.WRITE);
        List<ComponentReference> resources = config.has("choicesResourceId")
                ? List.of(new ComponentReference(config.path("choicesResourceId").asText(),
                        "/config/choicesResourceId", new ComponentKey("prometheus.resource.typed-choices", 1)))
                : List.of();
        return new ComponentSemantics(storage.consumedObservations(), storage.emittedBehaviourModalities(),
                storage.storageUses(), resources, storage.stateReferences());
    }

    private static List<ComponentStorageUse> storageUses(JsonNode config) {
        List<ComponentStorageUse> uses = new ArrayList<>();
        JsonNode bindings = config.path("storageBindings");
        for (int index = 0; index < bindings.size(); index++) {
            JsonNode binding = bindings.get(index);
            uses.add(new ComponentStorageUse(binding.path("key").asText(), access(binding.path("access").asText()),
                    binding.get("expectedValueSchema"), "/config/storageBindings/" + index + "/key"));
        }
        return List.copyOf(uses);
    }

    private static List<CompiledStorageBinding> compiledStorageBindings(JsonNode config) {
        List<CompiledStorageBinding> bindings = new ArrayList<>();
        JsonNode source = config.path("storageBindings");
        for (int index = 0; index < source.size(); index++) {
            JsonNode binding = source.get(index);
            bindings.add(new CompiledStorageBinding(binding.path("key").asText(),
                    access(binding.path("access").asText()), immutableOrNull(binding.get("expectedValueSchema"))));
        }
        return List.copyOf(bindings);
    }

    private static ComponentStorageAccess access(String value) {
        return switch (value) {
            case "write" -> ComponentStorageAccess.WRITE;
            case "read-write" -> ComponentStorageAccess.READ_WRITE;
            default -> ComponentStorageAccess.READ;
        };
    }

    private static List<ComponentReference> references(JsonNode config, String field) {
        List<ComponentReference> references = new ArrayList<>();
        for (int index = 0; index < config.path(field).size(); index++) {
            references.add(new ComponentReference(config.path(field).get(index).asText(),
                    "/config/" + field + "/" + index));
        }
        return List.copyOf(references);
    }

    private static Set<String> strings(JsonNode config, String field) {
        return Set.copyOf(new LinkedHashSet<>(stringList(config, field)));
    }

    private static List<String> stringList(JsonNode config, String field) {
        List<String> values = new ArrayList<>();
        config.path(field).forEach(value -> values.add(value.asText()));
        return List.copyOf(values);
    }

    private static List<ImmutableJson> immutableList(JsonNode array) {
        List<ImmutableJson> values = new ArrayList<>();
        array.forEach(value -> values.add(new ImmutableJson(value)));
        return List.copyOf(values);
    }

    private static ImmutableJson immutableOrNull(JsonNode value) {
        return value == null ? null : new ImmutableJson(value);
    }

    private static String prompt(JsonNode config, String field) {
        JsonNode prompt = config.get(field);
        if (prompt == null) {
            return "";
        }
        List<String> sections = new ArrayList<>();
        prompt.path("sections").forEach(section -> sections.add(
                PromptComposer.normalizeLineEndings(section.path("content").asText())));
        return String.join(PromptComposer.SECTION_SEPARATOR, sections);
    }

    private static String arraySchema(String field) {
        return STRING_ARRAY_SCHEMA.formatted(field, field);
    }

    private static String requiredStringSchema(String field) {
        return """
                {"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object",
                 "properties":{"%s":{"type":"string","minLength":1}},"required":["%s"],"additionalProperties":false}
                """.formatted(field, field);
    }

    private static String requiredStringsSchema(String... fields) {
        List<String> properties = new ArrayList<>();
        List<String> required = new ArrayList<>();
        for (String field : fields) {
            String name = JSON.valueToTree(field).toString();
            properties.add(name + ":{\"type\":\"string\",\"minLength\":1}");
            required.add(name);
        }
        return "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"type\":\"object\","
                + "\"properties\":{" + String.join(",", properties) + "},\"required\":["
                + String.join(",", required) + "],\"additionalProperties\":false}";
    }

    private static String exactTextSchema() {
        return """
                {"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object","properties":{
                 "eventType":{"type":"string","minLength":1},"actor":{"type":"string","minLength":1},
                 "eventKind":{"type":"string","minLength":1},
                 "maxTextCodePoints":{"type":"integer","minimum":1,"maximum":100000}},
                 "required":["eventType","actor","eventKind","maxTextCodePoints"],"additionalProperties":false}
                """;
    }

    private static String promptPolicySchema() {
        return promptComponentSchema(List.of("responsePrompt", "starterPrompt", "summaryPrompt",
                "nonverbalPlanPrompt", "gesturePrompt"));
    }

    private static String promptDecisionSchema() {
        return promptComponentSchema(List.of("decisionPrompt"));
    }

    private static String promptComponentSchema(List<String> promptFields) {
        List<String> properties = new ArrayList<>();
        promptFields.forEach(field -> properties.add(JSON.valueToTree(field).toString() + ":" + PROMPT_SCHEMA));
        properties.add("\"storageBindings\":" + STORAGE_BINDINGS_SCHEMA);
        properties.add("\"consumedObservations\":{" +
                "\"type\":\"array\",\"uniqueItems\":true,\"items\":{\"type\":\"string\",\"minLength\":1}}");
        properties.add("\"emittedModalities\":{" +
                "\"type\":\"array\",\"uniqueItems\":true,\"items\":{\"type\":\"string\",\"minLength\":1}}");
        return "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"type\":\"object\"," +
                "\"properties\":{" + String.join(",", properties) + "},\"additionalProperties\":false}";
    }

    private static String extractionSchema() {
        return """
                {"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object","properties":{
                 "targetStorageKey":{"type":"string","minLength":1},"extractionPrompt":%s,
                 "outputSchema":{"type":"object"},"storageBindings":%s},
                 "required":["targetStorageKey"],"additionalProperties":false}
                """.formatted(PROMPT_SCHEMA, STORAGE_BINDINGS_SCHEMA);
    }

    private static String constantInitializerSchema() {
        return """
                {"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object","properties":{
                 "storageKey":{"type":"string","minLength":1},"value":{}},
                 "required":["storageKey","value"],"additionalProperties":false}
                """;
    }

    private static String randomInitializerSchema() {
        return """
                {"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object","properties":{
                 "storageKey":{"type":"string","minLength":1},"choices":{"type":"array","minItems":1,"items":{}},
                 "choicesResourceId":{"type":"string","minLength":1}},"required":["storageKey"],
                 "oneOf":[{"required":["choices"],"not":{"required":["choicesResourceId"]}},
                 {"required":["choicesResourceId"],"not":{"required":["choices"]}}],"additionalProperties":false}
                """;
    }

    private static String typedChoicesSchema() {
        return """
                {"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object","properties":{
                 "values":{"type":"array","minItems":1,"items":{}}},"required":["values"],"additionalProperties":false}
                """;
    }

    private static String compositeSelectorSchema() {
        return """
                {"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object","properties":{
                 "selectors":{"type":"array","minItems":1,"items":{"type":"object","properties":{
                 "kind":{"type":"string","minLength":1},"version":{"type":"integer","minimum":1},
                 "config":{"type":"object"}},"required":["kind","version","config"],"additionalProperties":false}}},
                 "required":["selectors"],"additionalProperties":false}
                """;
    }

    private static JsonNode tree(String json) {
        try {
            return JSON.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static JsonNode arrayValueSchema(JsonNode itemSchema) {
        var schema = JSON.createObjectNode().put("type", "array");
        schema.set("items", itemSchema.deepCopy());
        return schema;
    }
}
