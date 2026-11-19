package com.appliedenhancements.packtests.mixin;

import net.neoforged.neoforge.gametest.GameTestHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * TEST-ONLY. NeoForge 26 turns game tests off on every production server (GameTestHooks.isGametestEnabled
 * returns false when FMLEnvironment.isProduction(), before it even reads -Dneoforge.enableGameTest). That gate
 * also stops RegisterGameTestsEvent (so AE2 cannot register its test plots) and the GameTestTicker. The pack-test
 * server sets -Dappliedenhancements.packtests=true; only then are game tests enabled. Without the flag this does
 * nothing.
 */
@Mixin(value = GameTestHooks.class, remap = false)
public abstract class GameTestHooksMixin {
    @Inject(method = "isGametestEnabled", at = @At("HEAD"), cancellable = true)
    private static void appliedenhancementsPacktests$enableOnPackTestServer(CallbackInfoReturnable<Boolean> callback) {
        if (Boolean.getBoolean("appliedenhancements.packtests")) {
            callback.setReturnValue(true);
        }
    }
}
