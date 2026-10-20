package ch.zhaw.prometheus.agentdefs.usecases.gala;

import static ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerMemory.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import ch.zhaw.prometheus.agentdefs.usecases.GalaDinnerReception;
import ch.zhaw.prometheus.application.live.LiveVoicePolicyAdapter;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.SpeechProvenance;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.task.*;
import ch.zhaw.prometheus.spi.InferenceRequest;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

class GalaDinnerTaskUnitTest {
    private static final String SPEC = """
            {"goal":"Eine kurze Reaktion auf Papier zeigen","maxActions":2,"sessionBound":true,"rules":[
            {"eventType":"obs.hand.sign","field":"sign","operator":"eq","value":"paper",
             "action":"Sage einen kurzen Roboterwitz","effect":"ACT","minConfidence":0.8,"samples":1,"cooldownSeconds":3}]}
            """;
    private static final String OPENING = "Die Aufgabe ist aktiv. Ich warte auf Ihr Papierzeichen.";
    private final LanguageModelGateway gateway = mock(LanguageModelGateway.class);
    private final Agent agent = new GalaDinnerReception().createAgent();
    private final Storage memory = ((GalaDinnerPolicy) agent.getCurrentState().ownPolicy()).storage();
    private final ExternalSpeech owner = new ExternalSpeech(UUID.randomUUID(), agent.executionEpoch());
    private final PolicyRuntime runtime = new PolicyRuntime(new PromptMessageAssembler(), gateway).withExternalSpeech(owner);

    @BeforeEach void occupied() { put(memory, COUNT, "1"); put(memory, SEEN, Instant.now().toString()); }

    @Test void proposalActivationCaptureFreshCueAndCompletionUseTheGenericContract() {
        propose(SPEC);
        assertEquals("Gala co-created task", agent.getCurrentState().getName());
        assertInstanceOf(TaskPolicy.class, agent.getCurrentState().ownPolicy());
        assertTrue(TaskCueStatus.of(agent, Instant.now()).draft());
        assertFalse(memory.containsKey(TaskMemory.SPEC));
        clearInvocations(gateway);
        assertNull(hand("paper", .99, Instant.now())); verifyNoInteractions(gateway);
        String instructions = new LiveVoicePolicyAdapter().instructions(agent);
        assertTrue(instructions.contains("Proposed goal: Eine kurze Reaktion"));
        assertTrue(instructions.contains("German"));
        assertTrue(instructions.contains("deliver the backend's spoken response faithfully"));
        activate();
        assertEquals("RUNNING", TaskMemory.phase(memory));
        assertEquals(owner.sessionId().toString(), TaskMemory.text(memory, TaskMemory.SESSION, ""));
        clearInvocations(gateway);
        assertNull(hand("paper", .99, Instant.now()));
        arm();
        assertNull(hand("paper", .99, Instant.now()), "An uncaptured announcement still blocks cues");
        capture(OPENING);
        assertTrue(TaskCueStatus.of(agent, Instant.now()).responseCaptured());
        assertNull(hand("paper", .99, Instant.now().minusSeconds(20)));
        assertNull(hand("paper", .1, Instant.now()));
        assertNull(hand("rock", .99, Instant.now()));
        verifyNoInteractions(gateway);
        when(gateway.infer(any())).thenReturn("{\"speech\":\"Mein Humor hat heute keine Ladepause.\"}");
        Event result = hand("paper", .99, Instant.now());
        assertTrue(BehaviourPlan.fromJson(result.getPayload()).getSpeech().endsWith("Aufgabe abgeschlossen."));
        assertHome();
        assertNull(hand("paper", .99, Instant.now()));
        verify(gateway).infer(any());
    }

    @Test void activationWithoutProposalAndInvalidOrAlteredPlansCannotPublishActivationClaims() {
        when(gateway.infer(any())).thenReturn(GalaDinnerReceptionUnitTest.answer("START_TASK", ""),
                update("ACTIVATE", SPEC, "UNAUTHORIZED"));
        assertFalse(speech(say("Starte direkt")).contains("UNAUTHORIZED"));
        assertFalse(memory.containsKey(TaskMemory.SPEC));
        for (String invalid : List.of(SPEC.replace("\"sessionBound\":true", "\"sessionBound\":false"),
                SPEC.replace("\"maxActions\":2", "\"maxActions\":6"),
                SPEC.replace("\"field\":\"sign\"", "\"field\":\"identity\""))) {
            when(gateway.infer(any())).thenReturn(update("PROPOSE", invalid, "UNAUTHORIZED"));
            assertFalse(speech(say("Ändere den Vorschlag")).contains("UNAUTHORIZED"));
            assertFalse(memory.containsKey(TaskMemory.DRAFT));
        }
        when(gateway.infer(any())).thenReturn(update("PROPOSE", SPEC, "Vorschlag, noch nicht aktiv."));
        say("Bitte einen gültigen Vorschlag");
        var draft = memory.get(TaskMemory.DRAFT).deepCopy();
        when(gateway.infer(any())).thenReturn(update("ACTIVATE", SPEC.replace("\"maxActions\":2", "\"maxActions\":3"), "UNAUTHORIZED"));
        assertFalse(speech(say("Ja")).contains("UNAUTHORIZED"));
        assertEquals(draft, memory.get(TaskMemory.DRAFT));
        assertFalse(memory.containsKey(TaskMemory.SPEC));
    }

