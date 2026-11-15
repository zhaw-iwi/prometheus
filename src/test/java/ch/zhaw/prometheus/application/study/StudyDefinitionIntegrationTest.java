package ch.zhaw.prometheus.application.study;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ch.zhaw.prometheus.application.*;
import ch.zhaw.prometheus.agentdefs.study.StudyCoordinationAgent;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.OutputProfile;
import ch.zhaw.prometheus.model.task.TaskPolicy;
import ch.zhaw.prometheus.model.coordination.CoordinationMemory;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

/** Disposable local MySQL only, through the isolated acceptance runner. */
@SpringBootTest(properties={"prometheus.runtime.tick.enabled=false","prometheus.study.sensing=simulation"})
class StudyDefinitionIntegrationTest {
    @Autowired ScopedDemoService demo;
    @Autowired AccessCodeAdminService admin;
    @Autowired AgentRepository agents;
    @MockitoBean LanguageModelGateway gateway;
    @Test void studyDefinitionPersistsNeutralProfileAndGuardedProposal() {
        String code = UUID.randomUUID().toString().substring(0,5);
        var access = admin.createAccessCode(code, true);
        admin.replaceAllowedAgentTypes(access.getId(), List.of(StudyCoordinationAgent.KEY));
        var id = demo.createAgent(code, StudyCoordinationAgent.KEY).getID();
        when(gateway.infer(any())).thenReturn("""
                {"operation":"PROPOSE","agreement":{"goal":"Write a greeting","humanRole":"Judge the wording",
                "agentRole":"Write one greeting","success":"I accept it","questions":[],"checks":[],"dialogueLimit":2,"reactions":null},
                "reply":{"speech":"Shall we start?"}}
                """);
        demo.acknowledge(code,id,new EventRequest(Event.TYPE_USER_UTTERANCE,"user",Event.KIND_OBSERVATION,
                "Propose a greeting, do not start"),OutputProfile.FULL_PLAN).orElseThrow();
        var reloaded = agents.findById(id).orElseThrow();
        assertEquals("Coordination Assistant", reloaded.getName());
        assertFalse(reloaded.getInteractionProfile().isExternalRealtimeSpeech());
        var memory = ((TaskPolicy)reloaded.getCurrentState().ownPolicy()).storage();
        assertEquals("READY", CoordinationMemory.stage(memory));
        assertFalse(memory.containsKey(CoordinationMemory.AGREEMENT));
        assertEquals("Write a greeting", memory.get(CoordinationMemory.DRAFT).getAsJsonObject().get("goal").getAsString());
    }
}
