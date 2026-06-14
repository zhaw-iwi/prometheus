package ch.zhaw.prometheus.controllers.views;

import java.util.List;
import java.util.UUID;

public class AccessCodeView {
    private final UUID id;
    private final String code;
    private final boolean enabled;
    private final List<String> allowedAgentTypeKeys;

    public AccessCodeView(UUID id, String code, boolean enabled, List<String> allowedAgentTypeKeys) {
        this.id = id;
        this.code = code;
        this.enabled = enabled;
        this.allowedAgentTypeKeys = allowedAgentTypeKeys == null ? List.of() : List.copyOf(allowedAgentTypeKeys);
    }

    public UUID getId() {
        return this.id;
    }

    public String getCode() {
        return this.code;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public List<String> getAllowedAgentTypeKeys() {
        return this.allowedAgentTypeKeys;
    }
}
