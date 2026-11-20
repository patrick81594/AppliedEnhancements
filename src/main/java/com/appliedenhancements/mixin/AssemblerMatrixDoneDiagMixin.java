package com.appliedenhancements.mixin;

import com.appliedenhancements.runtime.DiagLog;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * TEMPORARY read-only diagnostics (2026-11-19): where does a finished Assembler Matrix craft go? An empty
 * assemblePattern result would silently lose a craft; pushOut is the hand-over to the network.
 */
@Pseudo
@Mixin(targets = "com.glodblock.github.extendedae.common.me.CraftingMatrixThread", remap = false)
public abstract class AssemblerMatrixDoneDiagMixin {
    @Inject(method = "assemblePattern", at = @At("RETURN"), require = 0)
    private void appliedenhancements$diagAssemble(net.minecraft.world.item.crafting.CraftingInput input,
            CallbackInfoReturnable<ItemStack> callback) {
        DiagLog.log("matrix.assemble." + callback.getReturnValue().getItem(),
                "matrix thread assemblePattern -> {}", callback.getReturnValue());
    }

    @Inject(method = "pushOut", at = @At("HEAD"), require = 0)
    private void appliedenhancements$diagPushOut(ItemStack stack, CallbackInfo callback) {
        DiagLog.log("matrix.pushout." + stack.getItem(), "matrix thread pushOut {}", stack);
    }

    @Inject(method = "onCraftingDone", at = @At("RETURN"), require = 0)
    private void appliedenhancements$diagDone(CallbackInfoReturnable<?> callback) {
        DiagLog.log("matrix.done", "matrix thread onCraftingDone");
    }
}
