package ch.zhaw.prometheus.controllers.views;

import java.util.List;

public class AdminAgentTypeView {
    private final String key;
    private final String displayName;
    private final String description;
    private final List<String> packagePath;

    public AdminAgentTypeView(String key, String displayName, String description) {
        this(key, displayName, description, List.of());
    }

    public AdminAgentTypeView(String key, String displayName, String description, List<String> packagePath) {
        this.key = key;
        this.displayName = displayName;
        this.description = description;
        this.packagePath = packagePath == null ? List.of() : List.copyOf(packagePath);
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

    public List<String> getPackagePath() {
        return this.packagePath;
    }
}
