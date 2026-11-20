package com.appliedenhancements.chance.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.crafting.execution.ElapsedTimeTracker;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;

/**
 * The chance module's own view of AE2's {@code ExecutingCraftingJob}. It deliberately does not reuse the
 * AELIS accessor {@code ExecutingCraftingJobCycleAccessor}: the method names differ ({@code appliedenhancements$chance...})
 * so both accessor Mixins can target the same class, and this module can be moved to another mod without the
 * AELIS code.
 */
@Mixin(value = ExecutingCraftingJob.class, remap = false)
public interface ChanceJobAccessor {
    @Accessor("tasks")
    Map<IPatternDetails, ?> appliedenhancements$chanceTasks();

    @Accessor("waitingFor")
    ListCraftingInventory appliedenhancements$chanceWaitingFor();

    @Accessor("finalOutput")
    GenericStack appliedenhancements$chanceFinalOutput();

    @Accessor("remainingAmount")
    long appliedenhancements$chanceRemainingAmount();

    @Accessor("remainingAmount")
    void appliedenhancements$chanceSetRemainingAmount(long remainingAmount);

    @Accessor("timeTracker")
    ElapsedTimeTracker appliedenhancements$chanceTimeTracker();
}
