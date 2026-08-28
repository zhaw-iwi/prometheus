package ch.zhaw.prometheus.agentdefs.tdsr.aisha;

import java.util.List;

import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;

public final class AishaInteractionProfiles {
    public static final String TAG_AISHA = "demo.aisha";
    public static final String TAG_INVEST_QATAR = "customer.invest_qatar";
    public static final String TAG_CATALOG_QA = "demo.aisha.catalog_qa";

    private AishaInteractionProfiles() {
    }

    public static AgentInteractionProfile verbalCatalogQa() {
        return AgentInteractionProfile.of(
                List.of(AgentInteractionProfile.OBS_USER_UTTERANCE),
                List.of(
                        AgentInteractionProfile.MODALITY_SPEECH,
                        AgentInteractionProfile.MODALITY_NONVERBAL_GESTURE),
                List.of(TAG_AISHA, TAG_INVEST_QATAR, TAG_CATALOG_QA));
    }
}
