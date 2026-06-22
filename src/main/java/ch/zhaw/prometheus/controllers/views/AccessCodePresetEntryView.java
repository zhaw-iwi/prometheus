package ch.zhaw.prometheus.controllers.views;

import java.util.List;

public class AccessCodePresetEntryView {
    private final String code;
    private final List<String> agentTypeKeys;

    public AccessCodePresetEntryView(String code, List<String> agentTypeKeys) {
        this.code = code;
        this.agentTypeKeys = agentTypeKeys == null ? List.of() : List.copyOf(agentTypeKeys);
    }

    public String getCode() {
        return this.code;
    }

    public List<String> getAgentTypeKeys() {
        return this.agentTypeKeys;
    }
}
