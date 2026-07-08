package ch.zhaw.prometheus.model.rps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RpsRulesUnitTest {
    @Test
    void evaluatesAllCanonicalRoundOutcomes() {
        assertEquals(RpsRoundOutcome.DRAW, RpsRules.evaluate(RpsSign.ROCK, RpsSign.ROCK));
        assertEquals(RpsRoundOutcome.DRAW, RpsRules.evaluate(RpsSign.SCISSOR, RpsSign.SCISSOR));
        assertEquals(RpsRoundOutcome.DRAW, RpsRules.evaluate(RpsSign.PAPER, RpsSign.PAPER));

        assertEquals(RpsRoundOutcome.AGENT_WIN, RpsRules.evaluate(RpsSign.ROCK, RpsSign.SCISSOR));
        assertEquals(RpsRoundOutcome.AGENT_WIN, RpsRules.evaluate(RpsSign.SCISSOR, RpsSign.PAPER));
        assertEquals(RpsRoundOutcome.AGENT_WIN, RpsRules.evaluate(RpsSign.PAPER, RpsSign.ROCK));

        assertEquals(RpsRoundOutcome.USER_WIN, RpsRules.evaluate(RpsSign.SCISSOR, RpsSign.ROCK));
        assertEquals(RpsRoundOutcome.USER_WIN, RpsRules.evaluate(RpsSign.PAPER, RpsSign.SCISSOR));
        assertEquals(RpsRoundOutcome.USER_WIN, RpsRules.evaluate(RpsSign.ROCK, RpsSign.PAPER));
    }

    @Test
    void normalizesExpectedSignTokens() {
        assertEquals(RpsSign.ROCK, RpsSign.parse("rock"));
        assertEquals(RpsSign.ROCK, RpsSign.parse("Stein"));
        assertEquals(RpsSign.SCISSOR, RpsSign.parse("scissors"));
        assertEquals(RpsSign.SCISSOR, RpsSign.parse("Schere"));
        assertEquals(RpsSign.PAPER, RpsSign.parse("Papier"));
    }

    @Test
    void rejectsInvalidSignsLoudly() {
        assertThrows(IllegalArgumentException.class, () -> RpsSign.parse("lizard"));
        assertThrows(IllegalArgumentException.class, () -> RpsRules.evaluate(null, RpsSign.ROCK));
        assertThrows(IllegalArgumentException.class, () -> RpsRules.reason(RpsSign.ROCK, RpsSign.PAPER));
    }
}

