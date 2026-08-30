package ch.zhaw.prometheus.definition.compiled;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.ObjectNode;

import ch.zhaw.prometheus.definition.component.BuiltInComponentCatalog;
import ch.zhaw.prometheus.definition.component.builtin.ConstantInitializerComponent;
import ch.zhaw.prometheus.definition.component.builtin.IncrementActionComponent;
import ch.zhaw.prometheus.definition.component.builtin.PromptPolicyComponent;
import ch.zhaw.prometheus.definition.document.AgentDefinitionDocument;
import ch.zhaw.prometheus.definition.document.AgentDefinitionJson;
import ch.zhaw.prometheus.definition.validation.SemanticDiagnosticCode;

class DefinitionCompilerUnitTest {
    private AgentDefinitionJson json;
    private DefinitionCompiler compiler;

    @BeforeEach
    void setUp() {
        this.json = new AgentDefinitionJson();
        this.compiler = new DefinitionCompiler(BuiltInComponentCatalog.createRegistry(), this.json);
    }

    @Test
    void compilesReferencesAndBaselineComponentsIntoOneImmutableGraph() {
        CompiledAgentDefinition compiled = this.compiler.compile(load("deterministic-components.json"));

        assertSame(compiled.state("round"), compiled.lifecycle().initialState());
        assertSame(compiled.state("round"), compiled.transitions().getFirst().sourceState());
        assertSame(compiled.state("round"), compiled.transitions().getFirst().targetState());
        assertInstanceOf(PromptPolicyComponent.class,
                ((CompiledAtomicState) compiled.state("round")).policy());
        assertInstanceOf(ConstantInitializerComponent.class, compiled.lifecycle().initializers().getFirst());
        assertInstanceOf(IncrementActionComponent.class, compiled.transitions().getFirst().actions().getFirst());
        assertEquals("Describe the deterministic result.",
                ((PromptPolicyComponent) ((CompiledAtomicState) compiled.state("round")).policy()).responsePrompt());

        assertThrows(UnsupportedOperationException.class, () -> compiled.states().clear());
        assertThrows(UnsupportedOperationException.class, () -> compiled.statesById().clear());
        ObjectNode leakedCopy = (ObjectNode) compiled.storage().getFirst().valueSchema().value();
        leakedCopy.put("minimum", 99);
        assertEquals(0, compiled.storage().getFirst().valueSchema().value().path("minimum").asInt());
    }

    @Test
    void resolvesCompositeChildrenAndTransitionTargetsByObjectIdentity() {
        CompiledAgentDefinition compiled = this.compiler.compile(load("composite-flow.json"));
        CompiledCompositeState session = assertInstanceOf(CompiledCompositeState.class, compiled.state("session"));

        assertSame(compiled.state("conversation"), session.childStates().getFirst());
        assertSame(compiled.state("conversation"), session.initialChildState());
        assertSame(compiled.state("conversation"), compiled.transitions().getFirst().sourceState());
        assertSame(compiled.state("done"), compiled.transitions().getFirst().targetState());
    }

    @Test
    void equivalentDocumentsCompileToEquivalentArtifacts() {
        AgentDefinitionDocument document = load("deterministic-components.json");
        AgentDefinitionDocument canonicalRoundTrip = this.json.parse(this.json.canonicalJson(document));

        assertEquals(this.compiler.compile(document), this.compiler.compile(canonicalRoundTrip));
        assertNotSame(this.compiler.compile(document), this.compiler.compile(canonicalRoundTrip));
    }

    @Test
    void reportsUnknownVersionInvalidConfigAndWrongCategoryWithStablePointers() {
        String source = resourceText("deterministic-components.json")
                .replace("\"prometheus.policy.prompt\"", "\"prometheus.selector.any\"")
                .replaceFirst("\"version\"\\s*:\\s*1,\\s*\"config\"\\s*:\\s*\\{\\s*\"responsePrompt\"",
                        "\"version\": 1, \"config\": {\"responsePrompt\"");
        AgentDefinitionDocument wrongCategory = this.json.parse(source);
        var categoryResult = this.compiler.validate(wrongCategory);
        assertTrue(categoryResult.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code() == SemanticDiagnosticCode.COMPONENT_CATEGORY_MISMATCH
                        && diagnostic.pointer().equals("/states/0/policy")));
        assertTrue(categoryResult.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code() == SemanticDiagnosticCode.INVALID_COMPONENT_CONFIG));

        AgentDefinitionDocument unknown = this.json.parse(resourceText("minimal-single-state.json")
                .replace("\"prometheus.policy.no-op\"", "\"unknown.policy\""));
        var unknownResult = this.compiler.validate(unknown);
        assertTrue(unknownResult.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code() == SemanticDiagnosticCode.UNKNOWN_COMPONENT
                        && diagnostic.pointer().equals("/states/0/policy")));

        AgentDefinitionDocument unknownVersion = this.json.parse(resourceText("minimal-single-state.json")
                .replace("\"version\": 1", "\"version\": 99"));
        assertTrue(this.compiler.validate(unknownVersion).diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code() == SemanticDiagnosticCode.UNKNOWN_COMPONENT));
    }

    private AgentDefinitionDocument load(String name) {
        try (InputStream input = getClass().getResourceAsStream("/agent-definitions/valid/" + name)) {
            return this.json.parse(input);
        } catch (Exception exception) {
            throw new IllegalArgumentException(exception);
        }
    }

    private String resourceText(String name) {
        try (InputStream input = getClass().getResourceAsStream("/agent-definitions/valid/" + name)) {
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalArgumentException(exception);
        }
    }
}
