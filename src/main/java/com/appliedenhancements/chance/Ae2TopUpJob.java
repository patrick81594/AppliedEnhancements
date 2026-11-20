package com.appliedenhancements.chance;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.crafting.inv.ListCraftingInventory;
import com.appliedenhancements.chance.mixin.ChanceJobAccessor;
import com.appliedenhancements.chance.mixin.ChanceTimeTrackerAccessor;

/** {@link TopUpJob} over AE2's ExecutingCraftingJob (reached through the accessor Mixins). */
public final class Ae2TopUpJob implements TopUpJob {
    private static final String PROGRESS_CLASS = "appeng.crafting.execution.ExecutingCraftingJob$TaskProgress";

    private final ChanceJobAccessor job;
    private final ListCraftingInventory inventory;

    public Ae2TopUpJob(Object executingJob, ListCraftingInventory inventory) {
        this.job = (ChanceJobAccessor) executingJob;
        this.inventory = inventory;
    }

    @Override
    public AEKey finalKey() {
        var output = job.appliedenhancements$chanceFinalOutput();
        return output == null ? null : output.what();
    }

    @Override
    public long remaining() {
        return job.appliedenhancements$chanceRemainingAmount();
    }

    @Override
    public boolean tasksEmpty() {
        return job.appliedenhancements$chanceTasks().isEmpty();
    }

    @Override
    public boolean hasTask(IPatternDetails pattern) {
        return job.appliedenhancements$chanceTasks().containsKey(pattern);
    }

    @Override
    public java.util.Set<AEKey> waitingKeys() {
        return new java.util.HashSet<>(job.appliedenhancements$chanceWaitingFor().list.keySet());
    }

    @Override
    public long waitingFor(AEKey key) {
        return job.appliedenhancements$chanceWaitingFor().list.get(key);
    }

    @Override
    public ListCraftingInventory inventory() {
        return inventory;
    }

    @Override
    public long dropWaitingFor(AEKey key) {
        long dropped = job.appliedenhancements$chanceWaitingFor().extract(key, Long.MAX_VALUE, Actionable.MODULATE);
        if (dropped > 0) {
            // The dropped outputs were never made: take them off the job's total work. (decrementItems would count
            // them as COMPLETED work, and the CPU would show "Crafted 100%" while the job still needs items.)
            var started = ((ChanceTimeTrackerAccessor) job.appliedenhancements$chanceTimeTracker())
                    .appliedenhancements$chanceStartedWorkByType();
            started.put(key.getType(), Math.max(0, started.getLong(key.getType()) - dropped));
        }
        return dropped;
    }

    @Override
    public void addCrafts(IPatternDetails pattern, long crafts) {
        try {
            JobTaskProgress.add(job.appliedenhancements$chanceTasks(), pattern, crafts, PROGRESS_CLASS,
                    ChanceJobAccessor.class.getClassLoader());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot add top-up crafts to the AE2 job", e);
        }
        var output = pattern.getPrimaryOutput();
        ((ChanceTimeTrackerAccessor) job.appliedenhancements$chanceTimeTracker())
                .appliedenhancements$chanceAddMaxItems(output.amount() * crafts, output.what().getType());
    }
}
