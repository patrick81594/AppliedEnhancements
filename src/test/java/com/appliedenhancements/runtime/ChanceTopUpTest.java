package com.appliedenhancements.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.appliedenhancements.test.TestAEKey;
import org.junit.jupiter.api.Test;

class ChanceTopUpTest {
    @Test
    void yieldStartsAtOnePerCraftAndFollowsObservations() {
        assertEquals(1.0, ChanceTopUp.estimateYield(0, 0), 1e-9);
        assertEquals(10.0 / 16.0, ChanceTopUp.estimateYield(0, 6), 1e-9);
        assertTrue(ChanceTopUp.estimateYield(50, 1000) < 0.06);
        // an empty wave of a slow machine (8 crafts, nothing yet) must not look like a 1-in-9 pattern
        assertTrue(ChanceTopUp.estimateYield(0, 8) > 0.5);
    }

    @Test
    void craftsNeededRoundsUpAndRespectsTheLimit() {
        assertEquals(20, ChanceTopUp.craftsNeeded(1, 0.05, 100));
        assertEquals(5, ChanceTopUp.craftsNeeded(1, 0.05, 5));
        assertEquals(0, ChanceTopUp.craftsNeeded(0, 0.5, 5));
        assertEquals(0, ChanceTopUp.craftsNeeded(3, 0.5, 0));
        assertEquals(0, ChanceTopUp.craftsNeeded(3, 0, 5));
    }

    @Test
    void affordableCraftsIsLimitedByTheScarcestInput() {
        assertEquals(3, ChanceTopUp.affordableCrafts(10, new long[] {2, 5}, new long[] {100, 17}));
        assertEquals(0, ChanceTopUp.affordableCrafts(10, new long[] {2}, new long[] {1}));
        assertEquals(4, ChanceTopUp.affordableCrafts(4, new long[] {1}, new long[] {100}));
    }

    @Test
    void missingForCraftsIsWhatStockCannotCover() {
        assertArrayEquals(new long[] {0, 7}, ChanceTopUp.missingForCrafts(5, new long[] {1, 5}, new long[] {9, 18}));
        assertArrayEquals(new long[] {5}, ChanceTopUp.missingForCrafts(5, new long[] {1}, new long[] {0}));
        assertArrayEquals(new long[] {0}, ChanceTopUp.missingForCrafts(0, new long[] {3}, new long[] {0}));
    }

    @Test
    void retryResetsLimitAndBackoffAndSkipsOneIdleGate() {
        var state = new ChanceTopUpState<String>();
        var key = new TestAEKey("olivine");
        state.onPush("p", key, 0);
        state.endWave("p", key);
        state.addExtra("p", 40);
        state.endWave("p", key);
        assertEquals(4, state.idleMultiplier());
        long before = state.allowedExtra("p", 10);
        state.requestRetry();
        assertEquals(1, state.idleMultiplier());
        assertTrue(state.allowedExtra("p", 10) > before);
        assertTrue(state.consumeRetryNow());
        assertFalse(state.consumeRetryNow());
    }

    @Test
    void idleNeedsRecordedActivity() {
        assertFalse(ChanceTopUp.isIdle(1000, -1, 10));
        assertFalse(ChanceTopUp.isIdle(1005, 1000, 10));
        assertTrue(ChanceTopUp.isIdle(1010, 1000, 10));
    }

    @Test
    void idleTimeDoublesWithEveryEmptyWaveAndResetsOnOutput() {
        var state = new ChanceTopUpState<String>();
        var key = new TestAEKey("olivine");
        assertEquals(1, state.idleMultiplier());
        long expected = 1;
        for (int i = 0; i < 8; i++) {
            state.onPush("p", key, i);
            state.endWave("p", key);
            expected = Math.min(expected * 2, 32);
            assertEquals(expected, state.idleMultiplier());
        }
        state.onOutput(key, 1, 100);
        assertEquals(1, state.idleMultiplier());
    }

    @Test
    void stateCountsWavesAndLimitsExtras() {
        var state = new ChanceTopUpState<String>();
        var flint = new TestAEKey("flint");
        for (int i = 0; i < 4; i++) {
            state.onPush("sieve", flint, 10 + i);
        }
        state.onOutput(flint, 2, 20);
        assertEquals("sieve", state.producerOf(flint));
        assertEquals(20, state.lastActivity());
        assertArrayEquals(new long[] {2, 4}, state.endWave("sieve", flint));
        assertEquals(40, state.allowedExtra("sieve", 10));
        state.addExtra("sieve", 15);
        assertEquals(25, state.allowedExtra("sieve", 10));
        // the second wave does not change the planned count
        state.onPush("sieve", flint, 30);
        assertArrayEquals(new long[] {0, 1}, state.endWave("sieve", flint));
        assertEquals(25, state.allowedExtra("sieve", 10));
        // late outputs with no crafts in the wave are kept for the next wave, not lost
        state.onOutput(flint, 3, 40);
        assertArrayEquals(new long[] {3, 0}, state.endWave("sieve", flint));
        state.onPush("sieve", flint, 50);
        assertArrayEquals(new long[] {3, 1}, state.endWave("sieve", flint));
        state.clear();
        assertEquals(0, state.allowedExtra("sieve", 10));
        state.seedPlannedIfUnknown("sieve", 3);
        assertEquals(30, state.allowedExtra("sieve", 10));
        state.seedPlannedIfUnknown("sieve", 99);
        assertEquals(30, state.allowedExtra("sieve", 10));
        state.clear();
        assertEquals(-1, state.lastActivity());
    }
}
