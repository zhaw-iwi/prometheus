package ch.zhaw.prometheus.model.rps;

public final class RpsRules {
    private RpsRules() {
    }

    public static RpsRoundOutcome evaluate(RpsSign agentSign, RpsSign userSign) {
        if (agentSign == null) {
            throw new IllegalArgumentException("agent sign must not be null");
        }
        if (userSign == null) {
            throw new IllegalArgumentException("user sign must not be null");
        }
        if (agentSign == userSign) {
            return RpsRoundOutcome.DRAW;
        }
        return beats(agentSign, userSign) ? RpsRoundOutcome.AGENT_WIN : RpsRoundOutcome.USER_WIN;
    }

    public static boolean beats(RpsSign first, RpsSign second) {
        if (first == null || second == null) {
            throw new IllegalArgumentException("signs must not be null");
        }
        return (first == RpsSign.ROCK && second == RpsSign.SCISSOR)
                || (first == RpsSign.SCISSOR && second == RpsSign.PAPER)
                || (first == RpsSign.PAPER && second == RpsSign.ROCK);
    }

    public static String reason(RpsSign winningSign, RpsSign losingSign) {
        if (winningSign == null || losingSign == null) {
            throw new IllegalArgumentException("signs must not be null");
        }
        if (winningSign == losingSign) {
            return winningSign.germanLabel() + " gegen " + losingSign.germanLabel();
        }
        if (!beats(winningSign, losingSign)) {
            throw new IllegalArgumentException(winningSign + " does not beat " + losingSign);
        }
        return winningSign.germanLabel() + " schlaegt " + losingSign.germanLabel();
    }
}