    @ParameterizedTest
    @ValueSource(strings = {"departure", "presence", "close", "stop", "pause", "timeout", "reset"})
    void encounterEndClearsDraftAndActiveTask(String ending) {
        for (boolean active : List.of(false, true)) {
            occupied(); propose(SPEC);
            if (active) activate();
            put(memory, "unrelated", "keep");
            clearInvocations(gateway);
            switch (ending) {
                case "departure" -> agent.acknowledge(Event.observation(Event.TYPE_SOCIAL_GROUPING, "sensor",
                        "{\"humanCount\":0,\"ts\":\"" + Instant.now().plusMillis(1) + "\"}"), runtime);
                case "presence" -> agent.acknowledge(Event.observation(Event.TYPE_HUMAN_PRESENCE, "sensor",
                        "{\"humanCount\":0,\"avgDetectionConfidence\":0,\"ts\":\"" + Instant.now().plusMillis(1) + "\"}"), runtime);
                case "close" -> agent.acknowledge(new Event(GalaDinnerPolicy.LIVE_ENDED, "system", Event.KIND_SYSTEM, "{}"), runtime);
                case "stop" -> say("Stopp");
                case "pause" -> say("Pause");
                case "timeout" -> { put(memory, LAST_INPUT, Instant.now().minusSeconds(91).toString()); agent.tick(runtime); }
                case "reset" -> agent.reset();
                default -> fail();
            }
            assertHome();
            assertEquals("keep", text(memory, "unrelated", ""));
            verifyNoInteractions(gateway);
        }
    }

    @Test void offTopicQuestionReturnsHomeWithoutCompetingSpeechOrASecondTaskInference() {
        propose(SPEC); activate();
        when(gateway.infer(any())).thenReturn("{\"operation\":\"STOP\",\"task\":null,\"reply\":{}}");
        clearInvocations(gateway);
        assertNull(say("Wann gibt es Dessert?"));
        assertHome();
        verify(gateway).infer(any());
    }

    @Test void taskFaceRulesReplaceAmbientReactionsAndWaitingIsSilent() {
        String faceTask = SPEC.replace("\"maxActions\":2", "\"maxActions\":4")
                .replace("obs.hand.sign", Event.TYPE_FACE_EMOTION).replace("\"field\":\"sign\"", "\"field\":\"emotion\"")
                .replace("\"paper\"", "\"happy\"");
        JsonObject task = JsonParser.parseString(faceTask).getAsJsonObject();
        JsonObject wait = task.getAsJsonArray("rules").get(0).getAsJsonObject().deepCopy();
        wait.addProperty("value", "neutral"); wait.addProperty("effect", "WAIT");
        task.getAsJsonArray("rules").add(wait);
        propose(task.toString()); activate(); capture(OPENING); arm(); clearInvocations(gateway);
        assertNull(face("sad"));
        assertNull(face("neutral"));
        assertEquals("WAITING", TaskMemory.phase(memory));
        assertNull(face("neutral")); verifyNoInteractions(gateway);
        when(gateway.infer(any())).thenReturn("{\"speech\":\"Ein kleiner Roboterwitz.\"}");
        Event response = face("happy");
        assertEquals("Ein kleiner Roboterwitz.", speech(response));
        assertEquals("RUNNING", TaskMemory.phase(memory));
        assertEquals(2, memory.get(TaskMemory.ACTIONS).getAsInt());
        assertFalse(memory.containsKey(FACE), "Ambient expression tracking must not override the task");
        assertNull(face("happy")); verify(gateway).infer(any());
    }

