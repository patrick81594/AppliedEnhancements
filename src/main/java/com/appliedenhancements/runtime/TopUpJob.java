package com.appliedenhancements.runtime;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.crafting.inv.ListCraftingInventory;

/** What the top-up runner needs from a running crafting job; implemented per CPU type (AE2, AdvancedAE). */
public interface TopUpJob {
    /** The job's requested output. */
    AEKey finalKey();

    /** How many items of the requested output the job still needs. */
    long remaining();

    /** True when every craft of the plan has been pushed. */
    boolean tasksEmpty();

    /** The CPU's own item store; top-up inputs go here so the normal push path can use them. */
    ListCraftingInventory inventory();

    /** Forgets outputs the job still waits for but that will not come; returns the dropped amount. */
    long dropWaitingFor(AEKey key);

    /** Schedules {@code crafts} more pushes of {@code pattern}. */
    void addCrafts(IPatternDetails pattern, long crafts);
}
