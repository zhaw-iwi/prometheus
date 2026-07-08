package ch.zhaw.prometheus.model.rps;

public enum RpsRoundOutcome {
    AGENT_WIN("agent"),
    USER_WIN("user"),
    DRAW("draw");

    private final String winner;

    RpsRoundOutcome(String winner) {
        this.winner = winner;
    }

    public String winner() {
        return this.winner;
    }
}

