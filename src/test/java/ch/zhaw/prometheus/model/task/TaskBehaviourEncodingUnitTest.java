package ch.zhaw.prometheus.model.task;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ch.zhaw.prometheus.agentdefs.core.GenericMultimodalBehaviour;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.spi.LanguageModelGateway;
import fixtures.behaviour.TaskReplyFixture;

class TaskBehaviourEncodingUnitTest {
    @Test void compactExpansionPreservesExactSpeechEveryChannelAndProviderObject() {
        String speech = "  Grüezi! \"Ja\" — مرحباً.\nTwo  spaces.  ";
        var compact = TaskReplyFixture.plan(speech, true);
        var original = compact.deepCopy();
        assertEquals(TaskMemory.plan(TaskReplyFixture.plan(speech, false)).toJsonObject(),
                TaskMemory.plan(compact).toJsonObject());
        assertEquals(speech, TaskMemory.plan(compact).getSpeech());
        assertEquals(original, compact);
        assertFalse(TaskMemory.plan(compact).toJsonObject().has("nv"));
        assertTrue(compact.toString().length() < TaskReplyFixture.plan(speech, false).toString().length());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"speech\":\"Only speech.\"}", "{\"speech\":null}", "{\"nonVerbal\":null}",
            "{\"motion\":{\"handSign\":\"rock\"}}"})
    void optionalAndSilentCanonicalPlansRemainUnchanged(String raw) {
        assertEquals(ch.zhaw.prometheus.model.behaviour.BehaviourPlan.fromJson(raw).toJsonObject(),
                TaskMemory.plan(JsonParser.parseString(raw)).toJsonObject());
    }

    @Test void compactNonSpeechAndEmptyNonverbalDoNotInventSpeechOrGestures() {
        assertEquals(TaskReplyFixture.plan(null, false), TaskMemory.plan(TaskReplyFixture.plan(null, true)).toJsonObject());
        assertEquals(JsonParser.parseString("{\"nonVerbal\":{}}"),
                TaskMemory.plan(JsonParser.parseString("{\"nv\":{}}")).toJsonObject());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"nv\":{\"f\":[\"gentleSmile\",2]}}",
            "{\"nv\":{\"f\":[\"unapprovedExpression\",0.3]}}",
            "{\"nv\":{\"z\":[\"toward_user\",\"unapprovedFocus\"]}}",
            "{\"nv\":{\"g\":\"NONE\",\"x\":{\"gesture\":\"POLITE\"}}}",
            "{\"nv\":{\"x\":{\"posture\":{}}}}",
            "{\"nv\":{},\"nonVerbal\":{}}",
            "{\"nv\":{},\"display\":{}}",
            "{\"nv\":{},\"motion\":{\"move\":\"forward\"}}"
    })
    void compactEncodingCannotBypassTaskRestrictions(String raw) {
        assertThrows(RuntimeException.class, () -> TaskMemory.plan(JsonParser.parseString(raw)));
    }

    @Test void expandedSizeLimitStillApplies() {
        // Valid speech below 1000 characters, but escaped whitespace plus modalities exceed 1800 JSON characters.
        String speech = "x" + "\n".repeat(800);
        var compact = TaskReplyFixture.plan(speech, true);
        assertTrue(new Gson().toJson(compact).length() < 1800);
        assertTrue(new Gson().toJson(TaskReplyFixture.plan(speech, false)).length() > 1800);
        assertThrows(IllegalArgumentException.class, () -> TaskMemory.plan(compact));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void configurationAndCueUseTheSameCanonicalStateAndOutputContract(boolean compact) {
        var gateway = mock(LanguageModelGateway.class);
        var runtime = new PolicyRuntime(new PromptMessageAssembler(), gateway);
        var agent = new GenericMultimodalBehaviour().createAgent();
        var storage = ((TaskPolicy) agent.getCurrentState().ownPolicy()).storage();
        String spec = """
                {"goal":"Show paper on rock","maxActions":3,"sessionBound":true,"rules":[
                {"eventType":"obs.hand.sign","field":"sign","operator":"eq","value":"rock",
                 "action":"Show paper","effect":"ACT","minConfidence":0.8,"samples":1,"cooldownSeconds":3}]}
                """;
        when(gateway.infer(any())).thenReturn(
                TaskReplyFixture.update("PROPOSE", spec, "Here is the proposal.", compact),
                TaskReplyFixture.update("ACTIVATE", "null", "Here is paper.", compact),
                TaskReplyFixture.plan("Paper again.", compact).toString());
        for (String text : List.of("Please propose an interaction.", "Activate it.")) {
            var result = agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", text), runtime);
            String expected = text.startsWith("Please") ? "Here is the proposal." : "Here is paper.";
            assertEquals(TaskReplyFixture.plan(expected, false), JsonParser.parseString(result.getPayload()));
        }
        assertEquals("RUNNING", TaskMemory.phase(storage));
        assertEquals(TaskSpec.parse(JsonParser.parseString(spec)).json(), storage.get(TaskMemory.SPEC));
        assertFalse(storage.containsKey(TaskMemory.DRAFT));
        assertEquals(1, storage.get(TaskMemory.ACTIONS).getAsInt());
        TaskMemory.put(storage, TaskMemory.AFTER, Instant.EPOCH.toString());
        var cue = Event.observation(Event.TYPE_HAND_SIGN, "sensor",
                "{\"sign\":\"rock\",\"confidence\":0.95,\"ts\":\"" + Instant.now() + "\"}");
        var result = agent.acknowledge(cue, runtime);
        assertEquals(TaskReplyFixture.plan("Paper again.", false), JsonParser.parseString(result.getPayload()));
        assertEquals(2, storage.get(TaskMemory.ACTIONS).getAsInt());
        assertNull(agent.acknowledge(cue, runtime));
        verify(gateway, times(3)).infer(any());
        var requests = org.mockito.ArgumentCaptor.forClass(ch.zhaw.prometheus.spi.InferenceRequest.class);
        verify(gateway, times(3)).infer(requests.capture());
        assertTrue(requests.getAllValues().getFirst().messages().getFirst().getContent().contains("only inside reply"));
        assertTrue(requests.getAllValues().getLast().messages().getFirst().getContent().contains("returned BehaviourPlan"));

        // An invalid proposal preserves the already validated agreement; no repair call is made.
        var before = storage.get(TaskMemory.SPEC).deepCopy();
        when(gateway.infer(any())).thenReturn("{\"operation\":\"PROPOSE\",\"task\":" + spec
                + ",\"reply\":{\"speech\":\"UNVALIDATED\",\"nv\":{\"f\":[\"gentleSmile\",2]}}}");
        var rejected = agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Change it."), runtime);
        assertFalse(rejected.getPayload().contains("UNVALIDATED"));
        assertEquals(before, storage.get(TaskMemory.SPEC));
        assertEquals("RUNNING", TaskMemory.phase(storage));
        assertFalse(storage.containsKey(TaskMemory.DRAFT));
        assertEquals(2, storage.get(TaskMemory.ACTIONS).getAsInt());
        verify(gateway, times(4)).infer(any());

        // Real-provider regression: plan channels alongside reply must not authorize a proposal.
        when(gateway.infer(any())).thenReturn("{\"operation\":\"PROPOSE\",\"task\":" + spec
                + ",\"reply\":{\"speech\":\"UNVALIDATED\"},\"nv\":{\"g\":\"ACKNOWLEDGE\"},\"motion\":{\"handSign\":\"paper\"}}");
        rejected = agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Revise it."), runtime);
        assertFalse(rejected.getPayload().contains("UNVALIDATED"));
        assertEquals(before, storage.get(TaskMemory.SPEC));
        assertEquals("RUNNING", TaskMemory.phase(storage));
        assertFalse(storage.containsKey(TaskMemory.DRAFT));
        assertEquals(2, storage.get(TaskMemory.ACTIONS).getAsInt());
        verify(gateway, times(5)).infer(any());
    }
}
