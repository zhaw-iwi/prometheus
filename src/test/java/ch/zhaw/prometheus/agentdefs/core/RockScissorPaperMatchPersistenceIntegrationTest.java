package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.OuterState;
import ch.zhaw.prometheus.model.rps.RpsStorageKeys;
import ch.zhaw.prometheus.repositories.AgentRepository;
import jakarta.persistence.EntityManager;

@SpringBootTest
@Transactional
class RockScissorPaperMatchPersistenceIntegrationTest {
    @Autowired
    private AgentRepository agents;

    @Autowired
    private EntityManager entityManager;

    @Test
    void completeMatchStateGraphSurvivesPersistenceRoundTrip() {
        Agent saved = this.agents.saveAndFlush(new RockScissorPaperMatch().createAgent());
        UUID id = saved.getId();
        this.entityManager.clear();

        Agent loaded = this.agents.findById(id).orElseThrow();

        assertEquals("Valerian Core - Rock, Scissor, Paper Match", loaded.getName());
        assertTrue(loaded.getCurrentState() instanceof OuterState);
        assertTrue(loaded.listStates().contains("Valerian Core RPS Match Setup"));
        assertTrue(loaded.listStates().contains("Valerian Core RPS Match Round Result"));
        assertTrue(loaded.listStates().contains("Valerian Core RPS Match Result"));
    }

    @Test
    void germanMatchLanguageSurvivesPersistenceRoundTrip() {
        Agent saved = this.agents.saveAndFlush(new GermanRockScissorPaperMatch().createAgent());
        UUID id = saved.getId();
        this.entityManager.clear();

        Agent loaded = this.agents.findById(id).orElseThrow();

        assertEquals("Valerian Core - Schere, Stein, Papier Match (Deutsch)", loaded.getName());
        assertEquals("de", loaded.getLanguageCode());
        assertEquals("de", loaded.getStorage().get(RpsStorageKeys.LANGUAGE_CODE).getAsString());
    }
}
