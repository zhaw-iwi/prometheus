package ch.zhaw.prometheus.controllers.views;

import java.util.List;

public class DemoSessionView {
    private final String accessCode;
    private final List<AdminAgentTypeView> agentTypes;
    private final List<AgentInfoView> agents;

    public DemoSessionView(String accessCode, List<AdminAgentTypeView> agentTypes, List<AgentInfoView> agents) {
        this.accessCode = accessCode;
        this.agentTypes = agentTypes == null ? List.of() : List.copyOf(agentTypes);
        this.agents = agents == null ? List.of() : List.copyOf(agents);
    }

    public String getAccessCode() {
        return this.accessCode;
    }

    public List<AdminAgentTypeView> getAgentTypes() {
        return this.agentTypes;
    }

    public List<AgentInfoView> getAgents() {
        return this.agents;
    }
}
