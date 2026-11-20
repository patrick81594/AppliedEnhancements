package com.appliedenhancements.chance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.service.CraftingService;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.appliedenhancements.chance.AdvancedAeChanceOutputBonus;
import com.appliedenhancements.chance.AdvancedAeTopUp;
import com.appliedenhancements.chance.ChanceJobMarks;
import com.appliedenhancements.chance.ChanceRetryLink;
import com.appliedenhancements.chance.ChanceTopUpControl;
import com.appliedenhancements.chance.ChanceTopUpState;

/**
 * Chance-output support on AdvancedAE's quantum CPU: the chance half of what used to live in the AELIS mixin
 * {@code AdvancedAeCraftingCycleMixin} (same target class, {@code @Pseudo}, reflection into AdvancedAE internals
 * because AdvancedAE is not a compile dependency).
 *
 * <h2>Hook order</h2>
 * The AELIS mixin has priority 900, this one 910: applied right after it, before default-priority mixins, which is
 * the position the chance code had when it lived inside the AELIS mixin. As on the AE2 CPU, the {@code insert}
 * wrapper must be the OUTER one: its bonus calculation reads the waiting-for count before and after the inner
 * chain (AELIS retained-output wrapper, then AdvancedAE). See docs/MODULES.md.
 */
@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic", remap = false, priority = 910)
public abstract class AdvancedAeChanceMixin implements ChanceTopUpControl {
    @Shadow public abstract ListCraftingInventory getInventory();
    @Shadow public abstract boolean hasJob();
    @Shadow public abstract long getWaitingFor(AEKey key);
    @Shadow private void finishJob(boolean success) { throw new AssertionError(); }

    /** Middle-click retry: the separate job that helps this (source) job. Not saved. */
    @Unique private final ChanceRetryLink appliedenhancements$chanceRetry = new ChanceRetryLink();
    @Unique private final ChanceJobMarks appliedenhancements$chanceMarks = new ChanceJobMarks();
    @Unique private static final String APPLIEDENHANCEMENTS_CHANCE_MARKS_TAG = "appliedenhancementsChanceMarks";
    @Unique private final ChanceTopUpState<IPatternDetails> appliedenhancements$chanceTopUp = new ChanceTopUpState<>();

    @Override
    public Result appliedenhancements$requestChanceRetry(AEKey key) {
        return AdvancedAeChanceOutputBonus.requestRetry(
                this, appliedenhancements$chanceMarks, appliedenhancements$chanceRetry, key,
                key == null ? 0 : getWaitingFor(key));
    }

    /** Records pushes of marked patterns (wraps the same INVOKE as the AELIS mixin). */
    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"))
    private boolean appliedenhancements$chanceRecordPush(ICraftingProvider provider,
            IPatternDetails pattern, KeyCounter[] inputs, Operation<Boolean> original) {
        boolean accepted = original.call(provider, pattern, inputs);
        if (accepted && appliedenhancements$chanceMarks.recordIfMarked(pattern)) {
            appliedenhancements$chanceTopUp.onPush(pattern, pattern.getPrimaryOutput().what(),
                    AdvancedAeTopUp.now(this));
        }
        return accepted;
    }

    @WrapMethod(method = "insert")
    private long appliedenhancements$chanceOutput(AEKey key, long amount, Actionable mode,
            Operation<Long> original) {
        long waitBefore = key == null ? 0 : getWaitingFor(key);
        long accepted = original.call(key, amount, mode);
        // Chance outputs of marked patterns: extra items count toward the requested total.
        // The bonus counts toward the job but is not claimed: the caller stores the surplus in network storage.
        // What the CPU itself counted (see CraftingCpuLogicChanceMixin): the return value is 0 for a final output
        // whose requester took nothing, although the item was already counted against the job.
        long consumed = key == null ? 0
                : mode == Actionable.MODULATE ? Math.max(0, waitBefore - getWaitingFor(key))
                        : Math.min(amount, waitBefore);
        long bonus = AdvancedAeChanceOutputBonus.insertBonus(
                this, appliedenhancements$chanceMarks, getInventory(), key, amount, Math.max(accepted, consumed), mode,
                () -> finishJob(true));
        long delivered = Math.max(accepted, consumed) + bonus;
        if (delivered > 0 && mode == Actionable.MODULATE && key != null) {
            long now = AdvancedAeTopUp.now(this);
            if (appliedenhancements$chanceMarks.contains(key)) {
                appliedenhancements$chanceTopUp.onOutput(key, delivered, now);
            } else {
                appliedenhancements$chanceTopUp.touch(now);
            }
        }
        return accepted;
    }

    /** After the normal tick: start a chance top-up wave when a marked pattern's outputs are overdue. */
    @WrapMethod(method = "tickCraftingLogic")
    private void appliedenhancements$chanceTick(IEnergyService energyService, CraftingService craftingService,
            Operation<Void> original) {
        original.call(energyService, craftingService);
        AdvancedAeChanceOutputBonus.collectRetry(this, appliedenhancements$chanceRetry);
        AdvancedAeTopUp.tick(this, appliedenhancements$chanceMarks, appliedenhancements$chanceTopUp, getInventory());
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"))
    private void appliedenhancements$chanceWrite(ValueOutput output, CallbackInfo callback) {
        if (hasJob() && !appliedenhancements$chanceMarks.isEmpty()) {
            output.store(APPLIEDENHANCEMENTS_CHANCE_MARKS_TAG, AEKey.CODEC.listOf(),
                    appliedenhancements$chanceMarks.toList());
        }
    }

    @Inject(method = "readFromNBT", at = @At("RETURN"))
    private void appliedenhancements$chanceRead(ValueInput input, CallbackInfo callback) {
        appliedenhancements$chanceMarks.load(!hasJob() ? java.util.List.of()
                : input.read(APPLIEDENHANCEMENTS_CHANCE_MARKS_TAG, AEKey.CODEC.listOf())
                        .orElse(java.util.List.of()));
    }

    @Inject(method = "finishJob", at = @At("TAIL"))
    private void appliedenhancements$chanceFinish(boolean success, CallbackInfo callback) {
        appliedenhancements$chanceMarks.clear();
        appliedenhancements$chanceTopUp.clear();
        appliedenhancements$chanceRetry.cancel();
    }
}
