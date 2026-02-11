package ch.zhaw.prometheus.controllers.views;

import java.util.List;

public class BehaviourGenerateRequest {
    private List<String> omitModalities;

    public List<String> getOmitModalities() {
        return this.omitModalities;
    }

    public void setOmitModalities(List<String> omitModalities) {
        this.omitModalities = omitModalities;
    }
}

