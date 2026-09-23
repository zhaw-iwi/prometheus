package ch.zhaw.prometheus.agentdefs.core;

import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.rps.RpsSign;
import ch.zhaw.prometheus.model.rps.RpsStorageKeys;

final class CoreRpsLocale {
    private static final String LANGUAGE_GERMAN = "de";

    private CoreRpsLocale() {
    }

    static boolean isGerman(Storage storage) {
        return storage != null
                && storage.containsKey(RpsStorageKeys.LANGUAGE_CODE)
                && LANGUAGE_GERMAN.equalsIgnoreCase(
                        storage.get(RpsStorageKeys.LANGUAGE_CODE).getAsString());
    }

    static String label(Storage storage, RpsSign sign) {
        if (isGerman(storage)) {
            return sign.germanLabel();
        }
        return sign.canonical();
    }

    static String reason(Storage storage, RpsSign winningSign, RpsSign losingSign) {
        if (isGerman(storage)) {
            return label(storage, winningSign) + " schlägt " + label(storage, losingSign);
        }
        return label(storage, winningSign) + " beats " + label(storage, losingSign);
    }

    static String drawReason(Storage storage, RpsSign sign) {
        if (isGerman(storage)) {
            return label(storage, sign) + " gegen " + label(storage, sign);
        }
        return label(storage, sign) + " against " + label(storage, sign);
    }
}
