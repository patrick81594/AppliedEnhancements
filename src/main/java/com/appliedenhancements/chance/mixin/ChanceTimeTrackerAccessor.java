package com.appliedenhancements.chance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import appeng.api.stacks.AEKeyType;
import appeng.crafting.execution.ElapsedTimeTracker;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;

/** The chance module's own accessor for AE2's {@code ElapsedTimeTracker} (see {@link ChanceJobAccessor}). */
@Mixin(value = ElapsedTimeTracker.class, remap = false)
public interface ChanceTimeTrackerAccessor {
    @Accessor("startedWorkByType")
    Reference2LongMap<AEKeyType> appliedenhancements$chanceStartedWorkByType();

    @Invoker("decrementItems")
    void appliedenhancements$chanceDecrementItems(long amount, AEKeyType keyType);

    @Invoker("addMaxItems")
    void appliedenhancements$chanceAddMaxItems(long amount, AEKeyType keyType);
}
