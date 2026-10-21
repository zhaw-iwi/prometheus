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
import ch.zhaw.prometheus.agentdefs.usecases.GalaDinnerReception;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.model.AgentEmbodiment;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.OutputProfile;
import ch.zhaw.prometheus.model.policy.ExternalSpeech;
import ch.zhaw.prometheus.model.task.TaskMemory;
import ch.zhaw.prometheus.model.task.TaskPolicy;
import ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerTaskPolicy;
import ch.zhaw.prometheus.application.live.LiveVoicePolicyAdapter;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

@SpringBootTest(properties = "prometheus.runtime.tick.enabled=false")
class GalaDinnerReceptionIntegrationTest {
    @Autowired AgentRepository agents;
    @Autowired AccessCodeAdminService admin;
    @Autowired ScopedDemoService demo;
    @MockitoBean LanguageModelGateway language;
    @Autowired LiveAgentContextService contexts;
    @Autowired ExternalSpeechOwnership ownership;
    @Autowired GenericTaskSessionLifecycleService taskLifecycle;

    @Test void coCreatedProposalAndActivationReloadWithLiveTaskGuidanceAndCloseDiscardsTheAgreement() {
        String code = UUID.randomUUID().toString().substring(0, 5);
        var access = admin.createAccessCode(code, true);
        admin.replaceAllowedAgentTypes(access.getId(), List.of(GalaDinnerReception.KEY));
        UUID id = demo.createAgent(code, GalaDinnerReception.KEY, AgentEmbodiment.ROBOT.name()).getID();
        group(code, id, 1);
        UUID session = UUID.randomUUID();
        var startup = contexts.claim(code, id, session).orElseThrow();
        var owner = new ExternalSpeech(session, startup.epoch());
        try {
            when(language.infer(any())).thenReturn(answer("START_TASK", ""), """
                    {"operation":"PROPOSE","task":{"goal":"Auf Papier mit einem Witz reagieren","maxActions":2,
                    "sessionBound":true,"rules":[{"eventType":"obs.hand.sign","field":"sign","operator":"eq",
                    "value":"paper","action":"Sage einen kurzen Witz","effect":"ACT","minConfidence":0.8,
                    "samples":1,"cooldownSeconds":3}]},"reply":{"speech":"Das ist mein Vorschlag. Aktivieren?"}}
                    """);
            say(code, id, "Gestalten wir eine eigene Aufgabe");
            var loaded = agents.findById(id).orElseThrow();
            assertInstanceOf(GalaDinnerTaskPolicy.class, loaded.getCurrentState().ownPolicy());
            assertTrue(contexts.supported(code, id));
            var draft = loaded.getStorage().get(TaskMemory.DRAFT).deepCopy();
            assertEquals(draft, ((TaskPolicy) loaded.getCurrentState().ownPolicy()).storage().get(TaskMemory.DRAFT));
            assertTrue(((GalaDinnerTaskPolicy) loaded.getCurrentState().ownPolicy()).liveActivityContext().facts().contains("Proposed goal: Auf Papier"));
            when(language.infer(any())).thenReturn(
                    "{\"operation\":\"ACTIVATE\",\"task\":null,\"reply\":{\"speech\":\"Aktiviert. Ich warte auf Papier.\"}}");
            say(code, id, "Ja, bitte aktivieren");
            loaded = agents.findById(id).orElseThrow();
            assertEquals(draft, loaded.getStorage().get(TaskMemory.SPEC));
            assertEquals("RUNNING", loaded.getStorage().get(TaskMemory.PHASE).getAsString());
            assertTrue(((GalaDinnerTaskPolicy) loaded.getCurrentState().ownPolicy()).liveActivityContext().facts().contains("Agreed goal: Auf Papier"));
            clearInvocations(language);
            ownership.pauseInput(id, session);
            taskLifecycle.closed(id, owner.epoch(), session);
            contexts.closed(id, owner);
            loaded = agents.findById(id).orElseThrow();
            assertEquals("Gala reception", loaded.getCurrentState().getName());
            assertTrue(loaded.getStorage().keySet().stream().noneMatch(key -> key.startsWith("task.")));
            verifyNoInteractions(language);
        } finally { ownership.release(id, session); }
    }

