package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.*;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.access.*;
import ch.zhaw.prometheus.model.policy.NoOpPolicy;
import ch.zhaw.prometheus.repositories.*;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

@SpringBootTest(properties={"spring.jpa.properties.hibernate.generate_statistics=true", "prometheus.runtime.tick.enabled=false"})
@AutoConfigureMockMvc
@Transactional
class StateGraphIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired AgentRepository agents;
    @Autowired AccessCodeRepository codes;
    @Autowired AccessCodeAgentRepository links;
    @Autowired AgentApplicationService service;
    @Autowired ch.zhaw.prometheus.logging.AgentMonitorBroadcaster monitor;
    @Autowired EntityManagerFactory emf;
    @PersistenceContext EntityManager em;
    @MockitoBean LanguageModelGateway language;

    @Test void savedGraphSurvivesReloadIsScopedAndMonitorIdentityRequiresNoExtraQueries() throws Exception {
        String code = UUID.randomUUID().toString().substring(0,5);
        var access = codes.saveAndFlush(new AccessCode(code, true));
        var leaf = new State("Same", new NoOpPolicy(), List.of());
        var end = new Final("Same"); leaf.addTransition(new Transition(end));
        var outer = new OuterState("SECRET PROMPT", "Outer", List.of(), leaf);
        var agent = agents.saveAndFlush(new Agent("Graph", "", outer));
        links.saveAndFlush(new AccessCodeAgent(access,agent));
        var first = StateGraph.of(agent); UUID id = agent.getId(); em.clear();
        var loaded = agents.findById(id).orElseThrow();
        assertEquals(first,StateGraph.of(loaded));
        mvc.perform(get("/demo/agents/"+id+"/state-graph").header("X-Prometheus-Access-Code",code))
            .andExpect(status().isOk()).andExpect(jsonPath("$.nodes.length()").value(3))
            .andExpect(jsonPath("$.edges.length()").value(1))
            .andExpect(jsonPath("$.initialStateId").value(outer.getId().toString()))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SECRET PROMPT"))));
        var other = codes.saveAndFlush(new AccessCode(UUID.randomUUID().toString().substring(0,5),true));
        mvc.perform(get("/demo/agents/"+id+"/state-graph").header("X-Prometheus-Access-Code",other.getCode())).andExpect(status().isNotFound());
        mvc.perform(get("/demo/agents/"+id+"/state-graph")).andExpect(status().isUnauthorized());
        var stats = emf.unwrap(SessionFactory.class).getStatistics(); stats.clear();
        assertEquals(first.activeStateIds(), service.getAgentState(id).orElseThrow().getActiveStateIds());
        stats.clear();
        // The monitor already owns a loaded graph. Adding identities must not query or evaluate anything.
        var snapshot = (ch.zhaw.prometheus.controllers.views.AgentMonitorSnapshotView)
                org.springframework.test.util.ReflectionTestUtils.invokeMethod(monitor,"toSnapshot",loaded);
        assertEquals(first.activeStateIds(),snapshot.getActiveStateIds());
        assertEquals(0,stats.getPrepareStatementCount()); verifyNoInteractions(language);
    }
}
