package ch.zhaw.prometheus.model.rps;

import java.util.Locale;

public enum RpsSign {
    ROCK("rock", "Stein"),
    SCISSOR("scissor", "Schere"),
    PAPER("paper", "Papier");

    private final String canonical;
    private final String germanLabel;

    RpsSign(String canonical, String germanLabel) {
        this.canonical = canonical;
        this.germanLabel = germanLabel;
    }

    public String canonical() {
        return this.canonical;
    }

    public String germanLabel() {
        return this.germanLabel;
    }

    public static RpsSign parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("hand sign must not be blank");
        }
        String normalized = raw.trim()
                .toLowerCase(Locale.ROOT)
                .replace("-", "_")
                .replace(" ", "_");
        return switch (normalized) {
            case "rock", "stein" -> ROCK;
            case "scissor", "scissors", "schere" -> SCISSOR;
            case "paper", "papier" -> PAPER;
            default -> throw new IllegalArgumentException("unsupported hand sign: " + raw);
        };
    }
}

