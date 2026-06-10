package ch.zhaw.prometheus.model.rps;

import java.util.List;

public final class DeterministicRpsSignSelector {
    private static final List<RpsSign> CYCLE = List.of(RpsSign.ROCK, RpsSign.SCISSOR, RpsSign.PAPER);

    public RpsSign selectForNextRound(int completedRoundCount) {
        if (completedRoundCount < 0) {
            throw new IllegalArgumentException("completed round count must not be negative");
        }
        return CYCLE.get(completedRoundCount % CYCLE.size());
    }
}
