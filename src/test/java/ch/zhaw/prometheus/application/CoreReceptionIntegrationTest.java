package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import java.time.*;
import java.util.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import ch.zhaw.prometheus.agentdefs.core.*;
import ch.zhaw.prometheus.controllers.dto.LiveSessionRequest;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.task.*;
import ch.zhaw.prometheus.repositories.AgentRepository;
import fixtures.gptlive.LiveSmokeConfiguration;

/** Scoped encounter claims, real JPA reloads and access revocation; controlled provider. */
@SpringBootTest(properties = {"prometheus.live.enabled=true", "prometheus.runtime.tick.enabled=false", "prometheus.core.social-space.reception=true"})
@Import(LiveSmokeConfiguration.class)
class CoreReceptionIntegrationTest {
    @Autowired AgentRepository agents;
    @Autowired AccessCodeAdminService admin;
    @Autowired ScopedDemoService demo;
    @Autowired ScopedLiveSessionService live;
    @Autowired LiveAgentContextService contexts;
    @Autowired LiveSmokeConfiguration.Provider provider;
    @Autowired ExternalSpeechRecordingService recording;
    String code; UUID id;
    Agent loaded() { return agents.findById(id).orElseThrow(); }
    CoreTaskPolicy policy(Agent a) { return (CoreTaskPolicy) a.getCurrentState().ownPolicy(); }
    void sense(int count) { demo.acknowledge(code, id, new EventRequest(Event.TYPE_SOCIAL_GROUPING, "sensor", Event.KIND_OBSERVATION,
            "{\"humanCount\":" + count + ",\"groupCount\":0,\"ts\":\"" + Instant.now() + "\"}"), OutputProfile.FULL_PLAN); }
    @Test void newEncounterKeepsDurableTranscriptAndPausedAgreementButDoesNotSeedPriorDialogue() {
        code = UUID.randomUUID().toString().substring(0, 5); var access = admin.createAccessCode(code, true);
        admin.replaceAllowedAgentTypes(access.getId(), List.of(GenericMultimodalBehaviourAtSocialSpace.KEY));
        id = demo.createAgent(code, GenericMultimodalBehaviourAtSocialSpace.KEY).getID();
        assertTrue(loaded().getInteractionProfile().isPresenceControlledLive());
        assertEquals(2000, contexts.eligibility(code, id).departureGraceMs());
        assertThrows(IllegalStateException.class, () -> live.create(code, id, new LiveSessionRequest("v=0 offer", "willow")));
        sense(1);
        var first = live.create(code, id, new LiveSessionRequest("v=0 offer", "willow")).orElseThrow();
        var handles = new ArrayList<UUID>(); handles.add(first.handle());
        var owner = new ExternalSpeech(first.handle(), loaded().executionEpoch());
        try {
            assertTrue(provider.calls.get(provider.latest).request.input().toString().contains("new reception encounter"));
            assertTrue(recording.record(id, owner, "encounter-one", "Previous visitor private dialogue", List.of(),
                    SpeechProvenance.Association.NONE, true).isPresent());
            var agent = loaded(); var storage = policy(agent).storage();
            var spec = JsonParser.parseString("""
                    {"goal":"Show paper on rock","maxActions":3,"sessionBound":true,"firstAction":"ON_CUE",
                    "rules":[{"eventType":"obs.hand.sign","field":"sign","operator":"eq","value":"rock",
                    "action":"Show paper silently","effect":"ACT","minConfidence":0.8,"samples":1,"cooldownSeconds":3}]}
                    """);
            storage.put(TaskMemory.SPEC, spec); storage.put(TaskMemory.DRAFT, spec.deepCopy());
            storage.put(TaskMemory.PHASE, new JsonPrimitive("RUNNING")); storage.put(TaskMemory.ACTIONS, new JsonPrimitive(1));
            storage.put(TaskMemory.SESSION, new JsonPrimitive(first.handle().toString())); agents.saveAndFlush(agent);
            live.close(code, id, first.handle());
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertEquals("PAUSED", TaskMemory.phase(policy(loaded()).storage())));
            sense(1);
            var second = live.create(code, id, new LiveSessionRequest("v=0 new offer", "willow")).orElseThrow();
            handles.add(second.handle());
            assertNotEquals(first.handle(), second.handle());
            assertFalse(provider.calls.get(provider.latest).request.input().toString().contains("Previous visitor private dialogue"));
            assertTrue(loaded().getEventHistory().toList().stream().anyMatch(e -> e.getPayload().contains("Previous visitor private dialogue")));
            var s = policy(loaded()).storage(); assertEquals("PAUSED", TaskMemory.phase(s)); assertEquals(spec, s.get(TaskMemory.SPEC));
            assertEquals(spec, s.get(TaskMemory.DRAFT)); assertEquals(1, s.get(TaskMemory.ACTIONS).getAsInt());
            assertFalse(policy(loaded()).liveResolvedControlReply("yes", loaded().getEventHistory(), new ExternalSpeech(second.handle(), loaded().executionEpoch())));
            admin.updateAccessCodeEnabled(access.getId(), false);
            assertThrows(DemoAccessDeniedException.class, () -> live.status(code, id, second.handle()));
            await().atMost(Duration.ofSeconds(5)).until(() -> provider.calls.get(provider.latest).closed);
        } finally {
            admin.updateAccessCodeEnabled(access.getId(), true);
            for (var handle : handles) live.close(code, id, handle);
            demo.deleteAgent(code, id);
        }
    }
}
