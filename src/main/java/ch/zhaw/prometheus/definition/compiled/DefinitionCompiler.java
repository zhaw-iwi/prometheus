package ch.zhaw.prometheus.definition.compiled;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ch.zhaw.prometheus.definition.component.CompiledAction;
import ch.zhaw.prometheus.definition.component.CompiledComponent;
import ch.zhaw.prometheus.definition.component.CompiledDecision;
import ch.zhaw.prometheus.definition.component.CompiledInitializer;
import ch.zhaw.prometheus.definition.component.CompiledPolicy;
import ch.zhaw.prometheus.definition.component.CompiledResource;
import ch.zhaw.prometheus.definition.component.CompiledSelector;
import ch.zhaw.prometheus.definition.component.ComponentCategory;
import ch.zhaw.prometheus.definition.component.ComponentConfigViolation;
import ch.zhaw.prometheus.definition.component.ComponentKey;
import ch.zhaw.prometheus.definition.component.ComponentRegistry;
import ch.zhaw.prometheus.definition.document.AgentDefinitionDocument;
import ch.zhaw.prometheus.definition.document.AgentDefinitionJson;
import ch.zhaw.prometheus.definition.document.AtomicStateDefinition;
import ch.zhaw.prometheus.definition.document.ComponentEnvelope;
import ch.zhaw.prometheus.definition.document.CompositeStateDefinition;
import ch.zhaw.prometheus.definition.document.DefinitionResource;
import ch.zhaw.prometheus.definition.document.FinalStateDefinition;
import ch.zhaw.prometheus.definition.document.StateDefinition;
import ch.zhaw.prometheus.definition.document.StorageDefinition;
import ch.zhaw.prometheus.definition.document.TransitionDefinition;
import ch.zhaw.prometheus.definition.validation.DefinitionSemanticValidator;
import ch.zhaw.prometheus.definition.validation.DefinitionValidationResult;
import ch.zhaw.prometheus.definition.validation.SemanticDiagnosticCode;
import ch.zhaw.prometheus.definition.validation.ValidationDiagnostic;

/** Converts a fully validated JSON document model into a shareable immutable graph. */
public final class DefinitionCompiler implements CompiledDefinitionFactory {
    private final ComponentRegistry componentRegistry;
    private final DefinitionSemanticValidator semanticValidator;
    private final AgentDefinitionJson definitionJson;

    public DefinitionCompiler(ComponentRegistry componentRegistry) {
        this(componentRegistry, new AgentDefinitionJson());
    }

    public DefinitionCompiler(ComponentRegistry componentRegistry, AgentDefinitionJson definitionJson) {
        if (componentRegistry == null || definitionJson == null) {
            throw new IllegalArgumentException("componentRegistry and definitionJson must not be null");
        }
        this.componentRegistry = componentRegistry;
        this.semanticValidator = new DefinitionSemanticValidator(componentRegistry);
        this.definitionJson = definitionJson;
    }

    public DefinitionValidationResult validate(AgentDefinitionDocument definition) {
        List<ValidationDiagnostic> diagnostics = new ArrayList<>(this.semanticValidator.validate(definition).diagnostics());
        for (LocatedComponent located : locateComponents(definition)) {
            var registered = this.componentRegistry.find(located.envelope().kind(), located.envelope().version());
            if (registered.isEmpty()) {
                diagnostics.add(diagnostic(SemanticDiagnosticCode.UNKNOWN_COMPONENT, located.pointer(),
                        "Component '" + located.envelope().kind() + "' version " + located.envelope().version()
                                + " is not registered.",
                        "Select a registered component kind and version."));
                continue;
            }
            if (registered.get().category() != located.expectedCategory()) {
                diagnostics.add(diagnostic(SemanticDiagnosticCode.COMPONENT_CATEGORY_MISMATCH, located.pointer(),
                        "Component '" + located.envelope().kind() + "' is registered as "
                                + registered.get().category() + " but this location requires "
                                + located.expectedCategory() + ".",
                        "Select a component from the required category."));
            }
            for (ComponentConfigViolation violation : this.componentRegistry.validateConfig(located.envelope())) {
                diagnostics.add(diagnostic(SemanticDiagnosticCode.INVALID_COMPONENT_CONFIG,
                        located.pointer() + "/config" + violation.pointer(),
                        violation.message(), "Correct the component configuration to match its registered schema."));
            }
        }
        return new DefinitionValidationResult(diagnostics);
    }

    @Override
    public CompiledAgentDefinition compile(AgentDefinitionDocument definition) {
        DefinitionValidationResult validation = validate(definition);
        if (!validation.isValid()) {
            throw new DefinitionCompilationException("Agent definition has " + validation.errors().size()
                    + " compilation error(s); first: " + validation.errors().getFirst().code() + " at "
                    + validation.errors().getFirst().pointer(), validation);
        }
        try {
            return compileValidated(definition);
        } catch (ComponentCompilationFailure failure) {
            ValidationDiagnostic diagnostic = diagnostic(SemanticDiagnosticCode.COMPONENT_COMPILATION_FAILED,
                    failure.pointer(), "Registered component compilation failed.",
                    "Correct the component factory or its configuration.");
            DefinitionValidationResult failed = new DefinitionValidationResult(
                    append(validation.diagnostics(), diagnostic));
            throw new DefinitionCompilationException("Component compilation failed at " + failure.pointer(), failed,
                    failure.getCause());
        }
    }

