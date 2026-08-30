package ch.zhaw.prometheus.definition.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.definition.catalog.BundledDefinitionCatalog;
import ch.zhaw.prometheus.definition.compiled.CompiledDefinitionCache;
import ch.zhaw.prometheus.definition.compiled.DefinitionCompilationException;
import ch.zhaw.prometheus.definition.compiled.DefinitionCompiler;
import ch.zhaw.prometheus.definition.component.BuiltInComponentCatalog;
import ch.zhaw.prometheus.definition.document.AgentDefinitionDocument;
import ch.zhaw.prometheus.definition.document.AgentDefinitionJson;
import ch.zhaw.prometheus.definition.document.AgentLifecycle;
import ch.zhaw.prometheus.definition.repository.DefinitionLifecycleException;
import ch.zhaw.prometheus.definition.repository.DefinitionOptimisticLockException;
import ch.zhaw.prometheus.definition.repository.DefinitionProvenance;
import ch.zhaw.prometheus.definition.repository.DefinitionStatus;
import ch.zhaw.prometheus.definition.support.InMemoryDefinitionRepository;

public class DefinitionLifecycleServiceUnitTest {
    private InMemoryDefinitionRepository repository;
    private AgentDefinitionJson json;
    private CompiledDefinitionCache cache;
    private DefinitionLifecycleService lifecycle;
    private AgentDefinitionDocument source;

    @BeforeEach
    void setUp() {
        this.repository = new InMemoryDefinitionRepository();
        this.json = new AgentDefinitionJson();
        DefinitionCompiler compiler = new DefinitionCompiler(BuiltInComponentCatalog.createRegistry(), this.json);
        DefinitionRevisionSources sources = new DefinitionRevisionSources(this.repository, this.json);
        this.cache = new CompiledDefinitionCache(compiler, sources, null);
        this.lifecycle = new DefinitionLifecycleService(this.repository, this.json, compiler, this.cache, sources);
        this.source = BundledDefinitionCatalog.loadMainCatalog().require("core.talk_to_me").document();
    }

    @Test
    void draftUsesCanonicalHashOptimisticConcurrencyAndPublishedContentIsImmutable() {
        var draft = this.lifecycle.createDraft("\n" + this.json.canonicalJson(this.source) + "\n",
                DefinitionProvenance.DESIGNER, "unit-test");

        assertEquals(DefinitionStatus.DRAFT, draft.status());
        assertEquals(this.json.canonicalJson(this.source), draft.canonicalJson());
        assertEquals(this.json.contentHash(this.source), draft.contentHash());

        AgentDefinitionDocument changed = withDescription(this.source, "Updated draft description");
        var updated = this.lifecycle.updateDraft(this.source.key(), this.source.revision(),
                this.json.canonicalJson(changed), draft.optimisticVersion());
        assertEquals(1, updated.optimisticVersion());
        assertNotEquals(draft.contentHash(), updated.contentHash());
        assertThrows(DefinitionOptimisticLockException.class, () -> this.lifecycle.updateDraft(this.source.key(),
                this.source.revision(), this.json.canonicalJson(this.source), draft.optimisticVersion()));
        assertThrows(DefinitionOptimisticLockException.class, () -> this.lifecycle.publish(this.source.key(),
                this.source.revision(), draft.optimisticVersion()));
        assertEquals(0, this.cache.size());

        var published = this.lifecycle.publish(this.source.key(), this.source.revision(),
                updated.optimisticVersion());
        assertEquals(DefinitionStatus.PUBLISHED, published.status());
        assertEquals(1, this.cache.size());
        assertThrows(DefinitionLifecycleException.class, () -> this.lifecycle.updateDraft(this.source.key(),
                this.source.revision(), this.json.canonicalJson(this.source), published.optimisticVersion()));
        assertThrows(DefinitionLifecycleException.class, () -> this.repository.replaceDraft(published.id(),
                published.optimisticVersion(), this.json.canonicalJson(this.source),
                this.json.contentHash(this.source)));
    }

    @Test
    void publicationCompilesBeforeStatusChangeAndActivationRejectsDraftAndArchivedRevisions() {
        var draft = this.lifecycle.createDraft(this.json.canonicalJson(this.source), DefinitionProvenance.DESIGNER,
                null);
        AgentDefinitionDocument invalidDocument = withLifecycle(this.source,
                new AgentLifecycle("missing_state", this.source.lifecycle().startOnCreation(),
                        this.source.lifecycle().initializers(), this.source.lifecycle().reset()));
        var invalid = this.lifecycle.updateDraft(this.source.key(), this.source.revision(),
                this.json.canonicalJson(invalidDocument), draft.optimisticVersion());

        assertThrows(DefinitionCompilationException.class, () -> this.lifecycle.publish(this.source.key(),
                this.source.revision(), invalid.optimisticVersion()));
        assertEquals(DefinitionStatus.DRAFT,
                this.lifecycle.requireRevision(this.source.key(), this.source.revision()).status());
        assertEquals(0, this.cache.size());
        assertThrows(DefinitionLifecycleException.class, () -> this.lifecycle.activate(this.source.key(),
                this.source.revision(), this.lifecycle.requireDefinition(this.source.key()).optimisticVersion()));
    }

