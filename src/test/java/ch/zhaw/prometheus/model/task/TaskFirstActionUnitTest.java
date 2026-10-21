package ch.zhaw.prometheus.model.task;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.Instant;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.agentdefs.core.GenericMultimodalBehaviour;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

class TaskFirstActionUnitTest {
    @Test void extendingApplicationSchemaPreservesTheLegacyDefaultPromptByteForByte() throws Exception {
        var bytes = GenericMultimodalBehaviour.builtInPrompts().get("configure").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals("1fdf7e9f17da9b1cc1c5c5b43692e84c5d97c103f46ecfb1d37968c3de1d30c8",
                java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
    }
    private static final String SPEC = """
            {"goal":"Say hello on paper","maxActions":1,"sessionBound":true,"firstAction":"ON_CUE","rules":[
            {"eventType":"obs.hand.sign","field":"sign","operator":"eq","value":"paper","action":"Say hello",
             "effect":"ACT","minConfidence":0.8,"samples":1,"cooldownSeconds":3}]}
            """;
    private final LanguageModelGateway gateway = mock(LanguageModelGateway.class);
    private final Agent agent = new GenericMultimodalBehaviour().createAgent();
    private final PolicyRuntime runtime = new PolicyRuntime(new PromptMessageAssembler(), gateway);
    private Storage storage() { return ((TaskPolicy) agent.getCurrentState().ownPolicy()).storage(); }
    private Event say(String text) { return agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", text), runtime); }
    private Event hand(String sign, Instant time) { return agent.acknowledge(Event.observation(Event.TYPE_HAND_SIGN, "sensor",
            "{\"sign\":\"" + sign + "\",\"confidence\":0.99,\"ts\":\"" + time + "\"}"), runtime); }

    @Test void legacyJsonKeepsImmediateMeaningWhileFutureTimingRoundTripsAndRejectsUnknownModes() {
        var raw = JsonParser.parseString(SPEC).getAsJsonObject(); raw.remove("firstAction");
        var legacy = TaskSpec.parse(raw).executable();
        assertEquals(TaskSpec.FirstAction.IMMEDIATE, legacy.firstAction());
        assertEquals(raw, legacy.json());
        assertEquals(legacy, TaskSpec.parse(legacy.json()));
        var future = TaskSpec.parse(JsonParser.parseString(SPEC)).executable();
        assertEquals(TaskSpec.FirstAction.ON_CUE, future.firstAction());
        assertEquals(future, TaskSpec.parse(future.json()));
        for (String value : java.util.List.of("\"LATER\"", "null", "true", "1")) {
            raw.add("firstAction", JsonParser.parseString(value));
            assertThrows(IllegalArgumentException.class, () -> TaskSpec.parse(raw));
        }
        assertThrows(IllegalArgumentException.class, () -> TaskSpec.parse(JsonParser.parseString(
                SPEC.replace("\"effect\":\"ACT\"", "\"effect\":\"COMPLETE\""))).executable());
    }

    @Test void futureActivationArmsWithoutSpeechActionOrMotionAndOneFreshCueCompletesOnce() {
        var activate = JsonParser.parseString(GenericMultimodalTaskUnitTest.update("ACTIVATE", SPEC, "PREMATURE ACTION")).getAsJsonObject();
        activate.getAsJsonObject("reply").add("motion", JsonParser.parseString("{\"handSign\":\"rock\"}"));
        when(gateway.infer(any())).thenReturn(activate.toString());
        var reply = BehaviourPlan.fromJson(say("Activate our future reaction").getPayload());
        assertFalse(reply.getSpeech().contains("PREMATURE")); assertNull(reply.getMotion());
        assertEquals(0, storage().get(TaskMemory.ACTIONS).getAsInt());
        assertEquals("RUNNING", TaskMemory.phase(storage()));
        var after = Instant.now().minusSeconds(1); TaskMemory.put(storage(), TaskMemory.AFTER, after.toString());
        clearInvocations(gateway);
        assertNull(hand("paper", after.minusMillis(1))); assertNull(hand("rock", Instant.now()));
        verifyNoInteractions(gateway);
        when(gateway.infer(any())).thenReturn("{\"speech\":\"Hello\"}");
        var time = Instant.now(); assertNotNull(hand("paper", time));
        assertEquals(1, storage().get(TaskMemory.ACTIONS).getAsInt());
        assertEquals("COMPLETED", TaskMemory.phase(storage()));
        assertNull(hand("paper", time)); assertNull(hand("paper", Instant.now()));
        verify(gateway, times(1)).infer(any());
    }

    @Test void defaultActivationStillPerformsItsFirstActionAndInvalidReplacementKeepsIt() {
        String legacy = SPEC.replace(",\"firstAction\":\"ON_CUE\"", "");
        when(gateway.infer(any())).thenReturn(GenericMultimodalTaskUnitTest.update("ACTIVATE", legacy, "Hello now"));
        assertEquals("Hello now", BehaviourPlan.fromJson(say("Start now").getPayload()).getSpeech());
        assertEquals(1, storage().get(TaskMemory.ACTIONS).getAsInt());
        var before = storage().get(TaskMemory.SPEC).deepCopy();
        when(gateway.infer(any())).thenReturn(GenericMultimodalTaskUnitTest.update("PROPOSE",
                SPEC.replace("ON_CUE", "TOMORROW"), "REJECTED"));
        assertFalse(BehaviourPlan.fromJson(say("Revise").getPayload()).getSpeech().contains("REJECTED"));
        assertEquals(before, storage().get(TaskMemory.SPEC));
        assertEquals(1, storage().get(TaskMemory.ACTIONS).getAsInt());
    }
}
