package ch.zhaw.prometheus.agentdefs.usecases;

import ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerState;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.Storage;

/** English default, sharing the Swiss event policy and complete games implementation. */
public class SwissRoboticsDayReceptionMultiStateGamesEnglish extends SwissRoboticsDayReceptionMultiStateGames {
    public static final String KEY = "usecases.swiss_robotics_day_reception_multi_state_games_en";

    @Override public String key() { return KEY; }
    @Override public String languageCode() { return LANGUAGE_ENGLISH; }

    @Override protected Agent completeAgent(GalaDinnerState reception, Storage storage, boolean activities) {
        return completeAgent(reception, storage, activities,
                "Gigi - Swiss Robotics Day with multi-state RPS and guessing game (English)",
                "English GIGI reception for Swiss Robotics Day 2026 on 13 November at StageOne in Zurich, with multi-state RPS preparation and person guessing.");
    }
}
