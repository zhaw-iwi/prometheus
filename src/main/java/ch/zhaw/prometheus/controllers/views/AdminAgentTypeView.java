package ch.zhaw.prometheus.controllers.views;

public class AdminAgentTypeView {
    private final String key;
    private final String displayName;
    private final String description;

    public AdminAgentTypeView(String key, String displayName, String description) {
        this.key = key;
        this.displayName = displayName;
        this.description = description;
    }

    public String getKey() {
        return this.key;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public String getDescription() {
        return this.description;
    }
}
