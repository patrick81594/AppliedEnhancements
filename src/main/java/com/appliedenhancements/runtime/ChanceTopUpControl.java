package com.appliedenhancements.runtime;

import appeng.api.stacks.AEKey;

/** Implemented by the crafting CPU logic: lets a player restart the top-up of a stalled chance output. */
public interface ChanceTopUpControl {
    enum Result { STARTED, NO_JOB, NOT_MARKED, ALREADY_RUNNING }

    /**
     * Starts a separate retry job for the amount of {@code key} this job still waits for. The retry job is
     * cancelled automatically when this (source) job ends, so it never outlives the craft it is helping.
     * Only the job's own requested output, and only when it is marked as a chance output, qualifies.
     */
    Result appliedenhancements$requestChanceRetry(AEKey key);
}
