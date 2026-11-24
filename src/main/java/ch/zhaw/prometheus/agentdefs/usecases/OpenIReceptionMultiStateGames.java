package ch.zhaw.prometheus.agentdefs.usecases;

import java.util.List;

import ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerState;
import ch.zhaw.prometheus.agentdefs.usecases.gala.OpenIPolicy;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.Storage;

/** Open-i setting with the Gala games variant's explicit control graph. */
public class OpenIReceptionMultiStateGames extends GalaDinnerReceptionMultiStateGames {
    public static final String KEY = "usecases.open_i_reception_multi_state_games";

    @Override public String key() { return KEY; }

    @Override protected GalaDinnerState activityState(String name, Storage storage, boolean starting, boolean coCreatedTasks) {
        return new GalaDinnerState(name.replaceFirst("^Gala", "Open-i"), new OpenIPolicy(storage), starting);
    }

    @Override protected Agent completeAgent(GalaDinnerState reception, Storage storage, boolean activities) {
        return completeAgent(reception, storage, activities,
                "Gigi - Open-i with multi-state RPS and guessing game",
                "German GIGI reception for Open-i 2026 on 26 November at Kongresshaus Zürich, with multi-state RPS preparation and person guessing.");
    }

    @Override protected List<String> profileTags() {
        return List.of("demo.gigi.open_i", "demo.gigi.sira_lab");
    }
}
