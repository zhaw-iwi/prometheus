package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ch.zhaw.prometheus.agentdefs.core.*;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.task.*;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

@SpringBootTest(properties = "prometheus.runtime.tick.enabled=false")
class CoreTaskVoicePersistenceIntegrationTest {
    @Autowired AgentRepository agents;
    @MockitoBean LanguageModelGateway language;
    @Test void coordinationControlPermissionRemainsConsumedAfterReload() {
        var agent = new CoordinationAgent().createAgent(); var id = agents.saveAndFlush(agent).getId();
        var owner = new ExternalSpeech(UUID.randomUUID(), agent.executionEpoch());
        var runtime = new PolicyRuntime(new PromptMessageAssembler(), language).withExternalSpeech(owner);
        when(language.infer(any())).thenReturn("{\"operation\":\"PROPOSE\",\"agreement\":" + CoordinationAgentIntegrationTest.AGREEMENT
                + ",\"reply\":{\"speech\":\"Propose an invitation\"}}");
        agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Propose an invitation"), runtime);
        agents.saveAndFlush(agent); agent = agents.findById(id).orElseThrow();
        var policy = (CoordinationPolicy) agent.getCurrentState().ownPolicy();
        assertNotNull(policy.liveActivityContext().continuation()); Instant start = Instant.now();
        var speech = agent.recordExternalSpeech("The proposal is not active. Shall we start now?", new SpeechProvenance(
                SpeechProvenance.Origin.NATIVE, owner.sessionId(), owner.epoch(), "proposal-capture", SpeechProvenance.Association.NONE, List.of(), true));
        assertTrue(policy.liveSpeechCaptured(speech, owner, start, Instant.now(), List.of())); agents.saveAndFlush(agent);
        policy = (CoordinationPolicy) agents.findById(id).orElseThrow().getCurrentState().ownPolicy();
        assertNull(policy.liveActivityContext().continuation());
        assertEquals("READY", policy.statusPhase());
    }
    @Test void bothDefinitionsReloadConfirmedProposalAndAcceptOnceWithCurrentInvitation() {
        for (var candidate : List.of(new GenericMultimodalBehaviour().createAgent(), new GenericMultimodalBehaviourAtSocialSpace().createAgent())) {
            var id = agents.saveAndFlush(candidate).getId(); var agent = agents.findById(id).orElseThrow();
            var owner = new ExternalSpeech(UUID.randomUUID(), agent.executionEpoch());
            var live = new PolicyRuntime(new PromptMessageAssembler(), language).withExternalSpeech(owner);
            when(language.infer(any())).thenReturn("""
                    {"operation":"PROPOSE","task":{"goal":"Show paper on rock","maxActions":1,"firstAction":"ON_CUE",
                    "rules":[{"eventType":"obs.hand.sign","field":"sign","operator":"eq","value":"rock",
                    "action":"Show paper silently","effect":"ACT","minConfidence":0.8,"samples":1,"cooldownSeconds":3}]},"reply":{}}
                    """);
            agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Propose a task"), live); agents.saveAndFlush(agent);
            agent = agents.findById(id).orElseThrow(); var policy = (CoreTaskPolicy) agent.getCurrentState().ownPolicy();
            assertNotNull(policy.liveActivityContext().continuation()); assertTrue(policy.liveActivityContext().facts().contains("NOT ACTIVE"));
            Instant started = Instant.now();
            var event = agent.recordExternalSpeech("Shall I start the proposed task?", new SpeechProvenance(SpeechProvenance.Origin.NATIVE,
                    owner.sessionId(), owner.epoch(), "proposal-1", SpeechProvenance.Association.NONE, List.of(), true));
            assertFalse(policy.liveSpeechCaptured(event, owner, started, Instant.now(), List.of())); agents.saveAndFlush(agent);
            agent = agents.findById(id).orElseThrow(); policy = (CoreTaskPolicy) agent.getCurrentState().ownPolicy();
            assertTrue(policy.liveResolvedControlReply("yes", agent.getEventHistory(), owner));
            when(language.infer(any())).thenReturn("{\"operation\":\"ACTIVATE\",\"task\":null,\"reply\":{}}");
            agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Yes"), live); agents.saveAndFlush(agent);
            agent = agents.findById(id).orElseThrow(); policy = (CoreTaskPolicy) agent.getCurrentState().ownPolicy();
            assertEquals("RUNNING", TaskMemory.phase(policy.storage())); assertEquals(0, policy.storage().get(TaskMemory.ACTIONS).getAsInt());
            assertEquals("PENDING", TaskResponse.status(policy.storage()));
            assertFalse(agent.getEventHistory().toList().stream().anyMatch(ConversationProjection::isIntent));
            clearInvocations(language); agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Okay"), live);
            agents.saveAndFlush(agent); verify(language, never()).infer(any());
            assertEquals("RUNNING", TaskMemory.phase(((TaskPolicy) agents.findById(id).orElseThrow().getCurrentState().ownPolicy()).storage()));
        }
    }
}