    @Test void scopedCatalogCreationGamesReloadAndResetPreserveTheEventDefinition() {
        String code = UUID.randomUUID().toString().substring(0, 5);
        var access = admin.createAccessCode(code, true);
        admin.replaceAllowedAgentTypes(access.getId(), List.of(GalaDinnerReception.KEY));
        assertTrue(demo.listAgentTypes(code).stream().anyMatch(type -> GalaDinnerReception.KEY.equals(type.getKey())));
        var created = demo.createAgent(code, GalaDinnerReception.KEY, AgentEmbodiment.ROBOT.name());
        UUID id = created.getID();
        assertEquals("GIGI", created.getPersonaName());
        verifyNoInteractions(language);

        assertTrue(contexts.supported(code, id)); group(code, id, 1);
        UUID session = UUID.randomUUID();
        var startup = contexts.claim(code, id, session).orElseThrow();
        assertTrue(startup.startupInput().toString().contains("greet the visible visitors once"));
        when(language.infer(any())).thenReturn(answer("START_GUESS", ""));
        say(code, id, "Ich möchte eine Person erraten");
        var reloaded = agents.findById(id).orElseThrow();
        assertEquals("Gala guess the person", reloaded.getCurrentState().getName());
        assertEquals("Albert Einstein", reloaded.getStorage().get("gala.person").getAsString());
        when(language.infer(any())).thenReturn(answer("WAIT", ""));
        say(code, id, "Bist du Physiker?");
        assertEquals("Albert Einstein", agents.findById(id).orElseThrow().getStorage().get("gala.person").getAsString());
        say(code, id, "Albert Einstein");
        reloaded = agents.findById(id).orElseThrow();
        assertEquals("Gala reception", reloaded.getCurrentState().getName());
        assertFalse(reloaded.getStorage().containsKey("gala.person"));

        when(language.infer(any())).thenReturn(answer("START_RPS", ""));
        say(code, id, "Schere Stein Papier bitte");
        assertEquals("Gala rock-scissor-paper", agents.findById(id).orElseThrow().getCurrentState().getName());
        demo.acknowledge(code, id, new EventRequest(Event.TYPE_HAND_SIGN, "sensor", Event.KIND_OBSERVATION,
                "{\"sign\":\"paper\",\"confidence\":0.99,\"ts\":\"" + Instant.now() + "\"}"), OutputProfile.FULL_PLAN);
        reloaded = agents.findById(id).orElseThrow();
        assertEquals("Gala reception", reloaded.getCurrentState().getName());
        assertFalse(reloaded.getStorage().containsKey("gala.sign"));
        assertTrue(reloaded.getEventHistory().toList().stream().anyMatch(event ->
                Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(event.getType()) && event.getPayload().contains("handSign")));

        say(code, id, "Vorheriger Besucher"); group(code, id, 0);
        assertEquals("Gala reception", agents.findById(id).orElseThrow().getCurrentState().getName());
        ownership.release(id, session); group(code, id, 1);
        UUID nextSession = UUID.randomUUID();
        var next = contexts.claim(code, id, nextSession).orElseThrow();
        assertFalse(next.startupInput().toString().contains("Vorheriger Besucher"));
        when(language.infer(any())).thenReturn(answer("START_GUESS", ""));
        say(code, id, "Noch ein Ratespiel");
        contexts.closed(id, new ch.zhaw.prometheus.model.policy.ExternalSpeech(session, startup.epoch()));
        assertEquals("Gala guess the person", agents.findById(id).orElseThrow().getCurrentState().getName());
        var owner = new ch.zhaw.prometheus.model.policy.ExternalSpeech(nextSession, next.epoch());
        ownership.pauseInput(id, nextSession);
        contexts.closed(id, owner);
        assertEquals("Gala reception", agents.findById(id).orElseThrow().getCurrentState().getName());
        assertFalse(agents.findById(id).orElseThrow().getStorage().containsKey("gala.person"));
        ownership.release(id, nextSession); demo.reset(code, id);
        reloaded = agents.findById(id).orElseThrow();
        assertEquals("GIGI", reloaded.getPersonaName());
        assertEquals("de", reloaded.getLanguageCode());
        assertEquals("Gala reception", reloaded.getCurrentState().getName());
        assertFalse(reloaded.getStorage().containsKey("gala.person"));
        assertFalse(reloaded.getStorage().containsKey("gala.sign"));
    }
    private void say(String code, UUID id, String speech) {
        demo.acknowledge(code, id, new EventRequest(Event.TYPE_USER_UTTERANCE, "user", Event.KIND_OBSERVATION, speech),
                OutputProfile.FULL_PLAN);
    }
    private void group(String code, UUID id, int count) {
        demo.acknowledge(code, id, new EventRequest(Event.TYPE_SOCIAL_GROUPING, "sensor", Event.KIND_OBSERVATION,
                "{\"humanCount\":" + count + ",\"ts\":\"" + Instant.now() + "\"}"), OutputProfile.FULL_PLAN);
    }
    private String answer(String operation, String speech) {
        return "{\"operation\":\"" + operation
                + "\",\"gesture\":\"NONE\",\"expression\":\"attentive\",\"gaze\":\"toward_user\"}";
    }
}
