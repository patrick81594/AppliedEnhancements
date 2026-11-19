package com.appliedenhancements.runtime;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.crafting.inv.ListCraftingInventory;
import com.appliedenhancements.mixin.ElapsedTimeTrackerAccessor;
import com.appliedenhancements.mixin.ExecutingCraftingJobCycleAccessor;

/** {@link TopUpJob} over AE2's ExecutingCraftingJob (reached through the accessor Mixins). */
public final class Ae2TopUpJob implements TopUpJob {
    private static final String PROGRESS_CLASS = "appeng.crafting.execution.ExecutingCraftingJob$TaskProgress";

    private final ExecutingCraftingJobCycleAccessor job;
    private final ListCraftingInventory inventory;

    public Ae2TopUpJob(Object executingJob, ListCraftingInventory inventory) {
        this.job = (ExecutingCraftingJobCycleAccessor) executingJob;
        this.inventory = inventory;
    }

    @Override
    public AEKey finalKey() {
        var output = job.appliedenhancements$getFinalOutput();
        return output == null ? null : output.what();
    }

    @Override
    public long remaining() {
        return job.appliedenhancements$getRemainingAmount();
    }

    @Override
    public boolean tasksEmpty() {
        return job.appliedenhancements$getTasks().isEmpty();
    }

    @Override
    public ListCraftingInventory inventory() {
        return inventory;
    }

    @Override
    public long dropWaitingFor(AEKey key) {
        long dropped = job.appliedenhancements$getWaitingFor().extract(key, Long.MAX_VALUE, Actionable.MODULATE);
        if (dropped > 0) {
            ((ElapsedTimeTrackerAccessor) job.appliedenhancements$getTimeTracker())
                    .appliedenhancements$decrementItems(dropped, key.getType());
        }
        return dropped;
    }

    @Override
    public void addCrafts(IPatternDetails pattern, long crafts) {
        try {
            JobTaskProgress.add(job.appliedenhancements$getTasks(), pattern, crafts, PROGRESS_CLASS,
                    ExecutingCraftingJobCycleAccessor.class.getClassLoader());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot add top-up crafts to the AE2 job", e);
        }
        var output = pattern.getPrimaryOutput();
        ((ElapsedTimeTrackerAccessor) job.appliedenhancements$getTimeTracker())
                .appliedenhancements$addMaxItems(output.amount() * crafts, output.what().getType());
    }
}
