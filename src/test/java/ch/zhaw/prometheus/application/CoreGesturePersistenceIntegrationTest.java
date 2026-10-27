package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ch.zhaw.prometheus.agentdefs.core.*;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.behaviour.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.task.*;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

@SpringBootTest(properties = "prometheus.runtime.tick.enabled=false")
class CoreGesturePersistenceIntegrationTest {
    @Autowired AgentRepository agents;
    @MockitoBean LanguageModelGateway language;
    final String beat = "{\"nonVerbal\":{\"gesture\":\"EXPLAIN\"}}";
    final PromptMessageAssembler assembler = new PromptMessageAssembler();

    @Test void sharedHistorySurvivesReloadForBothPolicyFamiliesAndResetClearsIt() {
        for (Agent candidate : java.util.List.of(new LiveMultimodal().createAgent(), new GenericMultimodalBehaviour().createAgent(),
                new GenericMultimodalBehaviourAtSocialSpace().createAgent())) {
            var id = agents.saveAndFlush(candidate).getId();
            Agent loaded = agents.findById(id).orElseThrow();
            assertEquals("EXPLAIN", publish(loaded)); agents.saveAndFlush(loaded);
            loaded = agents.findById(id).orElseThrow();
            assertEquals("NONE", publish(loaded)); agents.saveAndFlush(loaded);
            loaded = agents.findById(id).orElseThrow(); loaded.reset(); agents.saveAndFlush(loaded);
            loaded = agents.findById(id).orElseThrow(); assertEquals("EXPLAIN", publish(loaded));
        }
    }

    private String publish(Agent agent) {
        when(language.infer(any())).thenReturn(beat);
        var policy = agent.getCurrentState().ownPolicy();
        if (policy instanceof TaskPolicy task) task.storage().put(TaskMemory.REPLY, BehaviourPlan.fromJson(beat).toJsonObject());
        var plan = policy.onStart(agent.getCurrentState(), new EventHistory(), assembler, language);
        return plan.getNonVerbal().getAsJsonObject().get("gesture").getAsString();
    }
}
