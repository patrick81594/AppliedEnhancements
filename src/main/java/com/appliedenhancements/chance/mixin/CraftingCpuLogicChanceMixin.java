package com.appliedenhancements.chance.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
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
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.appliedenhancements.Config;
import com.appliedenhancements.chance.Ae2TopUpJob;
import com.appliedenhancements.chance.ChanceJobMarks;
import com.appliedenhancements.chance.ChanceLog;
import com.appliedenhancements.chance.ChanceOutputBonus;
import com.appliedenhancements.chance.ChanceRetryLink;
import com.appliedenhancements.chance.ChanceTopUpControl;
import com.appliedenhancements.chance.ChanceTopUpRunner;
import com.appliedenhancements.chance.ChanceTopUpState;

/**
 * Chance-output support on AE2's crafting CPU (bonus counting, top-up waves, middle-click retry). This is the
 * chance half of what used to live in the AELIS mixin {@code CraftingCpuLogicBatchMixin}; it targets the same class.
 *
 * <h2>Hook order</h2>
 * Priority 1110 puts this mixin right AFTER the AELIS mixin (1100) and still before everything above 1110.
 * That matters for exactly one hook: {@code insert}. Both mixins wrap it ({@code @WrapMethod}); a later-applied
 * wrapper is the outer one, so the chance wrapper sees the whole inner chain (AELIS retained-output wrapper, then AE2).
 * The chance bonus is computed from the waiting-for count BEFORE and AFTER the call, so it must be outside the AELIS
 * wrapper, which moves retained cycle output out of the waiting-for list itself.
 * {@code pushPattern} (one more wrap of the same INVOKE) and the NBT/finishJob hooks do not depend on the order.
 * See docs/MODULES.md.
 */
@Mixin(value = CraftingCpuLogic.class, remap = false, priority = 1110)
public abstract class CraftingCpuLogicChanceMixin implements ChanceTopUpControl {
    @Shadow
    private ExecutingCraftingJob job;

    @Shadow
    @Final
    private ListCraftingInventory inventory;

    @Shadow
    @Final
    private CraftingCPUCluster cluster;

    @Shadow
    private void finishJob(boolean success) { throw new AssertionError(); }

    @Shadow
    public abstract long getWaitingFor(AEKey what);

    /** Middle-click retry: the separate job that helps this (source) job. Not saved. */
    @Unique private final ChanceRetryLink appliedenhancements$chanceRetry = new ChanceRetryLink();

    /** Marked (chance-output) patterns pushed in this job; see ChanceJobMarks. */
    @Unique private final ChanceJobMarks appliedenhancements$chanceMarks = new ChanceJobMarks();

    @Unique private static final String APPLIEDENHANCEMENTS_CHANCE_MARKS_TAG = "appliedenhancementsChanceMarks";

    /** Top-up wave bookkeeping of this job (not saved). */
    @Unique private final ChanceTopUpState<IPatternDetails> appliedenhancements$chanceTopUp = new ChanceTopUpState<>();

    @Unique
    private long appliedenhancements$chanceNow() {
        var level = cluster.getLevel();
        return level == null ? -1 : level.getGameTime();
    }

