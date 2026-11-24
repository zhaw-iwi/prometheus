package ch.zhaw.prometheus.agentdefs.usecases;

import ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerState;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.Storage;

/** English default, sharing the Open-i event policy and complete games implementation. */
public class OpenIReceptionMultiStateGamesEnglish extends OpenIReceptionMultiStateGames {
    public static final String KEY = "usecases.open_i_reception_multi_state_games_en";

    @Override public String key() { return KEY; }
    @Override public String languageCode() { return LANGUAGE_ENGLISH; }

    @Override protected Agent completeAgent(GalaDinnerState reception, Storage storage, boolean activities) {
        return completeAgent(reception, storage, activities,
                "Gigi - Open-i with multi-state RPS and guessing game (English)",
                "English GIGI reception for Open-i 2026 on 26 November at Kongresshaus Zürich, with multi-state RPS preparation and person guessing.");
    }
}
