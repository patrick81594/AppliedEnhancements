package com.appliedenhancements.chance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChanceOutputBonusTest {
    @Test
    void noExcessMeansNoBonus() {
        assertEquals(0, ChanceOutputBonus.bonusToAccept(0, 10));
    }

    @Test
    void jobThatNeedsNothingTakesNoBonus() {
        assertEquals(0, ChanceOutputBonus.bonusToAccept(5, 0));
    }

    @Test
    void bonusIsCappedAtWhatTheJobStillNeeds() {
        assertEquals(3, ChanceOutputBonus.bonusToAccept(5, 3));
    }

    @Test
    void bonusIsCappedAtTheExcessWhenTheJobNeedsMore() {
        assertEquals(2, ChanceOutputBonus.bonusToAccept(2, 9));
    }

    @Test
    void negativeInputsGiveNoBonus() {
        assertEquals(0, ChanceOutputBonus.bonusToAccept(-1, -1));
    }
}
