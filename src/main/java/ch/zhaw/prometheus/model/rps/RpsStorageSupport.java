package ch.zhaw.prometheus.model.rps;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import ch.zhaw.prometheus.model.Storage;

final class RpsStorageSupport {
    private RpsStorageSupport() {
    }

    static JsonArray rounds(Storage storage) {
        if (storage == null || !storage.containsKey(RpsStorageKeys.ROUNDS)) {
            return new JsonArray();
        }
        JsonElement value = storage.get(RpsStorageKeys.ROUNDS);
        if (value == null || !value.isJsonArray()) {
            return new JsonArray();
        }
        return value.getAsJsonArray().deepCopy();
    }

    static int completedRoundCount(Storage storage) {
        return rounds(storage).size();
    }

    static RpsSign currentAgentSign(Storage storage) {
        if (storage == null || !storage.containsKey(RpsStorageKeys.CURRENT_AGENT_SIGN)) {
            throw new IllegalStateException("RPS agent sign has not been selected");
        }
        return RpsSign.parse(storage.get(RpsStorageKeys.CURRENT_AGENT_SIGN).getAsString());
    }

    static int currentRoundNumber(Storage storage) {
        if (storage == null || !storage.containsKey(RpsStorageKeys.CURRENT_ROUND_NUMBER)) {
            return completedRoundCount(storage) + 1;
        }
        return storage.get(RpsStorageKeys.CURRENT_ROUND_NUMBER).getAsInt();
    }

    static JsonObject lastRound(Storage storage) {
        if (storage == null || !storage.containsKey(RpsStorageKeys.LAST_ROUND)) {
            throw new IllegalStateException("RPS round result is not available");
        }
        JsonElement value = storage.get(RpsStorageKeys.LAST_ROUND);
        if (value == null || !value.isJsonObject()) {
            throw new IllegalStateException("RPS round result is malformed");
        }
        return value.getAsJsonObject();
    }
}

