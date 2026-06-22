package ch.zhaw.prometheus.application;

import java.util.List;

public class AccessCodePresetSpec {
    private final String key;
    private final String displayName;
    private final List<AccessCodePresetEntrySpec> entries;

    public AccessCodePresetSpec(String key, String displayName, List<AccessCodePresetEntrySpec> entries) {
        this.key = key;
        this.displayName = displayName;
        this.entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public String key() {
        return this.key;
    }

    public String displayName() {
        return this.displayName;
    }

    public List<AccessCodePresetEntrySpec> entries() {
        return this.entries;
    }
}
