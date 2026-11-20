package com.appliedenhancements.chance;

import appeng.api.stacks.AEKey;

/** Implemented by the crafting CPU logic: lets a player restart the top-up of a stalled chance output. */
public interface ChanceTopUpControl {
    enum Result { STARTED, NO_JOB, NOT_MARKED, NOT_WAITING, ALREADY_RUNNING }

    /**
     * Starts a separate retry job for the amount of {@code key} this job still waits for. The retry job is
     * cancelled automatically when this (source) job ends, so it never outlives the craft it is helping.
     * Any item of a chance-marked pattern that the job is still waiting for qualifies: the job's own requested
     * output (outstanding = what the job still needs) or an intermediate of a larger craft (outstanding = what
     * the job still waits for). The retry job's results are delivered to whichever job waits first, so they
     * also fill the stalled intermediate.
     */
    Result appliedenhancements$requestChanceRetry(AEKey key);
}
