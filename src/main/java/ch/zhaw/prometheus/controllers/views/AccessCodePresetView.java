package ch.zhaw.prometheus.controllers.views;

import java.util.List;

public class AccessCodePresetView {
    private final String key;
    private final String displayName;
    private final List<AccessCodePresetEntryView> entries;

    public AccessCodePresetView(String key, String displayName, List<AccessCodePresetEntryView> entries) {
        this.key = key;
        this.displayName = displayName;
        this.entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public String getKey() {
        return this.key;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public List<AccessCodePresetEntryView> getEntries() {
        return this.entries;
    }
}
