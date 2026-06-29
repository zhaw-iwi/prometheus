package ch.zhaw.prometheus.application;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class AccessCodePresetCatalog {
    public List<AccessCodePresetSpec> list() {
        return List.of(shhdSceneAgents());
    }

    private static AccessCodePresetSpec shhdSceneAgents() {
        return new AccessCodePresetSpec(
                "shhd_scene_agents",
                "SHHD scene access codes",
                List.of(
                        entry("shhde",
                                "tdsr.shhd.de.epfl_active",
                                "tdsr.shhd.de.furka",
                                "tdsr.shhd.de.interviewing_people",
                                "tdsr.shhd.de.supsi_active",
                                "tdsr.shhd.de.unis_student"),
                        entry("shhen",
                                "tdsr.shhd.en.epfl_active",
                                "tdsr.shhd.en.furka",
                                "tdsr.shhd.en.interviewing_people",
                                "tdsr.shhd.en.supsi_active",
                                "tdsr.shhd.en.unis_student"),
                        entry("shhfr",
                                "tdsr.shhd.fr.epfl_active",
                                "tdsr.shhd.fr.furka",
                                "tdsr.shhd.fr.interviewing_people",
                                "tdsr.shhd.fr.supsi_active",
                                "tdsr.shhd.fr.unis_student"),
                        entry("shhit",
                                "tdsr.shhd.it.epfl_active",
                                "tdsr.shhd.it.furka",
                                "tdsr.shhd.it.interviewing_people",
                                "tdsr.shhd.it.supsi_active",
                                "tdsr.shhd.it.unis_student"),
                        entry("shhba",
                                "tdsr.shhd.babylon.epfl_active",
                                "tdsr.shhd.babylon.furka",
                                "tdsr.shhd.babylon.interviewing_people",
                                "tdsr.shhd.babylon.supsi_active",
                                "tdsr.shhd.babylon.unis_student")));
    }

    private static AccessCodePresetEntrySpec entry(String code, String... agentTypeKeys) {
        return new AccessCodePresetEntrySpec(code, List.of(agentTypeKeys));
    }
}
