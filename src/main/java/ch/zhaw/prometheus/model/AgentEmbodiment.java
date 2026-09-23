package ch.zhaw.prometheus.model;

import java.util.Locale;

public enum AgentEmbodiment {
    COCKPIT("Valerian"),
    ROBOT("Gigi");

    private final String personaName;

    AgentEmbodiment(String personaName) {
        this.personaName = personaName;
    }

    public String personaName() {
        return this.personaName;
    }

    public static AgentEmbodiment fromExternalValue(String value) {
        if (value == null || value.isBlank()) {
            return COCKPIT;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported agent embodiment: " + value);
        }
    }
}
