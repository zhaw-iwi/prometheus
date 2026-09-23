package ch.zhaw.prometheus.model.rps;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import ch.zhaw.prometheus.model.Storage;

public record RpsMatchScore(int agentWins, int userWins, int draws) {
    public static RpsMatchScore from(Storage storage) {
        int agentWins = 0;
        int userWins = 0;
        int draws = 0;
        for (JsonElement element : RpsStorageSupport.rounds(storage)) {
            if (element == null || !element.isJsonObject()) {
                throw new IllegalStateException("RPS round history is malformed");
            }
            JsonObject round = element.getAsJsonObject();
            if (!round.has("winner") || round.get("winner").isJsonNull()) {
                throw new IllegalStateException("RPS round winner is missing");
            }
            switch (round.get("winner").getAsString()) {
                case "agent" -> agentWins++;
                case "user" -> userWins++;
                case "draw" -> draws++;
                default -> throw new IllegalStateException("unsupported RPS winner in round history");
            }
        }
        return new RpsMatchScore(agentWins, userWins, draws);
    }

    public String winnerAt(int targetWins) {
        if (targetWins < 1) {
            throw new IllegalArgumentException("target wins must be positive");
        }
        if (this.agentWins >= targetWins) {
            return "agent";
        }
        if (this.userWins >= targetWins) {
            return "user";
        }
        return null;
    }
}
