package com.appliedenhancements.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import com.appliedenhancements.runtime.DiagLog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** TEMPORARY read-only diagnostics (2026-11-18): why does the Assembler Matrix refuse pushes? */
@Pseudo
@Mixin(targets = "com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern", remap = false)
public abstract class AssemblerMatrixPatternDiagMixin {
    @Inject(method = "pushPattern", at = @At("RETURN"), require = 0)
    private void appliedenhancements$diagPush(IPatternDetails details, KeyCounter[] inputs,
            CallbackInfoReturnable<Boolean> callback) {
        Object self = this;
        Object patterns = DiagLog.probe(self, "patterns");
        Object contains = patterns instanceof List<?> list ? list.contains(details) : "?";
        DiagLog.log("matrix.push." + callback.getReturnValue() + "." + details.getPrimaryOutput().what(),
                "matrix pushPattern {} -> {} | formed={} patterns.contains={} patternClass={} patternsInCore={}",
                details.getPrimaryOutput().what(), callback.getReturnValue(),
                DiagLog.probe(self, "isFormed"), contains, details.getClass().getName(),
                patterns instanceof List<?> list ? list.size() : "?");
    }

    @Inject(method = "isBusy", at = @At("RETURN"), require = 0)
    private void appliedenhancements$diagBusy(CallbackInfoReturnable<Boolean> callback) {
        Object cluster = DiagLog.probe(this, "cluster");
        DiagLog.log("matrix.busy." + callback.getReturnValue(), "matrix isBusy -> {} | cluster={}",
                callback.getReturnValue(), cluster == null ? "null" : "present");
        if (Boolean.TRUE.equals(callback.getReturnValue()) && cluster != null && DiagLog.every("matrix.dump", 60_000)) {
            DiagLog.dumpMatrixThreads(cluster);
        }
    }
}
