package ch.zhaw.prometheus.model.behaviour;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;
import ch.zhaw.prometheus.model.task.*;

class DirectCommandsUnitTest {
    final Storage storage = new Storage();
    final AgentInteractionProfile profile = AgentInteractionProfile.of(List.of(Event.TYPE_USER_UTTERANCE),
            List.of("motion.handSign", "nonVerbal.gesture"), List.of("demo.valerian.coordination"));
    final EventHistory history = new EventHistory();
    EventHistory turn(String text) { history.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, "user", text)); return history; }
    DirectCommands.Request request(String intent, String kind, String value, boolean silent) {
        var command = new JsonObject(); command.addProperty("intent", intent); command.addProperty("kind", kind);
        command.addProperty("value", value); command.addProperty("silent", silent);
        var root = new JsonObject(); root.add("command", command); return DirectCommands.take(root);
    }
    @ParameterizedTest @ValueSource(strings = {"rock", "paper", "scissor", "scissors"})
    void signsNormalizeAndRepeatedTurnsExecuteWithoutRepeatingOneTurn(String sign) {
        var input = turn("Can you show " + sign + "?"); var command = request("ACTION", "HAND_SIGN", sign, false);
        var first = DirectCommands.evaluate(command, profile, storage, input, false);
        assertEquals("ACCEPTED", first.status());
        assertEquals(sign.equals("scissors") ? "scissor" : sign, first.plan().getMotion().getAsJsonObject().get("handSign").getAsString());
        assertEquals("NONE", first.plan().getNonVerbal().getAsJsonObject().get("gesture").getAsString());
        assertEquals("DUPLICATE", DirectCommands.evaluate(command, profile, storage, input, false).status());
        assertTrue(DirectCommands.evaluate(command, profile, storage, input, false).plan().isEmpty());
        assertEquals("ACCEPTED", DirectCommands.evaluate(command, profile, storage, turn("Again, please"), false).status());
        assertFalse(storage.containsKey(TaskMemory.ACTIONS)); assertFalse(storage.containsKey(TaskMemory.DRAFT));
    }
    @ParameterizedTest @ValueSource(strings = {"OPEN_QUESTION", "EXPLAIN", "UNCERTAIN", "ACKNOWLEDGE", "POLITE"})
    void requestedGestureBypassesOnlyItsDecorativeCadenceAndCanBeSilent(String gesture) {
        var input = turn("Show a gesture without speaking"); var command = request("ACTION", "GESTURE", gesture, true);
        var first = DirectCommands.evaluate(command, profile, storage, input, false);
        assertNull(first.plan().getSpeech()); assertNull(first.plan().getMotion());
        assertEquals(gesture, first.plan().getNonVerbal().getAsJsonObject().get("gesture").getAsString());
        assertTrue(storage.get(DecorativeGestures.REQUIRED).getAsBoolean());
        assertEquals(gesture, DecorativeGestures.publish(storage, first.plan(), true, java.time.Instant.now())
                .getNonVerbal().getAsJsonObject().get("gesture").getAsString());
    }
    @ParameterizedTest @ValueSource(strings = {"CAPABILITY", "CLARIFY", "UNSUPPORTED"})
    void nonActionInterpretationsNeverPublishMotionOrChangeAgreement(String intent) {
        storage.put(TaskMemory.SPEC, JsonParser.parseString("{\"kept\":true}")); storage.put(TaskMemory.ACTIONS, new JsonPrimitive(2));
        var result = DirectCommands.evaluate(request(intent, null, null, false), profile, storage, turn("A non-action request"), false);
        assertNotNull(result.plan().getSpeech()); assertNull(result.plan().getMotion()); assertNull(result.plan().getNonVerbal());
        assertEquals(2, storage.get(TaskMemory.ACTIONS).getAsInt()); assertTrue(storage.get(TaskMemory.SPEC).getAsJsonObject().get("kept").getAsBoolean());
        assertFalse(storage.containsKey(DecorativeGestures.REQUIRED));
    }
    @ParameterizedTest @ValueSource(strings = {"CONFIGURATION", "RUNNING", "PAUSED", "FINISHING", "UNCERTAIN"})
    void pendingOutputConflictsAreExplicitAndDoNotSpendTaskBudget(String phase) {
        storage.put(TaskMemory.PHASE, new JsonPrimitive(phase)); storage.put(TaskMemory.ACTIONS, new JsonPrimitive(1));
        storage.put(TaskMemory.REVISION, new JsonPrimitive(4));
        boolean blocked = Set.of("FINISHING", "UNCERTAIN").contains(phase);
        var result = DirectCommands.evaluate(request("ACTION", "HAND_SIGN", "paper", false), profile, storage, turn("Show paper"), false);
        assertEquals(blocked ? "CONFLICT" : "ACCEPTED", result.status());
        assertEquals(1, storage.get(TaskMemory.ACTIONS).getAsInt()); assertEquals(4, storage.get(TaskMemory.REVISION).getAsInt());
        assertEquals(phase, TaskMemory.phase(storage));
        TaskResponse.expect(storage, new ch.zhaw.prometheus.model.policy.ExternalSpeech(UUID.randomUUID(), UUID.randomUUID()),
                UUID.randomUUID(), "Current task output", java.time.Instant.now());
        assertEquals("CONFLICT", DirectCommands.evaluate(request("ACTION", "GESTURE", "EXPLAIN", false), profile, storage, turn("Explain gesture"), false).status());
    }
    @Test void profileGatesOutputsEvenWhenTheModelRequestsAnOtherwiseValidAction() {
        var limited = AgentInteractionProfile.of(List.of(), List.of("speech"), profile.getProfileTags());
        var result = DirectCommands.evaluate(request("ACTION", "HAND_SIGN", "rock", false), limited, storage, turn("Make rock"), false);
        assertEquals("UNSUPPORTED", result.status()); assertNull(result.plan().getMotion());
        assertThrows(IllegalArgumentException.class, () -> DirectCommands.evaluate(request("ACTION", "HAND_SIGN", "rock", false),
                AgentInteractionProfile.of(List.of(), profile.getSupportedBehaviourModalities(), List.of()), storage, turn("Make rock"), false));
    }
    @Test void malformedCommandsTaskChangesAndUnrelatedObservationCannotAuthorizeAnOutput() {
        for (var bad : List.of("NONE", "WAVE", "open_question_gesture"))
            assertThrows(IllegalArgumentException.class, () -> request("ACTION", "GESTURE", bad, false));
        assertThrows(IllegalArgumentException.class, () -> request("CAPABILITY", "HAND_SIGN", "rock", false));
        var root = JsonParser.parseString("""
            {"operation":"START","agreement":null,"reply":{},
             "command":{"intent":"ACTION","kind":"HAND_SIGN","value":"rock","silent":false}}
            """).getAsJsonObject();
        assertThrows(IllegalArgumentException.class, () -> DirectCommands.taskEnvelope(root, "agreement", profile, storage, turn("Make rock"), false));
        var history = turn("Make rock"); history.appendEvent(Event.systemTick());
        assertThrows(IllegalArgumentException.class, () -> DirectCommands.evaluate(request("ACTION", "HAND_SIGN", "rock", false), profile, storage, history, false));
        assertFalse(storage.containsKey(DirectCommands.HISTORY));
    }
    @Test void identityHistoryIsBoundedReloadableAndExplicitResetClearsIt() {
        for (int i = 0; i < 70; i++) DirectCommands.evaluate(request("ACTION", "HAND_SIGN", "rock", false), profile, storage, turn("Show rock"), true);
        assertEquals(64, storage.get(DirectCommands.HISTORY).getAsJsonArray().size());
        var copy = new Storage(); copy.put(DirectCommands.HISTORY, JsonParser.parseString(storage.get(DirectCommands.HISTORY).toString()));
        assertEquals(storage.get(DirectCommands.HISTORY), copy.get(DirectCommands.HISTORY));
        DirectCommands.clear(storage); assertFalse(storage.containsKey(DirectCommands.HISTORY)); assertFalse(storage.containsKey(DirectCommands.LAST));
    }
}
