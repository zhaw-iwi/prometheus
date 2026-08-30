package ch.zhaw.prometheus.definition.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.definition.catalog.BundledAgentDefinition;
import ch.zhaw.prometheus.definition.catalog.BundledDefinitionCatalog;
import ch.zhaw.prometheus.definition.compiled.CompiledDefinitionCache;
import ch.zhaw.prometheus.definition.compiled.DefinitionCompiler;
import ch.zhaw.prometheus.definition.component.BuiltInComponentCatalog;
import ch.zhaw.prometheus.definition.document.AgentDefinitionJson;
import ch.zhaw.prometheus.definition.repository.DefinitionProvenance;
import ch.zhaw.prometheus.definition.support.InMemoryDefinitionRepository;

class BundledDefinitionImporterUnitTest {
    private InMemoryDefinitionRepository repository;
    private AgentDefinitionJson json;
    private DefinitionCompiler compiler;
    private CompiledDefinitionCache cache;
    private DefinitionLifecycleService lifecycle;
    private BundledDefinitionImporter importer;

    @BeforeEach
    void setUp() {
        this.repository = new InMemoryDefinitionRepository();
        this.json = new AgentDefinitionJson();
        this.compiler = new DefinitionCompiler(BuiltInComponentCatalog.createRegistry(), this.json);
        DefinitionRevisionSources sources = new DefinitionRevisionSources(this.repository, this.json);
        this.cache = new CompiledDefinitionCache(this.compiler, sources, null);
        this.lifecycle = new DefinitionLifecycleService(this.repository, this.json, this.compiler, this.cache, sources);
        this.importer = new BundledDefinitionImporter(this.repository, this.json);
    }

    @Test
    void firstAndSecondImportAreDeterministicAndActiveDefinitionsPrewarm() {
        var definitions = BundledDefinitionCatalog.loadMainCatalog().definitions();

        assertEquals(new BundledImportResult(12, 12, 12, 0), this.importer.importDefinitions(definitions));
        assertEquals(new BundledImportResult(0, 0, 0, 12), this.importer.importDefinitions(definitions));
        assertEquals(12, this.repository.definitionCount());
        assertEquals(12, this.repository.revisionCount());
        assertEquals(12, this.lifecycle.prewarmActive().size());
        assertEquals(12, this.cache.size());
    }

    @Test
    void sameKeyAndRevisionWithDifferentCanonicalHashFailsWithoutMutation() {
        BundledAgentDefinition original = BundledDefinitionCatalog.loadMainCatalog().require("core.talk_to_me");
        this.importer.importDefinitions(List.of(original));
        var changedDocument = DefinitionLifecycleServiceUnitTest.withDescription(original.document(),
                "Conflicting source-controlled content");
        BundledAgentDefinition conflicting = new BundledAgentDefinition(original.resource(), changedDocument,
                this.compiler.compile(changedDocument));

        assertThrows(BundledDefinitionConflictException.class,
                () -> this.importer.importDefinitions(List.of(conflicting)));
        assertEquals(1, this.repository.definitionCount());
        assertEquals(1, this.repository.revisionCount());
    }

    @Test
    void laterImportNeverOverridesActiveDesignerRevision() {
        BundledAgentDefinition bundled = BundledDefinitionCatalog.loadMainCatalog().require("core.talk_to_me");
        this.importer.importDefinitions(List.of(bundled));
        var designerDocument = DefinitionLifecycleServiceUnitTest.withRevision(
                DefinitionLifecycleServiceUnitTest.withDescription(bundled.document(), "Designer revision"), 2);
        var draft = this.lifecycle.createDraft(this.json.canonicalJson(designerDocument),
                DefinitionProvenance.DESIGNER, "designer");
        var published = this.lifecycle.publish(designerDocument.key(), 2, draft.optimisticVersion());
        var identity = this.lifecycle.requireDefinition(designerDocument.key());
        this.lifecycle.activate(designerDocument.key(), 2, identity.optimisticVersion());

        this.importer.importDefinitions(List.of(bundled));

        assertEquals(published.id(), this.lifecycle.requireDefinition(designerDocument.key()).activeRevisionId());
    }
}
