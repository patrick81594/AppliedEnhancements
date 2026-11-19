package com.appliedenhancements.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import com.appliedenhancements.runtime.DiagLog;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** TEMPORARY read-only diagnostics (2026-11-18): does a matrix crafter thread accept the job? */
@Pseudo
@Mixin(targets = "com.glodblock.github.extendedae.common.me.CraftingThread", remap = false)
public abstract class AssemblerMatrixThreadDiagMixin {
    @Inject(method = "acceptJob", at = @At("RETURN"), require = 0)
    private void appliedenhancements$diagAccept(IPatternDetails details, KeyCounter[] inputs, Direction side,
            CallbackInfoReturnable<Boolean> callback) {
        DiagLog.log("matrix.thread." + callback.getReturnValue(),
                "matrix crafter acceptJob {} -> {} | patternClass={} inputSlots={}",
                details.getPrimaryOutput().what(), callback.getReturnValue(),
                details.getClass().getName(), inputs == null ? "null" : inputs.length);
    }
}
