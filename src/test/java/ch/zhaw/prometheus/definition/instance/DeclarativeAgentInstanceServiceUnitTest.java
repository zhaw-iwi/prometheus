package ch.zhaw.prometheus.definition.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import ch.zhaw.prometheus.definition.application.BundledDefinitionImporter;
import ch.zhaw.prometheus.definition.application.DefinitionLifecycleService;
import ch.zhaw.prometheus.definition.application.DefinitionLifecycleServiceUnitTest;
import ch.zhaw.prometheus.definition.application.DefinitionRevisionSources;
import ch.zhaw.prometheus.definition.catalog.BundledAgentDefinition;
import ch.zhaw.prometheus.definition.catalog.BundledDefinitionCatalog;
import ch.zhaw.prometheus.definition.compiled.CompiledDefinitionCache;
import ch.zhaw.prometheus.definition.compiled.DefinitionCompiler;
import ch.zhaw.prometheus.definition.component.BuiltInComponentCatalog;
import ch.zhaw.prometheus.definition.document.AgentDefinitionJson;
import ch.zhaw.prometheus.definition.repository.DefinitionProvenance;
import ch.zhaw.prometheus.definition.runtime.AgentRuntimeContext;
import ch.zhaw.prometheus.definition.runtime.AgentRuntimeEngine;
import ch.zhaw.prometheus.definition.runtime.BuiltInRuntimeComponentExecutor;
import ch.zhaw.prometheus.definition.runtime.RuntimeBehaviour;
import ch.zhaw.prometheus.definition.runtime.RuntimeInvocation;
import ch.zhaw.prometheus.definition.runtime.RuntimeModelGateway;
import ch.zhaw.prometheus.definition.runtime.RuntimePromptBundle;
import ch.zhaw.prometheus.definition.support.InMemoryDeclarativeAgentRepository;
import ch.zhaw.prometheus.definition.support.InMemoryDefinitionRepository;

class DeclarativeAgentInstanceServiceUnitTest {
    @Test
    void createPinsActiveRevisionAndResetRetainsItAfterLaterActivation() {
        InMemoryDefinitionRepository definitions = new InMemoryDefinitionRepository();
        InMemoryDeclarativeAgentRepository instances = new InMemoryDeclarativeAgentRepository();
        AgentDefinitionJson json = new AgentDefinitionJson();
        DefinitionCompiler compiler = new DefinitionCompiler(BuiltInComponentCatalog.createRegistry(), json);
        DefinitionRevisionSources sources = new DefinitionRevisionSources(definitions, json);
        CompiledDefinitionCache cache = new CompiledDefinitionCache(compiler, sources, null);
        DefinitionLifecycleService lifecycle = new DefinitionLifecycleService(definitions, json, compiler, cache,
                sources);
        BundledDefinitionImporter importer = new BundledDefinitionImporter(definitions, json);
        BundledAgentDefinition bundled = BundledDefinitionCatalog.loadMainCatalog().require("core.talk_to_me");
        importer.importDefinitions(List.of(bundled));
        DeclarativeAgentInstanceService service = new DeclarativeAgentInstanceService(instances, lifecycle, cache,
                new AgentRuntimeEngine());
        AgentRuntimeContext context = new AgentRuntimeContext(new BuiltInRuntimeComponentExecutor(new FakeGateway()),
                new Random(7));

        var created = service.create(bundled.document().key(), context);
        long pinnedRevision = created.instance().definitionRevisionId();
        var secondDocument = DefinitionLifecycleServiceUnitTest.withRevision(
                DefinitionLifecycleServiceUnitTest.withDescription(bundled.document(), "Second revision"), 2);
        var draft = lifecycle.createDraft(json.canonicalJson(secondDocument), DefinitionProvenance.DESIGNER, null);
        var published = lifecycle.publish(secondDocument.key(), 2, draft.optimisticVersion());
        var identity = lifecycle.requireDefinition(secondDocument.key());
        lifecycle.activate(secondDocument.key(), 2, identity.optimisticVersion());
        assertEquals(published.id(), lifecycle.requireActiveRevision(secondDocument.key()).id());

        var reset = service.reset(created.instance().id(), context);

        assertEquals(pinnedRevision, reset.instance().definitionRevisionId());
        assertEquals(pinnedRevision, reset.result().before().definitionRevisionId());
        assertEquals(pinnedRevision, reset.result().after().definitionRevisionId());
        assertTrue(instances.existsByDefinitionRevisionId(pinnedRevision));
        assertEquals(1, reset.instance().optimisticVersion());
    }

    private static final class FakeGateway implements RuntimeModelGateway {
        @Override
        public RuntimeBehaviour generate(RuntimePromptBundle prompts, RuntimeInvocation invocation) {
            return RuntimeBehaviour.speechOnly("deterministic");
        }

        @Override
        public boolean decide(String prompt, RuntimeInvocation invocation) {
            return false;
        }

        @Override
        public JsonNode extract(String prompt, JsonNode outputSchema, RuntimeInvocation invocation) {
            return JsonNodeFactory.instance.objectNode();
        }
    }
}
