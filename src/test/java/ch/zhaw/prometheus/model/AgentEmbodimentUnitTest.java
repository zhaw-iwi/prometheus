package ch.zhaw.prometheus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AgentEmbodimentUnitTest {
    @Test
    void mapsEmbodimentsToTheirSpokenPersonas() {
        assertEquals("Valerian", AgentEmbodiment.COCKPIT.personaName());
        assertEquals("Gigi", AgentEmbodiment.ROBOT.personaName());
    }

    @Test
    void parsesExternalValuesAndDefaultsToCockpit() {
        assertEquals(AgentEmbodiment.COCKPIT, AgentEmbodiment.fromExternalValue(null));
        assertEquals(AgentEmbodiment.COCKPIT, AgentEmbodiment.fromExternalValue(" "));
        assertEquals(AgentEmbodiment.COCKPIT, AgentEmbodiment.fromExternalValue("cockpit"));
        assertEquals(AgentEmbodiment.ROBOT, AgentEmbodiment.fromExternalValue("robot"));
        assertThrows(IllegalArgumentException.class,
                () -> AgentEmbodiment.fromExternalValue("avatar"));
    }
}
