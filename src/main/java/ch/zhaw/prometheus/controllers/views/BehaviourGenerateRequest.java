package ch.zhaw.prometheus.controllers.views;

import java.util.List;

public class BehaviourGenerateRequest {
    private List<String> omitModalities;
    private String outputProfile;

    public List<String> getOmitModalities() {
        return this.omitModalities;
    }

    public void setOmitModalities(List<String> omitModalities) {
        this.omitModalities = omitModalities;
    }

    public String getOutputProfile() {
        return this.outputProfile;
    }

    public void setOutputProfile(String outputProfile) {
        this.outputProfile = outputProfile;
    }
}
