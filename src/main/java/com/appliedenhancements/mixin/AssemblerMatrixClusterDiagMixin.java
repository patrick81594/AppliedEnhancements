package com.appliedenhancements.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import com.appliedenhancements.runtime.DiagLog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

/** TEMPORARY read-only diagnostics (2026-11-18): does the matrix cluster find a free crafter? */
@Pseudo
@Mixin(targets = "com.glodblock.github.extendedae.common.me.matrix.ClusterAssemblerMatrix", remap = false)
public abstract class AssemblerMatrixClusterDiagMixin {
    @Inject(method = "pushCraftingJob", at = @At("RETURN"), require = 0)
    private void appliedenhancements$diagClusterPush(IPatternDetails details, KeyCounter[] inputs,
            CallbackInfoReturnable<Boolean> callback) {
        Object available = DiagLog.probe(this, "availableCrafters");
        DiagLog.log("matrix.cluster." + callback.getReturnValue(),
                "matrix cluster pushCraftingJob {} -> {} | availableCrafters={}",
                details.getPrimaryOutput().what(), callback.getReturnValue(),
                available instanceof Collection<?> c ? c.size() : "?");
    }
}
