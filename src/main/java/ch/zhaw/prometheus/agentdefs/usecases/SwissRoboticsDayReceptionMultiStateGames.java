package ch.zhaw.prometheus.agentdefs.usecases;

import java.util.List;

import ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerState;
import ch.zhaw.prometheus.agentdefs.usecases.gala.SwissRoboticsDayPolicy;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.Storage;

/** Swiss Robotics Day setting with the Gala games variant's explicit control graph. */
public class SwissRoboticsDayReceptionMultiStateGames extends GalaDinnerReceptionMultiStateGames {
    public static final String KEY = "usecases.swiss_robotics_day_reception_multi_state_games";

    @Override public String key() { return KEY; }

    @Override protected GalaDinnerState activityState(String name, Storage storage, boolean starting, boolean coCreatedTasks) {
        return new GalaDinnerState(name.replaceFirst("^Gala", "Swiss Robotics Day"),
                new SwissRoboticsDayPolicy(storage), starting);
    }

    @Override protected Agent completeAgent(GalaDinnerState reception, Storage storage, boolean activities) {
        return completeAgent(reception, storage, activities,
                "Gigi - Swiss Robotics Day with multi-state RPS and guessing game",
                "German GIGI reception for Swiss Robotics Day 2026 on 13 November at StageOne in Zurich, with multi-state RPS preparation and person guessing.");
    }

    @Override protected List<String> profileTags() {
        return List.of("demo.gigi.swiss_robotics_day", "demo.gigi.sira_lab");
    }
}
