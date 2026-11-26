package ch.zhaw.prometheus.model.policy;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class GestureCollectionUnitTest {
    @Test void rightHandUpSurvivesCanonicalAndCompactInferenceWhileApologyIsSuppressed() {
        for (String label : new String[] {"RIGHT_HAND_UP", "right_hand_up", "POLITE", "POLITE_APOLOGY", "polite_apology_gesture"}) {
            String expected = label.equalsIgnoreCase("RIGHT_HAND_UP") ? "RIGHT_HAND_UP" : "NONE";
            for (String json : new String[] {
                    "{\"speech\":\"Hello\",\"nonVerbal\":{\"gesture\":\"" + label + "\"}}",
                    "{\"speech\":\"Hello\",\"nv\":{\"g\":\"" + label + "\"}}"}) {
                assertEquals(expected, BehaviourPlanInference.parse(json).getNonVerbal()
                        .getAsJsonObject().get("gesture").getAsString());
            }
        }
    }
}
