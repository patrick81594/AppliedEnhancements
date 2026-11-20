package com.appliedenhancements.mixin;

import appeng.api.networking.IGridNode;
import appeng.api.stacks.AEKey;
import com.appliedenhancements.runtime.DiagLog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * TEMPORARY read-only diagnostics (2026-11-19): the Assembler Matrix crafter's hand-over of finished items.
 * addToBuffer receives the output of a finished craft; the flush lambda inserts it into the network and returns how
 * much the network accepted.
 */
@Pseudo
@Mixin(targets = "com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixCrafter", remap = false)
public abstract class AssemblerMatrixBufferDiagMixin {
    @Inject(method = "addToBuffer", at = @At("HEAD"), require = 0)
    private void appliedenhancements$diagAdd(AEKey key, long amount, CallbackInfo callback) {
        DiagLog.log("matrix.buffer.add." + key, "matrix crafter addToBuffer {} x{}", key, amount);
    }

    @Inject(method = "lambda$tickingRequest$0", at = @At("RETURN"), require = 0)
    private void appliedenhancements$diagFlush(IGridNode node, AEKey key, Long amount,
            CallbackInfoReturnable<Long> callback) {
        DiagLog.log("matrix.buffer.flush." + key + "." + (callback.getReturnValue() >= amount),
                "matrix crafter flush {} offered {} network accepted {}", key, amount, callback.getReturnValue());
    }
}