    @Test void lateActivationAndCueResultsAreDiscardedOnVoiceClosure() {
        propose(SPEC);
        var continuing = new AtomicBoolean(true);
        when(gateway.infer(any())).thenAnswer(call -> {
            continuing.set(false); return update("ACTIVATE", "null", OPENING);
        });
        assertNull(agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Ja"),
                runtime.withTaskContinuation(continuing::get)));
        assertHome();
        propose(SPEC); activate(); capture(OPENING); arm(); continuing.set(true);
        when(gateway.infer(any())).thenAnswer(call -> { continuing.set(false); return "{\"speech\":\"Zu spät\"}"; });
        assertNull(agent.acknowledge(Event.observation(Event.TYPE_HAND_SIGN, "sensor",
                "{\"sign\":\"paper\",\"confidence\":0.99,\"ts\":\"" + Instant.now() + "\"}"),
                runtime.withTaskContinuation(continuing::get)));
        assertHome();
    }

    @Test void germanRecoveryPreservesTheAgreementAndItsCaptureCanReleaseTheNextCue() {
        propose(SPEC); activate(); capture(OPENING); arm();
        var spec = memory.get(TaskMemory.SPEC).deepCopy();
        clearInvocations(gateway);
        assertEquals("Unsere Aufgabe ist bereits aktiv.", speech(say("Resume the task.")));
        verifyNoInteractions(gateway);
        when(gateway.infer(any())).thenThrow(new IllegalStateException("provider unavailable"));
        String recovery = speech(say("Wie war unsere Vereinbarung?"));
        assertTrue(recovery.startsWith("Das konnte ich"));
        assertEquals(spec, memory.get(TaskMemory.SPEC));
        assertFalse(TaskCueStatus.of(agent, Instant.now()).responseCaptured());
        capture(recovery);
        assertTrue(TaskCueStatus.of(agent, Instant.now()).responseCaptured());
        doReturn("{\"speech\":\"Ein letzter Witz.\"}").when(gateway).infer(any());
        assertNotNull(hand("paper", .99, Instant.now()));
        assertHome();
    }

    @Test void newTaskConfigurationExcludesPreviousVisitorsAndHasNoGenericPersona() {
        when(gateway.infer(any())).thenReturn(GalaDinnerReceptionUnitTest.answer("CHAT", ""));
        say("Besucher-Geheimnis");
        put(memory, SESSION, Integer.toString(agent.getEventHistory().toList().size()));
        clearInvocations(gateway); propose(SPEC);
        var requests = ArgumentCaptor.forClass(InferenceRequest.class);
        verify(gateway, times(2)).infer(requests.capture());
        var configuration = requests.getAllValues().getLast();
        assertFalse(configuration.messages().stream().anyMatch(message -> message.getContent().contains("Besucher-Geheimnis")));
        assertTrue(configuration.messages().getFirst().getContent().contains("You are djee-djee"));
        assertFalse(configuration.messages().getFirst().getContent().contains("You are Valerian"));
    }

    private void propose(String spec) {
        when(gateway.infer(any())).thenReturn(GalaDinnerReceptionUnitTest.answer("START_TASK", ""),
                update("PROPOSE", spec, "Ich schlage eine kurze Reaktion vor. Soll ich sie aktivieren?"));
        say("Lass uns eine Aufgabe mit meinem Handzeichen gestalten.");
    }
    private void activate() { when(gateway.infer(any())).thenReturn(update("ACTIVATE", "null", OPENING)); say("Ja, bitte aktivieren."); }
    private void arm() { put(memory, TaskMemory.AFTER, Instant.now().minusSeconds(1).toString()); }
    private void capture(String text) {
        agent.recordExternalSpeech(text, new SpeechProvenance(SpeechProvenance.Origin.NATIVE,
                owner.sessionId(), owner.epoch(), UUID.randomUUID().toString(), SpeechProvenance.Association.NONE, List.of(), true));
    }
    private void assertHome() {
        assertEquals("Gala reception", agent.getCurrentState().getName());
        assertTrue(memory.toMap().keySet().stream().noneMatch(key -> key.startsWith("task.")));
    }
    private Event say(String text) { return agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", text), runtime); }
    private Event hand(String sign, double confidence, Instant time) {
        return agent.acknowledge(Event.observation(Event.TYPE_HAND_SIGN, "sensor",
                "{\"sign\":\"" + sign + "\",\"confidence\":" + confidence + ",\"ts\":\"" + time + "\"}"), runtime);
    }
    private Event face(String emotion) {
        return agent.acknowledge(Event.observation(Event.TYPE_FACE_EMOTION, "sensor",
                "{\"emotion\":\"" + emotion + "\",\"confidence\":0.99,\"ts\":\"" + Instant.now() + "\"}"), runtime);
    }
    private static String speech(Event event) { return BehaviourPlan.fromJson(event.getPayload()).getSpeech(); }
    private static String update(String operation, String spec, String speech) {
        return "{\"operation\":\"" + operation + "\",\"task\":" + spec + ",\"reply\":"
                + BehaviourPlan.speechOnly(speech).toJson() + "}";
    }
}
