package ch.zhaw.prometheus.model.rps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;

class RpsRevealPolicyContractTest {
    private static final Set<String> CANONICAL_HAND_SIGNS = Set.of("rock", "scissor", "paper");

    @Test
    void revealPolicyEmitsCanonicalHandSignMotionAndNoLocomotion() {
        for (RpsSign sign : RpsSign.values()) {
            Storage storage = new Storage();
            storage.put(RpsStorageKeys.CURRENT_AGENT_SIGN, new JsonPrimitive(sign.canonical()));
            storage.put(RpsStorageKeys.CURRENT_ROUND_NUMBER, new JsonPrimitive(1));

            BehaviourPlan plan = new RpsRevealPolicy(storage).onStart(null, null, null, null);

            assertNotNull(plan);
            assertNotNull(BehaviourPlan.fromJson(plan.toJson()));
            assertTrue(plan.getNonVerbal() == null);
            assertNotNull(plan.getMotion());
            JsonObject motion = plan.getMotion().getAsJsonObject();
            assertEquals(sign.canonical(), motion.get("handSign").getAsString());
            assertTrue(CANONICAL_HAND_SIGNS.contains(motion.get("handSign").getAsString()));
            assertFalse(motion.has("move"));
            assertFalse(motion.has("turn"));
            assertFalse(motion.has("locomotion"));
        }
    }
}