    private CompiledAgentDefinition compileValidated(AgentDefinitionDocument definition) {
        Map<String, StateDefinition> sourceStates = new LinkedHashMap<>();
        definition.states().forEach(state -> sourceStates.put(state.id(), state));
        StateCompiler stateCompiler = new StateCompiler(sourceStates);
        List<CompiledState> states = definition.states().stream().map(stateCompiler::compile).toList();
        Map<String, CompiledState> statesById = new LinkedHashMap<>();
        states.forEach(state -> statesById.put(state.id(), state));

        List<CompiledStorageDefinition> storage = definition.storage().stream().map(this::compileStorage).toList();
        List<CompiledResourceDefinition> resources = new ArrayList<>();
        for (int index = 0; index < definition.resources().size(); index++) {
            DefinitionResource resource = definition.resources().get(index);
            ComponentEnvelope envelope = new ComponentEnvelope(resource.kind(), resource.version(), resource.config());
            resources.add(new CompiledResourceDefinition(resource.id(),
                    new ComponentKey(resource.kind(), resource.version()),
                    compileAt(envelope, CompiledResource.class, "/resources/" + index)));
        }
        List<CompiledInitializer> initializers = new ArrayList<>();
        for (int index = 0; index < definition.lifecycle().initializers().size(); index++) {
            initializers.add(compileAt(definition.lifecycle().initializers().get(index), CompiledInitializer.class,
                    "/lifecycle/initializers/" + index));
        }
        CompiledLifecycle lifecycle = new CompiledLifecycle(statesById.get(definition.lifecycle().initialStateId()),
                definition.lifecycle().startOnCreation(), initializers, definition.lifecycle().reset().storage(),
                definition.lifecycle().reset().history());

        List<CompiledTransition> transitions = new ArrayList<>();
        for (int transitionIndex = 0; transitionIndex < definition.transitions().size(); transitionIndex++) {
            TransitionDefinition transition = definition.transitions().get(transitionIndex);
            List<CompiledDecision> decisions = new ArrayList<>();
            for (int index = 0; index < transition.decisions().size(); index++) {
                decisions.add(compileAt(transition.decisions().get(index), CompiledDecision.class,
                        "/transitions/" + transitionIndex + "/decisions/" + index));
            }
            List<CompiledAction> actions = new ArrayList<>();
            for (int index = 0; index < transition.actions().size(); index++) {
                actions.add(compileAt(transition.actions().get(index), CompiledAction.class,
                        "/transitions/" + transitionIndex + "/actions/" + index));
            }
            transitions.add(new CompiledTransition(transition.id(), statesById.get(transition.sourceStateId()),
                    statesById.get(transition.targetStateId()), transition.order(), decisions, actions));
        }

        return new CompiledAgentDefinition(definition.schemaVersion(), definition.key(), definition.revision(),
                this.definitionJson.contentHash(definition),
                new CompiledAgentMetadata(definition.metadata().displayName(), definition.metadata().description(),
                        definition.metadata().categoryPath(), definition.metadata().languageCode(),
                        definition.metadata().tags()),
                new CompiledInteraction(definition.interaction().supportedObservations(),
                        definition.interaction().supportedBehaviourModalities(), definition.interaction().profileTags()),
                lifecycle, storage, resources, states, statesById, transitions);
    }

    private CompiledStorageDefinition compileStorage(StorageDefinition storage) {
        List<ImmutableJson> examples = storage.examples().stream().map(ImmutableJson::new).toList();
        return new CompiledStorageDefinition(storage.key(), storage.description(), new ImmutableJson(storage.valueSchema()),
                storage.required(), storage.visibility(), storage.reset(),
                storage.initialValue() == null ? null : new ImmutableJson(storage.initialValue()), examples);
    }

    private <T extends CompiledComponent> T compileAt(ComponentEnvelope envelope, Class<T> type, String pointer) {
        try {
            return type.cast(this.componentRegistry.compile(envelope));
        } catch (RuntimeException exception) {
            throw new ComponentCompilationFailure(pointer, exception);
        }
    }

    private final class StateCompiler {
        private final Map<String, StateDefinition> sourceStates;
        private final Map<String, CompiledState> compiledStates = new LinkedHashMap<>();

        private StateCompiler(Map<String, StateDefinition> sourceStates) {
            this.sourceStates = sourceStates;
        }

