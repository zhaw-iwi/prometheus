package ch.zhaw.prometheus.application;

import java.util.List;

public class AccessCodePresetEntrySpec {
    private final String code;
    private final List<String> agentTypeKeys;

    public AccessCodePresetEntrySpec(String code, List<String> agentTypeKeys) {
        this.code = code;
        this.agentTypeKeys = agentTypeKeys == null ? List.of() : List.copyOf(agentTypeKeys);
    }

    public String code() {
        return this.code;
    }

    public List<String> agentTypeKeys() {
        return this.agentTypeKeys;
    }
}
