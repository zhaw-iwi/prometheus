package ch.zhaw.prometheus.controllers.dto;

import java.util.List;

public class AccessCodePresetApplyRequest {
    private List<AccessCodePresetEntryRequest> entries;

    public List<AccessCodePresetEntryRequest> getEntries() {
        return this.entries;
    }

    public void setEntries(List<AccessCodePresetEntryRequest> entries) {
        this.entries = entries;
    }
}
