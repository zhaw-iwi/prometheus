package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ch.zhaw.prometheus.agentdefs.usecases.GalaDinnerReception;
import ch.zhaw.prometheus.agentdefs.usecases.GalaDinnerReceptionRpsPreparation;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.model.AgentEmbodiment;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.OutputProfile;
import ch.zhaw.prometheus.model.policy.ExternalSpeech;
import ch.zhaw.prometheus.model.task.TaskMemory;
import ch.zhaw.prometheus.model.task.TaskPolicy;
import ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerTaskPolicy;
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
    @Autowired LiveTranscriptIngressService ingress;
    @Autowired LiveSpeechCueService speechCues;

    @Test void receptionWeatherAndHandOccasionsSurviveReloadWithoutSpeechInference() {
        String code = UUID.randomUUID().toString().substring(0, 5);
        var access = admin.createAccessCode(code, true);
        admin.replaceAllowedAgentTypes(access.getId(), List.of(GalaDinnerReception.KEY));
        UUID id = demo.createAgent(code, GalaDinnerReception.KEY, AgentEmbodiment.ROBOT.name()).getID();
        group(code, id, 1);
        UUID session = UUID.randomUUID();
        var startup = contexts.claim(code, id, session).orElseThrow();
        var owner = new ExternalSpeech(session, startup.epoch());
        try {
            for (String type : List.of(Event.TYPE_WEATHER_CURRENT, Event.TYPE_HAND_SIGN)) {
                var agent = agents.findById(id).orElseThrow();
                var memory = (ch.zhaw.prometheus.model.Storage) org.springframework.test.util.ReflectionTestUtils.getField(agent, "storage");
                memory.put("gala.sensing.ambient_at", new com.google.gson.JsonPrimitive(Instant.now().minusSeconds(30).toString()));
                for (String key : List.of("gala.continuation_id", "gala.continuation_ambient"))
                    if (memory.containsKey(key)) memory.remove(key);
                agents.saveAndFlush(agent);
                String fields = Event.TYPE_WEATHER_CURRENT.equals(type)
                        ? "\"location_label\":\"Zurich\",\"condition\":\"clear\",\"temperature_c\":9"
                        : "\"source\":\"valerian.hand.camera\",\"sign\":\"paper\",\"confidence\":0.9,\"stabilityFrames\":3";
                String payload = "{" + fields + ",\"observed_at\":\"" + Instant.now().minusSeconds(1) + "\"}";
                demo.acknowledge(code, id, new EventRequest(type, "sensor", Event.KIND_OBSERVATION, payload), OutputProfile.FULL_PLAN);
                var reloaded = agents.findById(id).orElseThrow();
                assertEquals("Gala reception", reloaded.getCurrentState().getName());
                assertFalse(reloaded.getStorage().containsKey("gala.sign"));
                var occasion = contexts.refresh(id, owner).orElseThrow().context().activity().continuation();
                assertNotNull(occasion); assertTrue(occasion.interruptible());
                assertEquals(ch.zhaw.prometheus.model.policy.LiveActivityContext.Occasion.PROACTIVE, occasion.occasion());
                assertTrue(occasion.facts().contains(Event.TYPE_WEATHER_CURRENT.equals(type) ? "Zurich" : "paper"));
                demo.acknowledge(code, id, new EventRequest(type, "sensor", Event.KIND_OBSERVATION, payload), OutputProfile.FULL_PLAN);
                assertEquals(occasion.id(), contexts.refresh(id, owner).orElseThrow().context().activity().continuation().id());
            }
            verifyNoInteractions(language);
        } finally { ownership.release(id, session); }
    }

    @Test void completedCurrentQuestionAllowsOkayAfterAnOldAnnouncementAndAdmitsOnlyOnce() {
        String code = UUID.randomUUID().toString().substring(0, 5);
        var access = admin.createAccessCode(code, true);
        admin.replaceAllowedAgentTypes(access.getId(), List.of(GalaDinnerReception.KEY));
        UUID id = demo.createAgent(code, GalaDinnerReception.KEY, AgentEmbodiment.ROBOT.name()).getID();
        group(code, id, 1);
        UUID session = UUID.randomUUID();
        var startup = contexts.claim(code, id, session).orElseThrow();
        var owner = new ExternalSpeech(session, startup.epoch());
        try {
            var agent = agents.findById(id).orElseThrow();
            var path = agent.getCurrentState().getActiveStatePath();
            var old = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                    "{\"speech\":\"Die vorige Runde ist beendet.\"}");
            old.speechProvenance(ch.zhaw.prometheus.model.event.SpeechProvenance.intent(owner));
            agent.getEventHistory().appendEvent(old); agents.saveAndFlush(agent);
            long received = System.currentTimeMillis() + 1000; // controlled relative receipt times
            var spoken = new ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.Fragment("question",
                    ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.Speaker.ASSISTANT,
                    0L, 500L, received, 1, "Soll ich kurz das SIRA Lab vorstellen?");
            assertTrue(ingress.receipt(id, owner, spoken));
            var nativeSegment = new ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.Segment(UUID.randomUUID(),
                    spoken.speaker(), List.of(spoken), ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.Closure.COMPLETE,
                    "observed_silence", received, received);
            assertEquals("COMPLETE", ingress.commit(id, owner, nativeSegment, path).orElseThrow().status());
            var input = new ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.Fragment("okay",
                    ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.Speaker.USER,
                    600L, 900L, received + 1000, 2, "Okay");
            assertTrue(ingress.receipt(id, owner, input));
            var reply = new ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.Segment(UUID.randomUUID(),
                    input.speaker(), List.of(input), ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.Closure.COMPLETE,
                    "observed_silence", input.receivedMs(), input.receivedMs());
            when(language.infer(any())).thenReturn(answer("WAIT", ""));
            var accepted = ingress.commit(id, owner, reply, path).orElseThrow();
            assertEquals("COMPLETE", accepted.status()); assertNotNull(accepted.eventId());
            assertEquals(accepted.eventId(), ingress.commit(id, owner, reply, path).orElseThrow().eventId());
            verify(language, times(1)).infer(any());
            assertEquals(1, agents.findById(id).orElseThrow().getEventHistory().toList().stream()
                    .filter(event -> Event.TYPE_USER_UTTERANCE.equals(event.getType()) && "Okay".equals(event.getPayload())).count());
        } finally { ownership.release(id, session); }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void coCreatedProposalAndActivationReloadAndClosureOrResponseDeadlineDiscardsTheAgreement(boolean timeout) {
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
                    {"operation":"PROPOSE","task":{"goal":"Auf Papier mit einem Witz reagieren","maxActions":1,"firstAction":"ON_CUE",
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
            assertEquals("ON_CUE", loaded.getStorage().get(TaskMemory.SPEC).getAsJsonObject().get("firstAction").getAsString());
            assertEquals(0, loaded.getStorage().get(TaskMemory.ACTIONS).getAsInt());
            assertTrue(((GalaDinnerTaskPolicy) loaded.getCurrentState().ownPolicy()).liveActivityContext().facts().contains("Agreed goal: Auf Papier"));
            clearInvocations(language);
            if (timeout) {
                var storage = ((TaskPolicy) loaded.getCurrentState().ownPolicy()).storage();
                var pending = storage.get(ch.zhaw.prometheus.model.task.TaskResponse.KEY).getAsJsonObject().deepCopy();
                pending.addProperty("deadline", Instant.now().minusSeconds(1).toString());
                storage.put(ch.zhaw.prometheus.model.task.TaskResponse.KEY, pending); agents.saveAndFlush(loaded);
                var ended = contexts.refresh(id, owner).orElseThrow().context().activity();
                assertTrue(ended.facts().contains("could not be confirmed"));
                assertEquals(ch.zhaw.prometheus.model.policy.LiveActivityContext.Occasion.PROACTIVE, ended.continuation().occasion());
                assertEquals(ended.continuation().id(), contexts.refresh(id, owner).orElseThrow().context().activity().continuation().id());
            } else {
                ownership.pauseInput(id, session);
                taskLifecycle.closed(id, owner.epoch(), session);
                contexts.closed(id, owner);
            }
            loaded = agents.findById(id).orElseThrow();
            assertEquals("Gala reception", loaded.getCurrentState().getName());
            assertTrue(loaded.getStorage().keySet().stream().noneMatch(key -> key.startsWith("task.")));
            verifyNoInteractions(language);
        } finally { ownership.release(id, session); }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void scopedCatalogCreationGamesReloadAndResetPreserveTheEventDefinition(boolean prepareRps) {
        String definitionKey = prepareRps ? GalaDinnerReceptionRpsPreparation.KEY : GalaDinnerReception.KEY;
        String code = UUID.randomUUID().toString().substring(0, 5);
        var access = admin.createAccessCode(code, true);
        admin.replaceAllowedAgentTypes(access.getId(), List.of(definitionKey));
        assertTrue(demo.listAgentTypes(code).stream().anyMatch(type -> definitionKey.equals(type.getKey())));
        var created = demo.createAgent(code, definitionKey, AgentEmbodiment.ROBOT.name());
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
        assertEquals(prepareRps ? "Gala rock-scissor-paper preparation" : "Gala rock-scissor-paper",
                agents.findById(id).orElseThrow().getCurrentState().getName());
        String preparationCapture = null;
        if (prepareRps) {
            preparationCapture = agents.findById(id).orElseThrow().getStorage().get("gala.rps_capture_id").getAsString();
            clearInvocations(language);
            int recognised = 0;
            for (String sign : List.of("paper", "scissor", "rock")) {
                demo.acknowledge(code, id, new EventRequest(Event.TYPE_HAND_SIGN, "sensor", Event.KIND_OBSERVATION,
                        "{\"sign\":\"" + sign + "\",\"confidence\":0.99,\"captureId\":\"" + preparationCapture
                                + "\",\"ts\":\"" + Instant.now() + "\"}"), OutputProfile.FULL_PLAN);
                reloaded = agents.findById(id).orElseThrow();
                if (++recognised < 3) {
                    assertEquals("Gala rock-scissor-paper preparation", reloaded.getCurrentState().getName());
                    assertEquals(recognised, reloaded.getStorage().get("gala.rps_recognised_signs").getAsJsonArray().size());
                    assertFalse(reloaded.getStorage().containsKey("gala.sign"));
                }
            }
            verifyNoInteractions(language);
            reloaded = agents.findById(id).orElseThrow();
            assertEquals("Gala rock-scissor-paper countdown", reloaded.getCurrentState().getName());
            completeCountdown(code, id);
        }
        reloaded = agents.findById(id).orElseThrow();
        assertEquals("Gala rock-scissor-paper", reloaded.getCurrentState().getName());
        String captureId = reloaded.getStorage().get("gala.rps_capture_id").getAsString();
        if (prepareRps) assertNotEquals(preparationCapture, captureId);
        demo.acknowledge(code, id, new EventRequest(Event.TYPE_HAND_SIGN, "sensor", Event.KIND_OBSERVATION,
                "{\"sign\":\"paper\",\"confidence\":0.99,\"captureId\":\"" + captureId + "\",\"ts\":\"" + Instant.now() + "\"}"), OutputProfile.FULL_PLAN);
        reloaded = agents.findById(id).orElseThrow();
        assertEquals("Gala reception", reloaded.getCurrentState().getName());
        assertFalse(reloaded.getStorage().containsKey("gala.sign"));
        assertTrue(reloaded.getEventHistory().toList().stream().anyMatch(event ->
                Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(event.getType()) && event.getPayload().contains("handSign")));

        say(code, id, "Vorheriger Besucher"); group(code, id, 0);
        assertEquals(prepareRps ? "Gala rock-scissor-paper countdown" : "Gala rock-scissor-paper", agents.findById(id).orElseThrow().getCurrentState().getName());
        if (prepareRps) assertEquals("true", agents.findById(id).orElseThrow().getStorage().get("gala.rps_prepared").getAsString());
        contexts.closed(id, new ch.zhaw.prometheus.model.policy.ExternalSpeech(session, startup.epoch()));
        assertEquals("Gala reception", agents.findById(id).orElseThrow().getCurrentState().getName());
        ownership.release(id, session); group(code, id, 1);
        UUID nextSession = UUID.randomUUID();
        var next = contexts.claim(code, id, nextSession).orElseThrow();
        assertFalse(next.startupInput().toString().contains("Vorheriger Besucher"));
        assertFalse(agents.findById(id).orElseThrow().getStorage().containsKey("gala.rps_prepared"));
        when(language.infer(any())).thenReturn(answer("START_RPS", ""));
        say(code, id, "Schere Stein Papier bitte");
        assertEquals(prepareRps ? "Gala rock-scissor-paper preparation" : "Gala rock-scissor-paper",
                agents.findById(id).orElseThrow().getCurrentState().getName());
        say(code, id, "Stopp");
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
    private void completeCountdown(String code, UUID id) {
        var agent = agents.findById(id).orElseThrow(); var memory = agent.getStorage();
        var owner = ownership.current(agent);
        String receipt = "count-" + UUID.randomUUID();
        assertTrue(ingress.receipt(id, owner, new ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.Fragment(receipt,
                ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.Speaker.ASSISTANT, 10000L, 12000L, Instant.now().toEpochMilli(), 1, "Schere, Stein, Papier!")));
        assertTrue(speechCues.accept(id, owner, new LiveSpeechCueService.Request(UUID.fromString(memory.get("gala.countdown_id").getAsString()),
                UUID.fromString(memory.get("gala.round_id").getAsString()), "complete", List.of(receipt))));
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
