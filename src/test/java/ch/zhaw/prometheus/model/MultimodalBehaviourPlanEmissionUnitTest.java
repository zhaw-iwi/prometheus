package ch.zhaw.prometheus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.Policy;

class MultimodalBehaviourPlanEmissionUnitTest {

    @Test
    void emitsFullMultimodalPlanInPayloadWithoutServerSideReduction() {
        State state = new State("conversation", new MultimodalPolicy(), List.of());
        Agent agent = new Agent("a", "d", state);

        Event start = agent.start();

        assertNotNull(start);
        assertEquals(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, start.getType());
        JsonObject payload = JsonParser.parseString(start.getPayload()).getAsJsonObject();
        assertEquals("hello", payload.get("speech").getAsString());
        assertEquals("wave", payload.getAsJsonObject("nonVerbal").get("gesture").getAsString());
        assertEquals("step_forward", payload.getAsJsonObject("motion").get("action").getAsString());
        assertEquals("banner", payload.getAsJsonObject("display").get("mode").getAsString());
        assertNull(payload.get("nonVerbal").getAsJsonObject().get("unknown"));
    }

    private static class MultimodalPolicy extends Policy {
        @Override
        public BehaviourPlan onStart(State state, EventHistory events) {
            JsonObject nonVerbal = new JsonObject();
            nonVerbal.addProperty("gesture", "wave");
            JsonObject motion = new JsonObject();
            motion.addProperty("action", "step_forward");
            JsonObject display = new JsonObject();
            display.addProperty("mode", "banner");
            return new BehaviourPlan("hello", nonVerbal, motion, display);
        }

        @Override
        public BehaviourPlan onRespond(State state, EventHistory events) {
            return null;
        }

        @Override
        public String summarise(State state, EventHistory events) {
            return "";
        }

        @Override
        public String describe() {
            return "multimodal";
        }
    }
}

