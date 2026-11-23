package ch.zhaw.prometheus.model.behaviour;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.hand.*;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;
import ch.zhaw.prometheus.model.task.*;

class NamedHandOutputUnitTest {
    final AgentInteractionProfile profile = AgentInteractionProfile.of(List.of(), List.of(HandActions.MODALITY), List.of("demo.valerian.coordination"));
    @Test void allSixNamedActionsAndSidesUseTheSameContractWithoutSpendingTaskBudget() {
        for (String sign : HandSignObservation.SIGNS) for (String side : HandActions.EFFECTORS) {
            var history = new EventHistory(); history.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Show " + sign + " silently"));
            var storage = new Storage(); storage.put(TaskMemory.ACTIONS, new JsonPrimitive(2));
            var input = JsonParser.parseString("{\"command\":{\"intent\":\"ACTION\",\"kind\":\"HAND_SIGN\",\"value\":\"" + sign + "\",\"effector\":\"" + side + "\",\"silent\":true}}").getAsJsonObject();
            var result = DirectCommands.evaluate(DirectCommands.take(input), profile, storage, history, false);
            assertEquals("ACCEPTED", result.status()); assertNull(result.plan().getSpeech());
            assertEquals(HandActions.named(sign, side), result.plan().getMotion().getAsJsonObject().get("handAction"));
            assertEquals(result.plan().toJson(), TaskMemory.plan(result.plan().toJsonObject(), profile).toJson());
            assertEquals(2, storage.get(TaskMemory.ACTIONS).getAsInt()); assertFalse(storage.containsKey(TaskMemory.SPEC));
            assertEquals("DUPLICATE", DirectCommands.evaluate(new DirectCommands.Request(DirectCommands.Intent.ACTION,"HAND_SIGN",sign,true,side),profile,storage,history,false).status());
        }
    }
    @Test void aliasesAreExplicitAndMalformedFutureOrAmbiguousActionsReject() {
        assertEquals("open_palm", HandActions.sign("halt")); assertEquals("index_up", HandActions.sign("attention"));
        assertEquals("thumbs_up", HandActions.sign("Thumbs up"));
        for (String sign : List.of("ok", "ok_ring", "shaka", "rock", "constructor"))
            assertThrows(IllegalArgumentException.class, () -> HandActions.named(sign,"right_hand"));
        var valid = HandActions.named("peace","left_hand");
        for (String invalid : List.of(valid.toString().replace("1", "2"), valid.toString().replace("1", "\"1\""),
                valid.toString().replace("left_hand", "head"), "{}"))
            assertThrows(IllegalArgumentException.class, () -> HandActions.validate(JsonParser.parseString(invalid)));
        valid.addProperty("unexpected", true); assertThrows(IllegalArgumentException.class, () -> HandActions.validate(valid));
        var motion = new JsonObject(); motion.add("handAction",HandActions.named("peace","right_hand")); motion.addProperty("handSign","rock");
        var plan = new JsonObject(); plan.add("motion",motion);
        assertThrows(IllegalArgumentException.class, () -> TaskMemory.plan(plan));
    }
    @Test void legacyProfilesAndPendingTasksCannotAuthorizeNamedOutput() {
        var limited = AgentInteractionProfile.of(List.of(),List.of("motion.handSign"),profile.getProfileTags());
        var history = new EventHistory();history.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE,"user","Show thumbs up"));
        var storage = new Storage(); var request = new DirectCommands.Request(DirectCommands.Intent.ACTION,"HAND_SIGN","thumbs_up",false);
        var result=DirectCommands.evaluate(request,limited,storage,history,false);
        assertEquals("UNSUPPORTED",result.status());assertNull(result.plan().getMotion());
        var plan=new JsonObject();var motion=new JsonObject();motion.add("handAction",HandActions.named("thumbs_up","right_hand"));plan.add("motion",motion);
        assertThrows(IllegalArgumentException.class,()->TaskMemory.plan(plan,limited));
        storage=new Storage();storage.put(TaskMemory.PHASE,new JsonPrimitive("FINISHING"));
        assertEquals("CONFLICT",DirectCommands.evaluate(request,profile,storage,history,false).status());
    }
}
