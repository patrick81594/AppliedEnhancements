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
    private java.util.concurrent.Future<?> pendingPlan;
    private AEKey pendingKey;
    private long pendingAmount;
    private long lastFollowUp = -1;
    private int emptyWaves;
    private boolean retryNow;

    /** Most doublings of the idle time: 2^5 = 32 times the configured value. */
    private static final int MAX_BACKOFF_STEPS = 5;

    /**
     * How many times the configured idle time applies right now: it doubles with every wave that saw no output
     * (a slow machine or a low chance), up to 32 times, and is back to 1 as soon as an output arrives.
     */
    public long idleMultiplier() {
        return 1L << Math.min(emptyWaves, MAX_BACKOFF_STEPS);
    }

    /** A crafting calculation for a missing input that is still running (null when none). */
    public java.util.concurrent.Future<?> pendingPlan() {
        return pendingPlan;
    }

    public AEKey pendingKey() {
        return pendingKey;
    }

    public long pendingAmount() {
        return pendingAmount;
    }

    public void setPending(java.util.concurrent.Future<?> plan, AEKey key, long amount, long now) {
        pendingPlan = plan;
        pendingKey = key;
        pendingAmount = amount;
        lastFollowUp = now;
    }

    public void clearPending() {
        pendingPlan = null;
        pendingKey = null;
        pendingAmount = 0;
    }

    /** True when no follow-up craft was requested within the last {@code minTicks}. */
    public boolean followUpAllowed(long now, long minTicks) {
        return pendingPlan == null && (lastFollowUp < 0 || now - lastFollowUp >= minTicks);
    }

    /**
     * The player asked to try again (middle click in the CPU screen): forget the "limit reached" bookkeeping
     * (top-up crafts already added, empty-wave back-off, follow-up pause) and start a wave on the next tick,
     * without waiting for the idle time.
     */
    public void requestRetry() {
        for (var wave : waves.values()) {
            wave.extra = 0;
        }
        emptyWaves = 0;
        lastFollowUp = -1;
        retryNow = true;
    }

    /** True once after {@link #requestRetry()}: the idle gate is skipped for exactly one wave. */
    public boolean consumeRetryNow() {
        boolean was = retryNow;
        retryNow = false;
        return was;
    }

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
        emptyWaves = 0;
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
        if (seen[0] == 0) {
            emptyWaves++;
        }
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
        emptyWaves = 0;
        retryNow = false;
        pendingPlan = null;
        pendingKey = null;
        pendingAmount = 0;
        lastFollowUp = -1;
    }
}
