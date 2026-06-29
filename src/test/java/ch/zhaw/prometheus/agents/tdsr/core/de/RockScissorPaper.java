package ch.zhaw.prometheus.agents.tdsr.core.de;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import ch.zhaw.prometheus.agentdefs.AgentCreationContext;
import ch.zhaw.prometheus.agentdefs.AgentCreationResult;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

@SpringBootTest
public class RockScissorPaper {
    private static final ch.zhaw.prometheus.agentdefs.tdsr.core.de.RockScissorPaper DEFINITION = new ch.zhaw.prometheus.agentdefs.tdsr.core.de.RockScissorPaper();

    @Autowired
    private AgentRepository repository;
    @Autowired
    private PromptMessageAssembler promptMessageAssembler;
    @Autowired
    private LanguageModelGateway languageModelGateway;

    static Agent createAgentDefinition() {
        return DEFINITION.createAgent();
    }

    @Test
    void setUp() {
        AgentCreationResult result = DEFINITION.createInstance(
                new AgentCreationContext(this.promptMessageAssembler, this.languageModelGateway));
        Agent saved = this.repository.save(result.agent());
        assertNotNull(saved.getId());
    }
}