package com.appliedenhancements.runtime;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;

/**
 * Rules for counting extra (chance) outputs of marked processing patterns.
 *
 * Used by the CPU mixins only when {@code Config.ENABLE_CHANCE_OUTPUT_BONUS} is on.
 */
public final class ChanceOutputBonus {
    private ChanceOutputBonus() {
    }

    /**
     * How many of the {@code excess} items a CPU should accept, given how many items the job
     * still needs. The bonus never overshoots the requested total.
     */
    public static long bonusToAccept(long excess, long remainingNeeded) {
        if (excess <= 0 || remainingNeeded <= 0) {
            return 0;
        }
        return Math.min(excess, remainingNeeded);
    }

    /**
     * True when one of the job's patterns is marked as a chance-output pattern and its primary
     * output is {@code what}. Only such patterns receive the bonus.
     */
    public static boolean hasMarkedProducer(Iterable<IPatternDetails> patterns, AEKey what) {
        if (what == null) {
            return false;
        }
        for (IPatternDetails pattern : patterns) {
            if (!what.equals(pattern.getPrimaryOutput().what())) {
                continue;
            }
            if (pattern.getDefinition() instanceof AEItemKey item
                    && ChanceOutputMarker.isMarked(item.toStack())) {
                return true;
            }
        }
        return false;
    }
}
