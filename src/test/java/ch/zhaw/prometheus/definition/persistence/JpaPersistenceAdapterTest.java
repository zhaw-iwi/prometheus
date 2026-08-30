package ch.zhaw.prometheus.definition.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import ch.zhaw.prometheus.definition.catalog.BundledDefinitionCatalog;
import ch.zhaw.prometheus.definition.compiled.ImmutableJson;
import ch.zhaw.prometheus.definition.document.AgentDefinitionJson;
import ch.zhaw.prometheus.definition.instance.PersistedDeclarativeAgent;
import ch.zhaw.prometheus.definition.instance.RuntimeInstanceStatus;
import ch.zhaw.prometheus.definition.repository.DefinitionProvenance;
import ch.zhaw.prometheus.definition.repository.DefinitionStatus;
import ch.zhaw.prometheus.definition.repository.NewDefinitionRevision;
import ch.zhaw.prometheus.definition.runtime.RuntimeEvent;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:designer_jpa;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.jpa.show-sql=false",
        "debug=false",
        "logging.level.root=WARN"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ JpaDefinitionRepository.class, JpaDeclarativeAgentRepository.class })
class JpaPersistenceAdapterTest {
    @Autowired
    private JpaDefinitionRepository definitions;

    @Autowired
    private JpaDeclarativeAgentRepository instances;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = { AgentDefinitionIdentityEntity.class, AgentDefinitionRevisionEntity.class,
            DeclarativeAgentInstanceEntity.class })
    @EnableJpaRepositories(basePackageClasses = { AgentDefinitionIdentityJpaRepository.class,
            AgentDefinitionRevisionJpaRepository.class, DeclarativeAgentInstanceJpaRepository.class })
    static class TestApplication {
    }

    @Test
    void canonicalDefinitionAndLightweightInstanceRoundTripThroughNativeJsonColumns() {
        AgentDefinitionJson json = new AgentDefinitionJson();
        var document = BundledDefinitionCatalog.loadMainCatalog().require("core.talk_to_me").document();
        var identity = this.definitions.createDefinition(document.key());
        var revision = this.definitions.createRevision(new NewDefinitionRevision(identity.id(), document.revision(),
                document.schemaVersion(), DefinitionStatus.PUBLISHED, json.canonicalJson(document),
                json.contentHash(document), DefinitionProvenance.BUNDLED, "test-resource"));
        identity = this.definitions.activate(identity.id(), revision.id(), identity.optimisticVersion());

        assertEquals(revision.contentHash(), this.definitions.findRevision(revision.id()).orElseThrow().contentHash());
        assertEquals(json.canonicalJson(document),
                this.definitions.findRevision(revision.id()).orElseThrow().canonicalJson());
        assertEquals(revision.id(), identity.activeRevisionId());
        assertEquals(List.of(document.key()), this.definitions.findDefinitions().stream()
                .map(definition -> definition.key()).toList());

        UUID instanceId = UUID.randomUUID();
        PersistedDeclarativeAgent instance = new PersistedDeclarativeAgent(instanceId, revision.id(), "talk",
                Map.of("message", new ImmutableJson(JsonNodeFactory.instance.textNode("current"))),
                Map.of("message", new ImmutableJson(JsonNodeFactory.instance.textNode("initial"))),
                List.of(new RuntimeEvent("obs.user_utterance", "user", "observation", "hello", List.of("talk"))),
                true, RuntimeInstanceStatus.ACTIVE, 0, null, null);
        var created = this.instances.create(instance);
        var reloaded = this.instances.find(instanceId).orElseThrow();
        assertEquals("talk", reloaded.activeLeafStateId());
        assertEquals("current", reloaded.storage().get("message").value().asText());
        assertEquals("hello", reloaded.history().getFirst().payload());
        assertEquals("talk", reloaded.history().getFirst().statePath().getFirst());
        assertTrue(reloaded.history().getFirst().id() != null);
        assertTrue(reloaded.history().getFirst().createdAt() != null);
        var updated = this.instances.update(created.withRuntime("talk", created.initialStorage(), List.of(), false,
                RuntimeInstanceStatus.ACTIVE), created.optimisticVersion());

        assertEquals(revision.id(), updated.definitionRevisionId());
        assertEquals("initial", updated.storage().get("message").value().asText());
        assertTrue(updated.history().isEmpty());
        assertEquals(1, updated.optimisticVersion());
        assertTrue(this.instances.existsByDefinitionRevisionId(revision.id()));
    }
}
