package ch.zhaw.prometheus.agentdefs.usecases.gala;

import static ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerMemory.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import ch.zhaw.prometheus.agentdefs.AgentCreationContext;
import ch.zhaw.prometheus.agentdefs.usecases.GalaDinnerReception;
import ch.zhaw.prometheus.agentdefs.usecases.GalaDinnerReceptionSingleState;
import ch.zhaw.prometheus.application.live.LiveContextProjection;
import ch.zhaw.prometheus.application.live.LiveVoicePolicyAdapter;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.AgentEmbodiment;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.ExternalSpeech;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.spi.InferenceRequest;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

class GalaDinnerReceptionSingleStateUnitTest {
    private final LanguageModelGateway gateway = mock(LanguageModelGateway.class);
    private final PolicyRuntime runtime = new PolicyRuntime(new PromptMessageAssembler(), gateway)
            .withExternalSpeech(new ExternalSpeech(UUID.randomUUID(), UUID.randomUUID()));
    private final GalaDinnerReceptionSingleState definition = new GalaDinnerReceptionSingleState();
    private final Agent agent = definition.createAgent();
    private final Storage memory = ((GalaDinnerPolicy) agent.getCurrentState().ownPolicy()).storage();

    @BeforeEach void occupied() { put(memory, COUNT, "1"); put(memory, SEEN, Instant.now().toString()); }

    @Test void oneStateSharesIdentityKnowledgeSensingAndLiveLifecycleForBothEmbodiments() throws Exception {
        var full = new GalaDinnerReception().createAgent();
        assertEquals(5, full.reachableStates().size());
        try (var reader = new InputStreamReader(getClass().getResourceAsStream(GalaDinnerReference.RESOURCE), StandardCharsets.UTF_8)) {
            var reference = new Gson().fromJson(reader, GalaDinnerReference.Document.class);
            for (var embodiment : AgentEmbodiment.values()) {
                var created = definition.createInstance(new AgentCreationContext(new PromptMessageAssembler(), gateway, embodiment)).agent();
                assertEquals(1, created.reachableStates().size());
                assertEquals("Gala reception", created.getCurrentState().getName());
                assertInstanceOf(GalaDinnerReceptionOnlyPolicy.class, created.getCurrentState().ownPolicy());
                assertEquals(full.getPersonaName(), created.getPersonaName());
                assertEquals(full.getLanguageCode(), created.getLanguageCode());
                assertEquals(embodiment, created.getEmbodiment());
                assertEquals(full.getInteractionProfile().getSupportedObservations(), created.getInteractionProfile().getSupportedObservations());
                assertTrue(created.getInteractionProfile().isPresenceControlledLive());
                assertTrue(created.getInteractionProfile().isCapabilityAwareness());
                var adapter = new LiveVoicePolicyAdapter();
                assertTrue(adapter.supports(created));
                String instructions = adapter.instructions(created);
                for (var section : reference.sections()) for (String fact : section.facts())
                    assertTrue(instructions.contains(fact), section.id() + ": " + fact);
                assertTrue(instructions.contains("dry robot self-irony recur naturally in replies"));
                assertTrue(instructions.contains("Games and co-created sensor tasks are unavailable"));
                assertFalse(instructions.contains("Unspecified play needs a choice"));
                assertFalse(instructions.contains("Offer conversation, information, games"));
                assertFalse(instructions.contains("Identity is SECRET"));
                assertFalse(instructions.contains("https://"));
                assertEquals(1, instructions.split("GALA REFERENZ", -1).length - 1);
                int bytes = instructions.getBytes(StandardCharsets.UTF_8).length;
                assertTrue(bytes <= 14400, Integer.toString(bytes));
                System.out.println("Reception-only Live instructions (" + embodiment + "): " + bytes + " bytes");
                var projection = new LiveContextProjection(new PromptMessageAssembler(), adapter).project(created);
                assertEquals(2000, projection.userSilenceMs());
                assertTrue(projection.filterIsolatedInputActivity());
                assertTrue(projection.startupInput().toString().contains("ich bin djee-djee"));
                assertTrue(projection.activity().facts().contains("Games and co-created tasks are unavailable"));
                assertNull(created.generate(runtime));
            }
        }
        verifyNoInteractions(gateway);
    }

