package com.appliedenhancements.runtime;

import appeng.api.stacks.AEKey;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-job bookkeeping for top-up waves: crafts pushed per marked pattern, outputs that arrived per key,
 * and when anything last happened. Not saved: after a reload the idle clock simply restarts.
 *
 * @param <P> the pattern type (IPatternDetails in the CPU, anything in tests)
 */
public final class ChanceTopUpState<P> {
    private static final class Wave {
        long crafts;
        long planned;
        long extra;
    }

    private final Map<P, Wave> waves = new HashMap<>();
    private final Map<AEKey, P> producers = new HashMap<>();
    private final Map<AEKey, Long> outputs = new HashMap<>();
    private long lastActivity = -1;

    public long lastActivity() {
        return lastActivity;
    }

    public void touch(long now) {
        lastActivity = now;
    }

    public void onPush(P pattern, AEKey outputKey, long now) {
        waves.computeIfAbsent(pattern, p -> new Wave()).crafts++;
        producers.put(outputKey, pattern);
        lastActivity = now;
    }

    public void onOutput(AEKey key, long amount, long now) {
        outputs.merge(key, amount, Long::sum);
        lastActivity = now;
    }

    public P producerOf(AEKey key) {
        return producers.get(key);
    }

    /**
     * Ends the current wave of {@code pattern}/{@code key}: returns {outputs, crafts} seen in it and starts a
     * new wave. The first wave also fixes the pattern's planned craft count (the base of the top-up limit).
     */
    public long[] endWave(P pattern, AEKey key) {
        var wave = waves.computeIfAbsent(pattern, p -> new Wave());
        long[] seen = {outputs.getOrDefault(key, 0L), wave.crafts};
        if (wave.planned == 0) {
            wave.planned = wave.crafts;
        }
        wave.crafts = 0;
        // Outputs that arrive while no craft of this wave exists (late deliveries of an earlier wave) stay
        // counted until crafts exist to pair them with, so they are never lost to the learned yield.
        if (seen[1] > 0) {
            outputs.remove(key);
        }
        return seen;
    }

    /** Top-up crafts this pattern may still add: {@code factor} times its planned crafts minus those added. */
    public long allowedExtra(P pattern, long factor) {
        var wave = waves.get(pattern);
        return wave == null ? 0 : Math.max(0, factor * wave.planned - wave.extra);
    }

    /** Sets the planned craft count when it is unknown (e.g. after a reload lost the wave bookkeeping). */
    public void seedPlannedIfUnknown(P pattern, long crafts) {
        var wave = waves.computeIfAbsent(pattern, p -> new Wave());
        if (wave.planned == 0) {
            wave.planned = Math.max(crafts, 1);
        }
    }

    public void addExtra(P pattern, long crafts) {
        waves.computeIfAbsent(pattern, p -> new Wave()).extra += crafts;
    }

    public void clear() {
        waves.clear();
        producers.clear();
        outputs.clear();
        lastActivity = -1;
    }
}