        private CompiledState compile(StateDefinition state) {
            CompiledState existing = this.compiledStates.get(state.id());
            if (existing != null) {
                return existing;
            }
            int index = new ArrayList<>(this.sourceStates.keySet()).indexOf(state.id());
            String pointer = "/states/" + index;
            CompiledState compiled;
            if (state instanceof AtomicStateDefinition atomic) {
                compiled = new CompiledAtomicState(atomic.id(), atomic.name(), atomic.entryMode(), atomic.oblivious(),
                        optional(atomic.eventSelector(), CompiledSelector.class, pointer + "/eventSelector"),
                        optional(atomic.policy(), CompiledPolicy.class, pointer + "/policy"));
            } else if (state instanceof CompositeStateDefinition composite) {
                List<CompiledState> children = composite.childStateIds().stream()
                        .map(childId -> compile(this.sourceStates.get(childId))).toList();
                compiled = new CompiledCompositeState(composite.id(), composite.name(), composite.entryMode(),
                        composite.oblivious(),
                        optional(composite.eventSelector(), CompiledSelector.class, pointer + "/eventSelector"),
                        optional(composite.policy(), CompiledPolicy.class, pointer + "/policy"), children,
                        compile(this.sourceStates.get(composite.initialChildStateId())));
            } else {
                FinalStateDefinition finalState = (FinalStateDefinition) state;
                compiled = new CompiledFinalState(finalState.id(), finalState.name());
            }
            this.compiledStates.put(state.id(), compiled);
            return compiled;
        }

        private <T extends CompiledComponent> T optional(ComponentEnvelope envelope, Class<T> type, String pointer) {
            return envelope == null ? null : compileAt(envelope, type, pointer);
        }
    }

    private static List<LocatedComponent> locateComponents(AgentDefinitionDocument definition) {
        List<LocatedComponent> components = new ArrayList<>();
        for (int index = 0; index < definition.lifecycle().initializers().size(); index++) {
            components.add(new LocatedComponent(definition.lifecycle().initializers().get(index),
                    ComponentCategory.INITIALIZER, "/lifecycle/initializers/" + index));
        }
        for (int index = 0; index < definition.resources().size(); index++) {
            DefinitionResource resource = definition.resources().get(index);
            components.add(new LocatedComponent(new ComponentEnvelope(resource.kind(), resource.version(),
                    resource.config()), ComponentCategory.RESOURCE, "/resources/" + index));
        }
        for (int index = 0; index < definition.states().size(); index++) {
            StateDefinition state = definition.states().get(index);
            if (state instanceof AtomicStateDefinition atomic) {
                add(components, atomic.eventSelector(), ComponentCategory.SELECTOR, "/states/" + index + "/eventSelector");
                add(components, atomic.policy(), ComponentCategory.POLICY, "/states/" + index + "/policy");
            } else if (state instanceof CompositeStateDefinition composite) {
                add(components, composite.eventSelector(), ComponentCategory.SELECTOR,
                        "/states/" + index + "/eventSelector");
                add(components, composite.policy(), ComponentCategory.POLICY, "/states/" + index + "/policy");
            }
        }
        for (int transitionIndex = 0; transitionIndex < definition.transitions().size(); transitionIndex++) {
            TransitionDefinition transition = definition.transitions().get(transitionIndex);
            for (int index = 0; index < transition.decisions().size(); index++) {
                components.add(new LocatedComponent(transition.decisions().get(index), ComponentCategory.DECISION,
                        "/transitions/" + transitionIndex + "/decisions/" + index));
            }
            for (int index = 0; index < transition.actions().size(); index++) {
                components.add(new LocatedComponent(transition.actions().get(index), ComponentCategory.ACTION,
                        "/transitions/" + transitionIndex + "/actions/" + index));
            }
        }
        return components;
    }

    private static void add(List<LocatedComponent> components, ComponentEnvelope envelope,
            ComponentCategory category, String pointer) {
        if (envelope != null) {
            components.add(new LocatedComponent(envelope, category, pointer));
            if (category == ComponentCategory.SELECTOR && envelope.config() != null
                    && envelope.config().path("selectors").isArray()) {
                for (int index = 0; index < envelope.config().path("selectors").size(); index++) {
                    var nested = envelope.config().path("selectors").get(index);
                    if (nested.path("kind").isTextual() && nested.path("version").canConvertToInt()
                            && nested.path("config").isObject()) {
                        add(components, new ComponentEnvelope(nested.path("kind").asText(),
                                nested.path("version").asInt(), nested.path("config")), ComponentCategory.SELECTOR,
                                pointer + "/config/selectors/" + index);
                    }
                }
            }
        }
    }

    private static ValidationDiagnostic diagnostic(SemanticDiagnosticCode code, String pointer, String message,
            String hint) {
        return ValidationDiagnostic.of(code, pointer, message, hint);
    }

    private static List<ValidationDiagnostic> append(List<ValidationDiagnostic> diagnostics,
            ValidationDiagnostic diagnostic) {
        List<ValidationDiagnostic> appended = new ArrayList<>(diagnostics);
        appended.add(diagnostic);
        return appended;
    }

    private record LocatedComponent(ComponentEnvelope envelope, ComponentCategory expectedCategory, String pointer) {
    }

    private static final class ComponentCompilationFailure extends RuntimeException {
        private final String pointer;

        private ComponentCompilationFailure(String pointer, Throwable cause) {
            super(cause);
            this.pointer = pointer;
        }

        private String pointer() {
            return this.pointer;
        }
    }
}
