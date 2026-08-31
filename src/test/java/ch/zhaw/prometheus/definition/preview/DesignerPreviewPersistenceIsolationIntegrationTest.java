package ch.zhaw.prometheus.definition.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ch.zhaw.prometheus.definition.application.DefinitionLifecycleService;
import ch.zhaw.prometheus.definition.instance.DeclarativeAgentRepository;
import ch.zhaw.prometheus.definition.repository.DefinitionRepository;
import ch.zhaw.prometheus.definition.runtime.RuntimeEvent;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:designer_preview_isolation;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true",
        "debug=false", "logging.level.root=WARN"
})
@Transactional
class DesignerPreviewPersistenceIsolationIntegrationTest {
    @Autowired
    private DesignerPreviewService previews;

    @Autowired
    private DefinitionLifecycleService lifecycle;

    @Autowired
    private DefinitionRepository definitions;

    @Autowired
    private DeclarativeAgentRepository agents;

    @MockitoBean
    private LanguageModelGateway languageModelGateway;

    @Test
    void savedPreviewUsesProductionDefinitionWithoutCreatingDefinitionOrAgentRecords() {
        var stored = this.lifecycle.cloneRevision("core.talk_to_me", 1, "designer.preview_isolation", 1);
        int definitionCount = this.definitions.findDefinitions().size();
        int revisionCount = this.definitions.findRevisions("designer.preview_isolation").size();
        assertTrue(this.agents.findAll().isEmpty());

        var created = this.previews.create(stored.canonicalJson(), DesignerPreviewService.PreviewSource.SAVED,
                stored.id());
        var result = this.previews.acknowledge(created.id(),
                new RuntimeEvent("obs.user_utterance", "user", "observation", "isolated preview"));

        assertEquals("isolated preview", result.transcript().getLast().behaviour().speech());
        assertEquals(stored.id(), result.storedRevisionId());
        assertEquals(definitionCount, this.definitions.findDefinitions().size());
        assertEquals(revisionCount, this.definitions.findRevisions("designer.preview_isolation").size());
        assertTrue(this.agents.findAll().isEmpty());
        verifyNoInteractions(this.languageModelGateway);
        this.previews.close(created.id());
        assertEquals(0, this.previews.sessionCount());
    }

    @Test
    void unsavedScenarioExecutionLeavesNoSessionDefinitionAgentOrHistoryRecord() throws Exception {
        int definitionCount = this.definitions.findDefinitions().size();
        int revisionCount = this.definitions.findRevisions("core.talk_to_me").size();
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode definition = (ObjectNode) mapper.readTree(
                this.lifecycle.requireRevision("core.talk_to_me", 1).canonicalJson());
        ObjectNode scenario = mapper.createObjectNode();
        scenario.put("name", "Exact isolated scenario");
        scenario.withArray("events").addObject().put("type", "obs.user_utterance").put("actor", "user")
                .put("kind", "observation").put("payload", "isolated scenario");
        scenario.withObject("expected").withArray("activeStatePath").add("talk");
        scenario.withObject("expected").withArray("behaviourFragments")
                .addObject().put("speech", "isolated scenario");
        definition.withObject("verification").withArray("scenarios").add(scenario);

        var result = this.previews.executeScenario(mapper.writeValueAsString(definition), 0);

        assertTrue(result.passed(), result.toString());
        assertTrue(result.discarded());
        assertEquals(0, this.previews.sessionCount());
        assertEquals(definitionCount, this.definitions.findDefinitions().size());
        assertEquals(revisionCount, this.definitions.findRevisions("core.talk_to_me").size());
        assertTrue(this.agents.findAll().isEmpty());
        verifyNoInteractions(this.languageModelGateway);
    }
}
