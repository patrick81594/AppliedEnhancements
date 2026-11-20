package com.appliedenhancements.matrixfix.mixin;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.TickRateModulation;
import com.appliedenhancements.Config;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Workaround for an ExtendedAE (26.1-1.0.4) Assembler Matrix bug.
 *
 * <p>
 * A matrix crafter hands its finished outputs to the network from {@code tickingRequest}. When the network accepts
 * less than was offered (full storage, or a crafting job that has already counted the items), the crafter sets
 * {@code blockCoolDown = 100} and skips every flush while that value is above zero. Nothing ever decrements it, so
 * after ONE partial hand-over the crafter never delivers an output again until the world is reloaded: crafting jobs
 * wait for items that sit in the crafter's buffer and the matrix looks busy forever.
 *
 * <p>
 * We count the cooldown down by the elapsed ticks at the start of every tick. If ExtendedAE fixes this itself the
 * value is already 0 or counted down by them, and this Mixin only makes it count slightly faster (harmless).
 */
@Pseudo
@Mixin(targets = "com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixCrafter", remap = false)
public abstract class AssemblerMatrixCrafterCooldownMixin {
    @Shadow(remap = false)
    private int blockCoolDown;

    @Inject(method = "tickingRequest", at = @At("HEAD"), require = 0)
    private void appliedenhancements$countDownFlushCooldown(IGridNode node, int ticksSinceLastCall,
            CallbackInfoReturnable<TickRateModulation> callback) {
        if (this.blockCoolDown > 0 && Config.FIX_MATRIX_CRAFTER_COOLDOWN.get()) {
            this.blockCoolDown = Math.max(0, this.blockCoolDown - Math.max(1, ticksSinceLastCall));
        }
    }
}
