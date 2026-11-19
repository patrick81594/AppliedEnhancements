package com.appliedenhancements.runtime;

/** Pure arithmetic for chance-output top-up waves (see ChanceTopUpRunner). */
public final class ChanceTopUp {
    private ChanceTopUp() {
    }

    /**
     * Outputs per craft, from what was observed. Starts from a prior of one output per craft (the
     * pattern's promise) and moves toward the observed rate as crafts accumulate.
     */
    public static double estimateYield(long outputs, long crafts) {
        return (Math.max(outputs, 0) + 1.0) / (Math.max(crafts, 0) + 1.0);
    }

    /** Extra crafts needed to cover {@code shortfall} items at {@code yield} outputs per craft, within the limit. */
    public static long craftsNeeded(long shortfall, double yield, long extraAllowed) {
        if (shortfall <= 0 || extraAllowed <= 0 || yield <= 0) {
            return 0;
        }
        return Math.min(extraAllowed, (long) Math.ceil(shortfall / yield));
    }

    /** How many of {@code wanted} crafts the available stock can pay for (all inputs must be covered). */
    public static long affordableCrafts(long wanted, long[] perCraft, long[] available) {
        long result = Math.max(wanted, 0);
        for (int i = 0; i < perCraft.length; i++) {
            if (perCraft[i] > 0) {
                result = Math.min(result, Math.max(available[i], 0) / perCraft[i]);
            }
        }
        return result;
    }

    /** True once {@code idleTicks} have passed since the last activity (never before activity was recorded). */
    public static boolean isIdle(long now, long lastActivity, long idleTicks) {
        return lastActivity >= 0 && now - lastActivity >= idleTicks;
    }
}
