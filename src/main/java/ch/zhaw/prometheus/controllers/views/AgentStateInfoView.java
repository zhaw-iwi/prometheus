package ch.zhaw.prometheus.controllers.views;

public class AgentStateInfoView {
    private java.util.List<java.util.UUID> activeStateIds = java.util.List.of();
    public java.util.List<java.util.UUID> getActiveStateIds() { return activeStateIds; }
    public AgentStateInfoView withActiveStateIds(java.util.List<java.util.UUID> ids) { activeStateIds = java.util.List.copyOf(ids); return this; }
    private String name;
    private String innerName;
    private java.util.List<String> innerNames;

    public AgentStateInfoView(String name, String innerName, java.util.List<String> innerNames) {
        this.name = name;
        this.innerName = innerName;
        this.innerNames = innerNames;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInnerName() {
        return innerName;
    }

    public void setInnerName(String innerName) {
        this.innerName = innerName;
    }

    public java.util.List<String> getInnerNames() {
        return innerNames;
    }

    public void setInnerNames(java.util.List<String> innerNames) {
        this.innerNames = innerNames;
    }
}