    /** Records pushes of marked patterns (the job's {@code pushPattern} call; wraps the same INVOKE as the AELIS mixin). */
    @WrapOperation(method = "executeCrafting", require = 0, at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"))
    private boolean appliedenhancements$chanceRecordPush(ICraftingProvider provider, IPatternDetails details,
            KeyCounter[] inputHolder, Operation<Boolean> original) {
        boolean pushed = original.call(provider, details, inputHolder);
        if (pushed && appliedenhancements$chanceMarks.recordIfMarked(details)) {
            appliedenhancements$chanceTopUp.onPush(details, details.getPrimaryOutput().what(),
                    appliedenhancements$chanceNow());
        }
        return pushed;
    }

    /**
     * Chance-output bonus (step 2 of chance support). AE2 accepts only the items the job
     * expects, so an extra item of the final output is refused. When the config is on and a
     * marked pattern produces that output, the refused extra counts toward the requested total,
     * capped at what the job still needs. The job finishes when the total is reached.
     */
    @WrapMethod(method = "insert")
    private long appliedenhancements$chanceOutputBonus(
            AEKey what,
            long amount,
            Actionable mode,
            Operation<Long> original) {
        long waitBefore = what == null ? 0 : getWaitingFor(what);
        long accepted = original.call(what, amount, mode);
        // The bonus counts toward the job but is NOT claimed: the caller stores the surplus in network storage,
        // exactly as AE2 does with a job's final output (it used to sit in the CPU until the job ended).
        // What AE2 itself counted for this insert. NOT the return value: for the job's final output AE2 returns what
        // the requester took (0 for a player's job) although it has already counted the item against the job, so
        // using the return value would count the same item a second time as a "bonus" and end the job short.
        long consumed = what == null ? 0
                : mode == Actionable.MODULATE ? Math.max(0, waitBefore - getWaitingFor(what))
                        : Math.min(amount, waitBefore);
        long total = consumed + appliedenhancements$chanceBonusFor(what, amount, consumed, mode);
        if (total > 0 && mode == Actionable.MODULATE && what != null) {
            long now = appliedenhancements$chanceNow();
            if (appliedenhancements$chanceMarks.contains(what)) {
                appliedenhancements$chanceTopUp.onOutput(what, total, now);
            } else {
                appliedenhancements$chanceTopUp.touch(now);
            }
        }
        return accepted;
    }

    /** The bonus part of {@link #appliedenhancements$chanceOutputBonus}: 0 when nothing applies. */
    @Unique
    private long appliedenhancements$chanceBonusFor(AEKey what, long amount, long accepted, Actionable mode) {
        if (what == null || job == null || amount <= accepted || !Config.ENABLE_CHANCE_OUTPUT_BONUS.get()) {
            return 0;
        }
        var jobAccess = (ChanceJobAccessor) job;
        var finalOutput = jobAccess.appliedenhancements$chanceFinalOutput();
        if (finalOutput == null || !what.matches(finalOutput)) {
            return 0;
        }
        if (!appliedenhancements$chanceMarks.contains(what)
                && !ChanceOutputBonus.hasMarkedProducer(jobAccess.appliedenhancements$chanceTasks().keySet(), what)) {
            return 0;
        }
        long remaining = jobAccess.appliedenhancements$chanceRemainingAmount();
        long bonus = ChanceOutputBonus.bonusToAccept(amount - accepted, remaining);
        if (bonus <= 0 || mode == Actionable.SIMULATE) {
            return Math.max(bonus, 0);
        }
        ((ChanceTimeTrackerAccessor) jobAccess.appliedenhancements$chanceTimeTracker())
                .appliedenhancements$chanceDecrementItems(bonus, what.getType());
        jobAccess.appliedenhancements$chanceSetRemainingAmount(remaining - bonus);
        cluster.markDirty();
        if (remaining - bonus <= 0) {
            finishJob(true);
        }
        return bonus;
    }

    @Override
    public Result appliedenhancements$requestChanceRetry(AEKey key) {
        if (job == null) {
            ChanceLog.info("Chance retry (AE2 CPU {}): NO_JOB, this CPU's job is null", cluster);
            return Result.NO_JOB;
        }
        if (key == null || !appliedenhancements$chanceMarks.contains(key)) {
            return Result.NOT_MARKED;
        }
        if (appliedenhancements$chanceRetry.active()) {
            return Result.ALREADY_RUNNING;
        }
        var jobAccess = (ChanceJobAccessor) job;
        var output = jobAccess.appliedenhancements$chanceFinalOutput();
        long outstanding = output != null && key.matches(output)
                ? jobAccess.appliedenhancements$chanceRemainingAmount()
                : getWaitingFor(key);
        var grid = cluster.getGrid();
        if (grid == null) {
            ChanceLog.info("Chance retry (AE2 CPU {}): NO_JOB, the cluster has no grid", cluster);
            return Result.NO_JOB;
        }
        if (outstanding <= 0) {
            return Result.NOT_WAITING;
        }
        appliedenhancements$chanceRetry.start(grid, cluster.getLevel(), key, outstanding);
        return Result.STARTED;
    }

    /** After the normal tick: start a chance top-up wave when a marked pattern's outputs are overdue. */
    @WrapMethod(method = "tickCraftingLogic")
    private void appliedenhancements$chanceTopUp(
            IEnergyService energyService,
            CraftingService craftingService,
            Operation<Void> original) {
        original.call(energyService, craftingService);
        appliedenhancements$chanceRetry.collect(cluster.getGrid());
        if (job == null || appliedenhancements$chanceMarks.isEmpty()) {
            return;
        }
        var grid = cluster.getGrid();
        long now = appliedenhancements$chanceNow();
        if (grid == null || now < 0) {
            return;
        }
        if (appliedenhancements$chanceTopUp.lastActivity() < 0) {
            appliedenhancements$chanceTopUp.touch(now);
        }
        ChanceTopUpRunner.tick(new Ae2TopUpJob(job, inventory),
                appliedenhancements$chanceTopUp, appliedenhancements$chanceMarks, grid, now);
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"))
    private void appliedenhancements$chanceWrite(ValueOutput output, CallbackInfo callback) {
        if (job != null && !appliedenhancements$chanceMarks.isEmpty()) {
            output.store(APPLIEDENHANCEMENTS_CHANCE_MARKS_TAG, AEKey.CODEC.listOf(),
                    appliedenhancements$chanceMarks.toList());
        }
    }

    @Inject(method = "readFromNBT", at = @At("RETURN"))
    private void appliedenhancements$chanceRead(ValueInput input, CallbackInfo callback) {
        appliedenhancements$chanceMarks.load(job == null ? java.util.List.of()
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
