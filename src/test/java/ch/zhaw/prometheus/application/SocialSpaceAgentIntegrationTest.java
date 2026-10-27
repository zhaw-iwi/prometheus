package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import ch.zhaw.prometheus.agentdefs.core.GenericMultimodalBehaviourAtSocialSpace;
import ch.zhaw.prometheus.application.live.LiveVoicePolicyAdapter;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.model.AgentEmbodiment;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.OutputProfile;
import ch.zhaw.prometheus.model.task.TaskMemory;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

@SpringBootTest(properties = "prometheus.runtime.tick.enabled=false")
class SocialSpaceAgentIntegrationTest {
    @Autowired AgentRepository agents;
    @Autowired AccessCodeAdminService admin;
    @Autowired ScopedDemoService demo;
    @Autowired AgentApplicationService application;
    @MockitoBean LanguageModelGateway language;

    @Test void legacyScopedRegistrationReloadPresenceTaskAndResetRetainTheSocialSpaceDefinition() {
        String code = UUID.randomUUID().toString().substring(0, 5);
        var access = admin.createAccessCode(code, true);
        String key = GenericMultimodalBehaviourAtSocialSpace.KEY;
        admin.replaceAllowedAgentTypes(access.getId(), List.of(key));
        assertTrue(demo.listAgentTypes(code).stream().anyMatch(type -> key.equals(type.getKey())));
        var created = demo.createAgent(code, key, AgentEmbodiment.ROBOT.name());
        UUID id = created.getID();
        assertEquals("Valerian", created.getPersonaName());
        assertEquals("ROBOT", created.getEmbodiment());
        var reloaded = agents.findById(id).orElseThrow();
        assertEquals("Valerian", reloaded.getPersonaName());
        assertEquals(AgentEmbodiment.ROBOT, reloaded.getEmbodiment());
        assertEquals("Valerian", application.getAgentInfo(id).orElseThrow().getPersonaName());
        assertEquals("Valerian", demo.getAgentInfo(code, id).orElseThrow().getPersonaName());
        // Saved pre-CA-03 instances have no opt-in; keep their arrival semantics covered.
        ((ch.zhaw.prometheus.model.task.TaskPolicy) reloaded.getCurrentState().ownPolicy()).storage()
                .remove(ch.zhaw.prometheus.model.behaviour.SensoryRemarks.ENABLED);
        agents.saveAndFlush(reloaded);
        assertEquals(created.getName(), application.getAgentInfo(id).orElseThrow().getName());
        assertEquals(created.getName(), demo.getAgentInfo(code, id).orElseThrow().getName());
        assertTrue(new LiveVoicePolicyAdapter().instructions(reloaded).contains("Your name is Valerian"));
        verifyNoInteractions(language);

        when(language.infer(any())).thenReturn("{\"speech\":\"Hello there.\"}");
        grouping(code, id, 0);
        grouping(code, id, 1);
        grouping(code, id, 1);
        verify(language, times(1)).infer(any());
        grouping(code, id, 2);
        grouping(code, id, 0);
        grouping(code, id, 0);
        verify(language, times(3)).infer(any());
        var events = agents.findById(id).orElseThrow().getEventHistory().toList();
        assertTrue(events.stream().anyMatch(event -> Event.TYPE_SOCIAL_SITUATION_CHANGE.equals(event.getType())));
        assertEquals(4, events.stream().filter(event -> Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(event.getType())).count());

        when(language.infer(any())).thenReturn("""
                {"operation":"PROPOSE","task":{"goal":"Respond to hand signs","maxActions":3,"sessionBound":true,"rules":[
                {"eventType":"obs.hand.sign","field":"sign","operator":"eq","value":"rock",
                 "action":"Show paper","effect":"ACT","minConfidence":0,"samples":1,"cooldownSeconds":3}]},
                 "reply":{"speech":"A proposal, awaiting your acceptance."}}
                """);
        say(code, id, "Let's plan a hand-sign interaction.");
        var draft = agents.findById(id).orElseThrow().getStorage().get(TaskMemory.DRAFT);
        assertNotNull(draft);
        when(language.infer(any())).thenReturn("{\"operation\":\"ACTIVATE\",\"task\":null,\"reply\":{\"speech\":\"Let's begin.\"}}");
        say(code, id, "Start it now.");
        assertEquals(draft, agents.findById(id).orElseThrow().getStorage().get(TaskMemory.SPEC));
        clearInvocations(language);
        grouping(code, id, 1);
        grouping(code, id, 1);
        say(code, id, "Pause");
        grouping(code, id, 0);
        assertEquals("PAUSED", agents.findById(id).orElseThrow().getStorage().get(TaskMemory.PHASE).getAsString());
        assertEquals(1, agents.findById(id).orElseThrow().getStorage().get(TaskMemory.ACTIONS).getAsInt());
        verifyNoInteractions(language);
        demo.reset(code, id);
        reloaded = agents.findById(id).orElseThrow();
        assertFalse(reloaded.getStorage().containsKey(TaskMemory.SPEC));
        assertEquals("Valerian", reloaded.getPersonaName());
    }

    private void grouping(String code, UUID id, int count) {
        demo.acknowledge(code, id, new EventRequest(Event.TYPE_SOCIAL_GROUPING, "sensor", Event.KIND_OBSERVATION,
                "{\"humanCount\":" + count + ",\"groupCount\":0,\"singletonCount\":" + count
                        + ",\"largestGroupSize\":" + Math.min(count, 1) + ",\"ts\":\"" + Instant.now() + "\"}"), OutputProfile.FULL_PLAN);
    }

    private void say(String code, UUID id, String speech) {
        demo.acknowledge(code, id, new EventRequest(Event.TYPE_USER_UTTERANCE, "user", Event.KIND_OBSERVATION, speech), OutputProfile.FULL_PLAN);
    }
}
