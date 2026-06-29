package ch.zhaw.prometheus.model.rps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DeterministicRpsSignSelectorUnitTest {
    @Test
    void cyclesRockScissorPaperByCompletedRoundCount() {
        DeterministicRpsSignSelector selector = new DeterministicRpsSignSelector();

        assertEquals(RpsSign.ROCK, selector.selectForNextRound(0));
        assertEquals(RpsSign.SCISSOR, selector.selectForNextRound(1));
        assertEquals(RpsSign.PAPER, selector.selectForNextRound(2));
        assertEquals(RpsSign.ROCK, selector.selectForNextRound(3));
    }

    @Test
    void rejectsNegativeRoundCounts() {
        DeterministicRpsSignSelector selector = new DeterministicRpsSignSelector();

        assertThrows(IllegalArgumentException.class, () -> selector.selectForNextRound(-1));
    }
}