    @Test
    void activationIsExplicitAndOnlyNonActivePublishedRevisionCanArchive() {
        var firstDraft = this.lifecycle.createDraft(this.json.canonicalJson(this.source), DefinitionProvenance.DESIGNER,
                null);
        var first = this.lifecycle.publish(this.source.key(), 1, firstDraft.optimisticVersion());
        var initialIdentity = this.lifecycle.requireDefinition(this.source.key());
        var identity = this.lifecycle.activate(this.source.key(), 1, initialIdentity.optimisticVersion());
        assertEquals(first.id(), identity.activeRevisionId());
        assertThrows(DefinitionLifecycleException.class,
                () -> this.lifecycle.archive(this.source.key(), 1, first.optimisticVersion()));

        AgentDefinitionDocument secondDocument = withRevision(this.source, 2);
        var secondDraft = this.lifecycle.createDraft(this.json.canonicalJson(secondDocument),
                DefinitionProvenance.DESIGNER, null);
        assertThrows(DefinitionLifecycleException.class,
                () -> this.lifecycle.activate(this.source.key(), 2, identity.optimisticVersion()));
        var second = this.lifecycle.publish(this.source.key(), 2, secondDraft.optimisticVersion());
        assertThrows(DefinitionOptimisticLockException.class,
                () -> this.lifecycle.activate(this.source.key(), 2, identity.optimisticVersion() - 1));
        var archived = this.lifecycle.archive(this.source.key(), 2, second.optimisticVersion());
        assertEquals(DefinitionStatus.ARCHIVED, archived.status());
        assertTrue(archived.archivedAt() != null);
        assertThrows(DefinitionLifecycleException.class,
                () -> this.lifecycle.activate(this.source.key(), 2, identity.optimisticVersion()));
    }

    @Test
    void designerListCloneValidateAndCanonicalExportUseTheRealCompilerAndRepositoryPath() {
        new BundledDefinitionImporter(this.repository, this.json).importMainCatalog();

        assertEquals(12, this.lifecycle.listDefinitions().size());
        assertEquals("core.facial_expression_sensitivity", this.lifecycle.listDefinitions().getFirst().key());
        assertEquals(1, this.lifecycle.listRevisions(this.source.key()).size());

        var clone = this.lifecycle.cloneRevision(this.source.key(), 1, this.source.key(), 2);
        assertEquals(DefinitionStatus.DRAFT, clone.status());
        assertEquals(DefinitionProvenance.DESIGNER, clone.provenance());
        assertEquals("clone:core.talk_to_me:1", clone.sourceDetail());
        assertEquals(2, this.json.parse(this.lifecycle.export(this.source.key(), 2)).revision());
        assertTrue(this.lifecycle.validate(clone.canonicalJson()).isValid());

        var published = this.lifecycle.publish(this.source.key(), 2, clone.optimisticVersion());
        assertEquals(DefinitionStatus.PUBLISHED, published.status());
        assertEquals(1, this.cache.size());
        String exported = this.lifecycle.export(this.source.key(), 2);
        assertEquals(published.canonicalJson(), exported);
        assertThrows(DefinitionLifecycleException.class,
                () -> this.lifecycle.createDraft(published.canonicalJson(), DefinitionProvenance.IMPORTED,
                        "designer-api-import"));

        InMemoryDefinitionRepository importRepository = new InMemoryDefinitionRepository();
        AgentDefinitionJson importJson = new AgentDefinitionJson();
        DefinitionCompiler importCompiler = new DefinitionCompiler(BuiltInComponentCatalog.createRegistry(),
                importJson);
        DefinitionRevisionSources importSources = new DefinitionRevisionSources(importRepository, importJson);
        DefinitionLifecycleService importLifecycle = new DefinitionLifecycleService(importRepository, importJson,
                importCompiler, new CompiledDefinitionCache(importCompiler, importSources, null), importSources);
        var imported = importLifecycle.createDraft(exported, DefinitionProvenance.IMPORTED,
                "designer-api-import");
        assertEquals(exported, imported.canonicalJson());
        assertEquals(DefinitionProvenance.IMPORTED, imported.provenance());

        AgentDefinitionDocument invalid = withLifecycle(withRevision(this.source, 3),
                new AgentLifecycle("missing_state", this.source.lifecycle().startOnCreation(),
                        this.source.lifecycle().initializers(), this.source.lifecycle().reset()));
        var validation = this.lifecycle.validate(this.json.canonicalJson(invalid));
        assertTrue(validation.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().name().equals("MISSING_INITIAL_STATE")));
    }

    public static AgentDefinitionDocument withRevision(AgentDefinitionDocument document, int revision) {
        return new AgentDefinitionDocument(document.schema(), document.schemaVersion(), document.key(), revision,
                document.metadata(), document.interaction(), document.lifecycle(), document.storage(),
                document.resources(), document.states(), document.transitions(), document.verification());
    }

    public static AgentDefinitionDocument withDescription(AgentDefinitionDocument document, String description) {
        var metadata = document.metadata();
        var changed = new ch.zhaw.prometheus.definition.document.AgentMetadata(metadata.displayName(), description,
                metadata.categoryPath(), metadata.languageCode(), metadata.tags());
        return new AgentDefinitionDocument(document.schema(), document.schemaVersion(), document.key(),
                document.revision(), changed, document.interaction(), document.lifecycle(), document.storage(),
                document.resources(), document.states(), document.transitions(), document.verification());
    }

    private static AgentDefinitionDocument withLifecycle(AgentDefinitionDocument document, AgentLifecycle lifecycle) {
        return new AgentDefinitionDocument(document.schema(), document.schemaVersion(), document.key(),
                document.revision(), document.metadata(), document.interaction(), lifecycle, document.storage(),
                document.resources(), document.states(), document.transitions(), document.verification());
    }
}