    @ParameterizedTest
    @ValueSource(strings = {"START_RPS", "START_GUESS", "START_TASK", "CHOOSE_GAME", "CHECK_EXIT", "EXIT_GAME", "KEEP_GAME", "GIVE_UP", "PROPOSE", "not-json"})
    void forbiddenOperationsAndMalformedOutputCannotCreateActivitiesOrCompetingSpeech(String selected) {
        when(gateway.infer(any())).thenReturn(selected.equals("not-json") ? selected : control(selected, "EXPLAIN"));
        assertNull(say("Spielen wir Schere Stein Papier; danach will ich eine Aufgabe konfigurieren."));
        assertReceptionOnly();
        assertFalse(memory.containsKey(CONTINUATION_ID));
        assertNull(agent.generate(runtime));
        var request = ArgumentCaptor.forClass(InferenceRequest.class);
        verify(gateway).infer(request.capture());
        String prompt = request.getValue().messages().getFirst().getContent();
        assertTrue(prompt.contains("CHAT|WAIT|CLARIFY|RETURN|QUIET|CORRECT_FACE"));
        assertFalse(prompt.contains("START_RPS"));
        // A bad optional control result does not poison the following conversation.
        when(gateway.infer(any())).thenReturn(control("CHAT", "EXPLAIN"));
        var plan = BehaviourPlan.fromJson(say("Wer hat dich entwickelt?").getPayload());
        assertNull(plan.getSpeech());
        assertNull(plan.getMotion());
        assertNull(plan.getDisplay());
        assertReceptionOnly();
    }

    @Test void ordinaryRepliesKeepLiveWordingAndTheExistingGestureSpacingAndRepeatRules() {
        when(gateway.infer(any())).thenReturn(control("CHAT", "EXPLAIN"));
        var first = BehaviourPlan.fromJson(say("Was macht das SIRA Lab?").getPayload());
        var second = BehaviourPlan.fromJson(say("Und das Institut?").getPayload());
        var explicit = BehaviourPlan.fromJson(say("Please show that gesture again").getPayload());
        assertEquals("EXPLAIN", first.getNonVerbal().getAsJsonObject().get("gesture").getAsString());
        assertEquals("NONE", second.getNonVerbal().getAsJsonObject().get("gesture").getAsString());
        assertEquals("EXPLAIN", explicit.getNonVerbal().getAsJsonObject().get("gesture").getAsString());
        for (var plan : List.of(first, second, explicit)) {
            assertNull(plan.getSpeech()); assertNull(plan.getMotion()); assertNull(plan.getDisplay());
        }
        assertNull(agent.generate(runtime));
        verify(gateway, times(3)).infer(any());
        assertReceptionOnly();
    }

    @Test void sharedSensoryTransitionsAdmitBoundedProactivityWithoutOpeningActivities() {
        Instant now = Instant.now();
        GalaDinnerSensing.begin(memory, now.minusSeconds(40));
        put(memory, SEEN, now.minusSeconds(5).toString());
        observe(Event.TYPE_SOCIAL_GROUPING, "{\"humanCount\":2}", now.minusSeconds(3));
        observe(Event.TYPE_SOCIAL_GROUPING, "{\"humanCount\":2}", now);
        assertOccasion("Visible group count changed from 1 to 2");
        readyForAmbient();
        put(memory, GalaDinnerSensing.FACE_BASE, "neutral");
        put(memory, GalaDinnerSensing.FACE_CANDIDATE, "happy");
        put(memory, GalaDinnerSensing.FACE_SINCE, now.minusSeconds(3).toString());
        put(memory, FACE_AT, now.minusSeconds(4).toString());
        var face = observe(Event.TYPE_FACE_EMOTION, "{\"emotion\":\"happy\",\"confidence\":0.95,\"valence\":0.8}", now);
        assertEquals("gentleSmile", BehaviourPlan.fromJson(face.getPayload()).getNonVerbal().getAsJsonObject().getAsJsonObject("facialExpression").get("type").getAsString());
        assertOccasion("happy");
        readyForAmbient();
        observe(Event.TYPE_WEATHER_CURRENT, "{\"location_label\":\"Winterthur\",\"condition\":\"rain\",\"temperature_c\":9}", now);
        assertOccasion("Winterthur");
        readyForAmbient();
        observe(Event.TYPE_HAND_SIGN, "{\"sign\":\"paper\",\"confidence\":0.95,\"source\":\"valerian.hand.camera\",\"stabilityFrames\":3}", now);
        assertOccasion("no game invitation/start/result");
        readyForAmbient();
        observe(Event.TYPE_HAND_SIGN, "{\"sign\":\"paper\",\"confidence\":0.95,\"source\":\"valerian.hand.camera\",\"stabilityFrames\":3}", now.plusMillis(1));
        assertFalse(memory.containsKey(CONTINUATION_ID), "A held sign must not repeat the comment");
        assertNull(agent.generate(runtime));
        verifyNoInteractions(gateway);
        assertReceptionOnly();
    }

