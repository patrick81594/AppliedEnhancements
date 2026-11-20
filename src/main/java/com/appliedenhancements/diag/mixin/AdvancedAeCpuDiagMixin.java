package com.appliedenhancements.diag.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.service.CraftingService;
import net.minecraft.world.level.Level;

import com.appliedenhancements.diag.DiagLog;

/**
 * Diagnostics observer on AdvancedAE's quantum CPU: the {@code [ACE/diag] quantum cpu ...} counterpart of
 * {@link CraftingCpuLogicDiagMixin}. {@code @Pseudo}, every injector {@code require = 0}, reflection only (the quantum
 * CPU's level is reached through {@link DiagLog#quantumGameTime(Object)}). Priority 920: after the AELIS mixin (900)
 * and the chance mixin (910), so the wrappers are the outer ones.
 */
@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic", remap = false, priority = 920)
public abstract class AdvancedAeCpuDiagMixin {
    @Shadow public abstract ListCraftingInventory getInventory();
    @Shadow public abstract GenericStack getFinalJobOutput();
    @Shadow public abstract boolean hasJob();
    @Shadow public abstract long getWaitingFor(AEKey key);

    /** Last push/insert game time (stall detection). */
    @Unique private long appliedenhancements$diagActivity = -1;

    @WrapOperation(method = "executeCrafting", require = 0, at = @At(value = "INVOKE",
            target = "Lappeng/crafting/execution/CraftingCpuHelper;extractPatternInputs(Lappeng/api/crafting/IPatternDetails;Lappeng/crafting/inv/ICraftingInventory;Lnet/minecraft/world/level/Level;Lappeng/api/stacks/KeyCounter;Lappeng/api/stacks/KeyCounter;)[Lappeng/api/stacks/KeyCounter;"))
    private KeyCounter[] appliedenhancements$diagExtract(IPatternDetails pattern, ICraftingInventory inventory,
            Level level, KeyCounter outputs, KeyCounter remainders, Operation<KeyCounter[]> original) {
        var extracted = original.call(pattern, inventory, level, outputs, remainders);
        if (extracted == null && DiagLog.enabled()) {
            DiagLog.log("quantum.extract.null." + pattern.getPrimaryOutput().what(),
                    "quantum cpu inputs {}: extractPatternInputs returned null (inputs not in CPU or cycle guard returned "
                            + "no inventory) patternClass={}",
                    pattern.getPrimaryOutput().what(), pattern.getClass().getName());
        }
        return extracted;
    }

    /** Which providers does the quantum CPU see? */
    @WrapOperation(method = "executeCrafting", require = 0, at = @At(value = "INVOKE",
            target = "Lappeng/me/service/CraftingService;getProviders(Lappeng/api/crafting/IPatternDetails;)Ljava/lang/Iterable;"))
    private Iterable<ICraftingProvider> appliedenhancements$diagProviders(CraftingService service,
            IPatternDetails pattern, Operation<Iterable<ICraftingProvider>> original) {
        var providers = original.call(service, pattern);
        if (!DiagLog.enabled()) {
            return providers;
        }
        var names = new java.util.ArrayList<String>();
        for (var provider : providers) {
            names.add(provider.getClass().getSimpleName()
                    + (DiagLog.isMatrix(provider) ? "(busy=" + provider.isBusy() + ")" : ""));
        }
        DiagLog.log("quantum.providers." + pattern.getPrimaryOutput().what() + "." + names.size(),
                "quantum cpu getProviders {} -> {} provider(s) {} patternClass={}", pattern.getPrimaryOutput().what(),
                names.size(), names, pattern.getClass().getName());
        return providers;
    }

    @WrapOperation(method = "executeCrafting", require = 0, at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"))
    private boolean appliedenhancements$diagPush(ICraftingProvider provider, IPatternDetails pattern,
            KeyCounter[] inputs, Operation<Boolean> original) {
        boolean accepted = original.call(provider, pattern, inputs);
        if (DiagLog.enabled()) {
            if (DiagLog.isMatrix(provider) || !accepted) {
                DiagLog.log("quantum.push." + accepted + "." + provider.getClass().getName() + "." + pattern.getPrimaryOutput().what(),
                        "quantum cpu pushPattern {} to {} -> {}", pattern.getPrimaryOutput().what(),
                        provider.getClass().getName(), accepted);
            }
            if (accepted) {
                appliedenhancements$diagActivity = DiagLog.quantumGameTime(this);
            }
        }
        return accepted;
    }

    @WrapMethod(method = "insert", require = 0)
    private long appliedenhancements$diagInsert(AEKey key, long amount, Actionable mode, Operation<Long> original) {
        if (!DiagLog.enabled() || key == null || mode != Actionable.MODULATE) {
            return original.call(key, amount, mode);
        }
        long waitBefore = getWaitingFor(key);
        long accepted = original.call(key, amount, mode);
        long consumed = Math.max(0, waitBefore - getWaitingFor(key));
        if (accepted < Math.min(amount, waitBefore)
                && !(getFinalJobOutput() != null && key.matches(getFinalJobOutput()))) {
            DiagLog.log("quantum.refuse." + key, "quantum cpu insert {}: offered {} accepted {} while waiting for {}",
                    key, amount, accepted, waitBefore);
        }
        if (Math.max(accepted, consumed) > 0) {
            appliedenhancements$diagActivity = DiagLog.quantumGameTime(this);
        }
        return accepted;
    }

    /** Stalled jobs: nothing pushed or received for a minute while the job still waits for something. */
    @WrapMethod(method = "tickCraftingLogic", require = 0)
    private void appliedenhancements$diagTick(IEnergyService energyService, CraftingService craftingService,
            Operation<Void> original) {
        original.call(energyService, craftingService);
        if (!DiagLog.enabled() || !hasJob()) {
            return;
        }
        long now = DiagLog.quantumGameTime(this);
        if (now >= 0 && appliedenhancements$diagActivity < 0) {
            appliedenhancements$diagActivity = now;
        } else if (now >= 0 && now - appliedenhancements$diagActivity >= 1200 && DiagLog.every("cpu.stall.quantum", 30000)) {
            var text = DiagLog.jobText(DiagLog.probe(this, "job"));
            if (text != null) {
                DiagLog.log("cpu.stall.quantum.log", "quantum cpu job STALLED {}s: {} cpuInventory={}",
                        (now - appliedenhancements$diagActivity) / 20, text, DiagLog.counterText(getInventory().list));
            }
        }
    }
}