    @Test void silenceDepartureAndResetPreserveTheRestrictedPolicyAndClearEncounterMemory() {
        when(gateway.infer(any())).thenReturn(control("CORRECT_FACE", "NONE"));
        assertNull(say("Ich bin eigentlich angespannt."));
        assertEquals("true", text(memory, GalaDinnerSensing.CORRECTED, ""));
        assertNull(say("Bitte sei still"));
        assertEquals("true", text(memory, QUIET, ""));
        readyForAmbient();
        observe(Event.TYPE_WEATHER_CURRENT, "{\"location_label\":\"Winterthur\",\"condition\":\"rain\",\"temperature_c\":9}", Instant.now());
        assertFalse(memory.containsKey(CONTINUATION_ID));
        observe(Event.TYPE_SOCIAL_GROUPING, "{\"humanCount\":0}", Instant.now());
        assertTrue(memory.containsKey(ABSENT_SINCE));
        agent.acknowledge(new Event(GalaDinnerPolicy.LIVE_ENDED, "system", Event.KIND_SYSTEM, "departure"), runtime);
        assertFalse(memory.containsKey(GalaDinnerSensing.CORRECTED));
        put(memory, "unrelated", "keep");
        agent.reset();
        assertEquals("keep", text(memory, "unrelated", ""));
        assertTrue(memory.toMap().keySet().stream().noneMatch(key -> key.startsWith(PREFIX)));
        assertInstanceOf(GalaDinnerReceptionOnlyPolicy.class, agent.getCurrentState().ownPolicy());
        occupied();
        when(gateway.infer(any())).thenReturn(control("START_GUESS", "NONE"));
        assertNull(say("Personenraten bitte"));
        assertReceptionOnly();
    }

    private void assertReceptionOnly() {
        assertEquals(1, agent.reachableStates().size());
        assertEquals("Gala reception", agent.getCurrentState().getName());
        assertEquals("RECEPTION", mode(memory));
        for (String key : List.of(PERSON, SIGN, CAPTURE_ID, EXIT_REQUEST)) assertFalse(memory.containsKey(key), key);
        assertTrue(memory.toMap().keySet().stream().noneMatch(key -> key.startsWith("task.")));
    }
    private void readyForAmbient() {
        GalaDinnerSensing.cancelAmbient(memory);
        put(memory, GalaDinnerSensing.AMBIENT_AT, Instant.now().minusSeconds(40).toString());
    }
    private void assertOccasion(String fact) {
        assertEquals("PROACTIVE", text(memory, CONTINUATION_OCCASION, ""));
        assertTrue(text(memory, CONTINUATION_FACTS, "").contains(fact), text(memory, CONTINUATION_FACTS, ""));
    }
    private Event observe(String type, String payload, Instant observed) {
        var data = com.google.gson.JsonParser.parseString(payload).getAsJsonObject();
        data.addProperty("observed_at", observed.toString());
        data.addProperty("ts", observed.toString());
        return agent.acknowledge(Event.observation(type, "sensor", data.toString()), runtime);
    }
    private Event say(String text) {
        return agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, text), runtime);
    }
    private static String control(String operation, String gesture) {
        var result = new JsonObject();
        result.addProperty("operation", operation); result.addProperty("gesture", gesture);
        result.addProperty("expression", "attentive"); result.addProperty("gaze", "toward_user");
        return result.toString();
    }
}
