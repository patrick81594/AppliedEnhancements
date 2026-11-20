package com.appliedenhancements.mixin;

import java.util.ArrayDeque;
import java.util.Optional;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.appliedenhancements.runtime.MolecularBalancedBatchScope;
import com.appliedenhancements.api.AelisCycleExecutionApi;
import com.appliedenhancements.api.AelisBatchExecutionContext;
import com.appliedenhancements.api.AelisCycleRuntimeController;
import com.appliedenhancements.runtime.AelisCycleDispatchScope;
import com.appliedenhancements.runtime.AelisCycleRuntimePreparation;
import com.appliedenhancements.runtime.DataEnergisticsOrderCompletion;
import com.appliedenhancements.runtime.CraftingAmountProjection;
import com.appliedenhancements.runtime.ChanceOutputBonus;
import com.appliedenhancements.Config;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Brackets all pushes to an opted-in provider during one CPU scheduling pass.
 */
// Apply before other default-priority CPU batch mixins.  Several integrations
// (including Mek Energistics/MMCR) wrap the same calls in executeCrafting; if
// they run first, their wrapper replaces the INVOKE that WrapOperation needs
// to find and Mixin aborts during startup.
@Mixin(value = CraftingCpuLogic.class, remap = false, priority = 1100)
public abstract class CraftingCpuLogicBatchMixin implements com.appliedenhancements.runtime.ChanceTopUpControl {
    @Shadow
    private ExecutingCraftingJob job;

    @Shadow
    @Final
    private ListCraftingInventory inventory;

    @Shadow
    @Final
    private CraftingCPUCluster cluster;

    @Shadow
    public abstract void cancel();

    @Shadow
    private void finishJob(boolean success) { throw new AssertionError(); }

    @Unique
    private static final ThreadLocal<ArrayDeque<MolecularBalancedBatchScope>>
            APPLIEDENHANCEMENTS_BATCH_SCOPES = ThreadLocal.withInitial(ArrayDeque::new);

    @Unique
    private static final String APPLIEDENHANCEMENTS_CYCLE_RUNTIME_TAG =
            "appliedenhancementsCycleRuntime";

    @Unique
    private AelisCycleRuntimeController appliedenhancements$cycleRuntime;

    @Unique private int appliedenhancements$dispatchDepth;

    /** Middle-click retry: the separate job that helps this (source) job. Not saved. */
    @Unique private final com.appliedenhancements.runtime.ChanceRetryLink appliedenhancements$retry =
            new com.appliedenhancements.runtime.ChanceRetryLink();

    /** Marked (chance-output) patterns pushed in this job; see ChanceJobMarks. */
    @Unique
    private final com.appliedenhancements.runtime.ChanceJobMarks appliedenhancements$chanceMarks =
            new com.appliedenhancements.runtime.ChanceJobMarks();

    @Unique
    private static final String APPLIEDENHANCEMENTS_CHANCE_MARKS_TAG = "appliedenhancementsChanceMarks";

    /** Top-up wave bookkeeping of this job (not saved). */
    @Unique
    private final com.appliedenhancements.runtime.ChanceTopUpState<IPatternDetails> appliedenhancements$topUp =
            new com.appliedenhancements.runtime.ChanceTopUpState<>();

    /** TEMPORARY diagnostics: last push/insert game time (stall detection). */
    @Unique
    private long appliedenhancements$diagActivity = -1;

    @Shadow
    public abstract long getWaitingFor(AEKey what);

    @Unique
    private void appliedenhancements$diagStall() {
        long now = appliedenhancements$now();
        if (now < 0 || appliedenhancements$diagActivity < 0) {
            appliedenhancements$diagActivity = now;
            return;
        }
        if (now - appliedenhancements$diagActivity < 1200 || !com.appliedenhancements.runtime.DiagLog.every("cpu.stall.ae2", 30000)) {
            return;
        }
        var access = (ExecutingCraftingJobCycleAccessor) job;
        var waiting = access.appliedenhancements$getWaitingFor();
        if (waiting.list.isEmpty()) {
            return;
        }
        com.appliedenhancements.runtime.DiagLog.log("cpu.stall.ae2.log",
                "cpu job STALLED {}s: final={} remaining={} waitingFor={} tasksLeft={} cpuInventory={}",
                (now - appliedenhancements$diagActivity) / 20, access.appliedenhancements$getFinalOutput(),
                access.appliedenhancements$getRemainingAmount(),
                com.appliedenhancements.runtime.DiagLog.counterText(waiting.list),
                access.appliedenhancements$getTasks().size(),
                com.appliedenhancements.runtime.DiagLog.counterText(inventory.list));
    }

    @Unique
    private long appliedenhancements$now() {
        var level = cluster.getLevel();
        return level == null ? -1 : level.getGameTime();
    }

    @ModifyVariable(method = "trySubmitJob", at = @At("HEAD"), argsOnly = true)
    private ICraftingPlan appliedenhancements$prepareCyclePlan(ICraftingPlan plan) {
        return AelisCycleExecutionApi.preparePlan(plan);
    }

    @Inject(method = "trySubmitJob", at = @At("RETURN"))
    private void appliedenhancements$startCycleRuntime(
            IGrid grid,
            ICraftingPlan plan,
            IActionSource source,
            ICraftingRequester requester,
            CallbackInfoReturnable<ICraftingSubmitResult> callback) {
        if (callback.getReturnValue() != null
                && callback.getReturnValue().successful()) {
            appliedenhancements$cycleRuntime = AelisCycleExecutionApi.getPlan(plan)
                    .map(AelisCycleRuntimeController::withCyclePhase)
                    .orElse(null);
        }
    }

    @WrapMethod(method = "executeCrafting")
    private int appliedenhancements$withBalancedBatchScope(int maxPatterns,
            CraftingService craftingService, IEnergyService energyService, Level level,
            Operation<Integer> original) {
        var scopes = APPLIEDENHANCEMENTS_BATCH_SCOPES.get();
        try (var cycleScope = AelisCycleDispatchScope.open(() -> appliedenhancements$cycleRuntime);
                var scope = new MolecularBalancedBatchScope()) {
            scopes.push(scope);
            try {
                int executed;
                appliedenhancements$dispatchDepth++;
                try {
                    executed = original.call(maxPatterns, craftingService, energyService, level);
                } finally {
                    appliedenhancements$dispatchDepth--;
                }
                appliedenhancements$finishReturnedCycle();
                return executed;
            } finally {
                MolecularBalancedBatchScope removed = scopes.pop();
                if (removed != scope) {
                    throw new IllegalStateException("Unbalanced crafting batch scope stack");
                }
                if (scopes.isEmpty()) {
                    APPLIEDENHANCEMENTS_BATCH_SCOPES.remove();
                }
            }
        }
    }

    @WrapOperation(method = "executeCrafting", require = 0, at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"))
    private boolean appliedenhancements$openProviderBatch(ICraftingProvider provider,
            IPatternDetails details, KeyCounter[] inputHolder, Operation<Boolean> original,
            @Local(ordinal = 0) KeyCounter expectedOutputs) {
        var scopes = APPLIEDENHANCEMENTS_BATCH_SCOPES.get();
        if (!scopes.isEmpty()) {
            scopes.peek().beginIfNeeded(provider, inputHolder);
        }
        var completionJob = job;
        var virtualCompletion = completionJob == null ? null : DataEnergisticsOrderCompletion.beforeNativePush(
                this, ((ExecutingCraftingJobCycleAccessor) completionJob).appliedenhancements$getFinalOutput(),
                expectedOutputs);
        AelisBatchExecutionContext.Dispatch dispatch;
        try {
            dispatch = AelisBatchExecutionContext.beginProviderDispatch(
                    appliedenhancements$cycleRuntime, details.getDefinition(), inputHolder);
        } catch (IllegalStateException invalidBatch) {
            com.appliedenhancements.runtime.AelisPlanningLog.warn("Rejected invalid AELIS provider batch: {}", invalidBatch.getMessage());
            return false;
        }
        try (dispatch) {
            boolean pushed = original.call(provider, details, inputHolder);
            if (com.appliedenhancements.runtime.DiagLog.isMatrix(provider) || !pushed) {
                com.appliedenhancements.runtime.DiagLog.log(
                        "cpu.push." + pushed + "." + provider.getClass().getName() + "." + details.getPrimaryOutput().what(),
                        "cpu pushPattern {} to {} -> {}", details.getPrimaryOutput().what(),
                        provider.getClass().getName(), pushed);
            }
            if (pushed) {
                dispatch.accepted();
                appliedenhancements$diagActivity = appliedenhancements$now();
                if (appliedenhancements$chanceMarks.recordIfMarked(details)) {
                    appliedenhancements$topUp.onPush(details, details.getPrimaryOutput().what(),
                            appliedenhancements$now());
                }
                if (job == completionJob && virtualCompletion != null) {
                    virtualCompletion.reconcileAcceptedPush();
                }
            }
            return pushed;
        }
    }

    /** TEMPORARY diagnostics (2026-11-18, Assembler Matrix): which providers does the CPU see per pattern? */
    @WrapOperation(method = "executeCrafting", require = 0, at = @At(value = "INVOKE",
            target = "Lappeng/me/service/CraftingService;getProviders(Lappeng/api/crafting/IPatternDetails;)Ljava/lang/Iterable;"))
    private Iterable<ICraftingProvider> appliedenhancements$diagProviders(CraftingService service,
            IPatternDetails details, Operation<Iterable<ICraftingProvider>> original) {
        var providers = original.call(service, details);
        if (!com.appliedenhancements.runtime.DiagLog.enabled()) {
            return providers;
        }
        var names = new java.util.ArrayList<String>();
        for (var provider : providers) {
            names.add(provider.getClass().getSimpleName()
                    + (com.appliedenhancements.runtime.DiagLog.isMatrix(provider) ? "(busy=" + provider.isBusy() + ")" : ""));
        }
        com.appliedenhancements.runtime.DiagLog.log("cpu.providers." + details.getPrimaryOutput().what() + "." + names.size(),
                "cpu getProviders {} -> {} provider(s) {} patternClass={}", details.getPrimaryOutput().what(),
                names.size(), names, details.getClass().getName());
        return providers;
    }

    @WrapOperation(method = "executeCrafting", require = 0, at = @At(value = "INVOKE",
            target = "Lappeng/crafting/execution/CraftingCpuHelper;extractPatternInputs(Lappeng/api/crafting/IPatternDetails;Lappeng/crafting/inv/ICraftingInventory;Lnet/minecraft/world/level/Level;Lappeng/api/stacks/KeyCounter;Lappeng/api/stacks/KeyCounter;)[Lappeng/api/stacks/KeyCounter;"))
    private KeyCounter[] appliedenhancements$guardCyclePatternInputs(
            IPatternDetails details,
            ICraftingInventory sourceInventory,
            Level level,
            KeyCounter expectedOutputs,
            KeyCounter expectedContainerItems,
            Operation<KeyCounter[]> original) {
        var guarded = AelisCycleExecutionApi.guardInputs(
                appliedenhancements$cycleRuntime, details.getDefinition(), sourceInventory);
        if (guarded == null) {
            com.appliedenhancements.runtime.DiagLog.log("cpu.guard.null." + details.getPrimaryOutput().what(),
                    "cpu inputs {}: cycle guard returned no inventory", details.getPrimaryOutput().what());
            return null;
        }
        KeyCounter[] extracted = original.call(
                details, guarded, level, expectedOutputs, expectedContainerItems);
        if (extracted == null) {
            com.appliedenhancements.runtime.DiagLog.log("cpu.extract.null." + details.getPrimaryOutput().what(),
                    "cpu inputs {}: AE2 extractPatternInputs returned null (inputs not in CPU) patternClass={}",
                    details.getPrimaryOutput().what(), details.getClass().getName());
        }
        if (extracted != null
                && job != null
                && !appliedenhancements$canTrackExpectedOutputs(
                        ((ExecutingCraftingJobCycleAccessor) job)
                                .appliedenhancements$getWaitingFor().list,
                        expectedOutputs,
                        expectedContainerItems)) {
            com.appliedenhancements.runtime.DiagLog.log("cpu.track." + details.getPrimaryOutput().what(),
                    "cpu inputs {}: expected outputs not trackable, inputs reinjected",
                    details.getPrimaryOutput().what());
            CraftingCpuHelper.reinjectPatternInputs(guarded, extracted);
            return null;
        }
        return extracted;
    }

    @Unique
    private static boolean appliedenhancements$canTrackExpectedOutputs(
            KeyCounter waitingFor, KeyCounter outputs, KeyCounter containers) {
        var additions = new KeyCounter();
        for (var entry : outputs) {
            additions.set(entry.getKey(), entry.getLongValue());
        }
        for (var entry : containers) {
            long current = additions.get(entry.getKey());
            if (current < 0 || entry.getLongValue() < 0
                    || current > Long.MAX_VALUE - entry.getLongValue()) {
                return false;
            }
            additions.set(entry.getKey(), current + entry.getLongValue());
        }
        for (var entry : additions) {
            long current = waitingFor.get(entry.getKey());
            if (current < 0 || entry.getLongValue() < 0
                    || current > Long.MAX_VALUE - entry.getLongValue()) {
                return false;
            }
        }
        return true;
    }

    @Inject(method = "getPendingOutputs", at = @At("HEAD"), cancellable = true)
    private void appliedenhancements$saturatePendingOutputs(
            AEKey template, CallbackInfoReturnable<Long> callback) {
        if (job == null) {
            callback.setReturnValue(0L);
            return;
        }
        long count = 0;
        for (var task : ((ExecutingCraftingJobCycleAccessor) job)
                .appliedenhancements$getTasks().entrySet()) {
            long taskCount = ((ExecutingCraftingTaskProgressAccessor) task.getValue())
                    .appliedenhancements$getValue();
            for (var output : task.getKey().getOutputs()) {
                if (template.matches(output)) {
                    count = CraftingAmountProjection.saturatingAddNonNegative(
                            count,
                            CraftingAmountProjection.saturatingMultiplyNonNegative(
                                    output.amount(), taskCount));
                }
            }
        }
        callback.setReturnValue(count);
    }

    @Inject(method = "getAllItems", at = @At("HEAD"), cancellable = true)
    private void appliedenhancements$saturateAllDisplayedItems(
            KeyCounter output, CallbackInfo callback) {
        appliedenhancements$mergeDisplayedItems(output, inventory.list);
        if (job == null) {
            callback.cancel();
            return;
        }
        var jobAccess = (ExecutingCraftingJobCycleAccessor) job;
        appliedenhancements$mergeDisplayedItems(
                output, jobAccess.appliedenhancements$getWaitingFor().list);
        for (var task : jobAccess.appliedenhancements$getTasks().entrySet()) {
            long taskCount = ((ExecutingCraftingTaskProgressAccessor) task.getValue())
                    .appliedenhancements$getValue();
            for (var stack : task.getKey().getOutputs()) {
                long amount = CraftingAmountProjection.saturatingMultiplyNonNegative(
                        stack.amount(), taskCount);
                output.set(stack.what(), CraftingAmountProjection.saturatingAddNonNegative(
                        output.get(stack.what()), amount));
            }
        }
        callback.cancel();
    }

    @Unique
    private static void appliedenhancements$mergeDisplayedItems(
            KeyCounter target, KeyCounter source) {
        for (var entry : source) {
            target.set(entry.getKey(), CraftingAmountProjection.saturatingAddNonNegative(
                    target.get(entry.getKey()), entry.getLongValue()));
        }
    }

    @WrapMethod(method = "insert")
    private long appliedenhancements$retainCycleFinalOutput(
            AEKey what,
            long amount,
            Actionable mode,
            Operation<Long> original) {
        var runtime = appliedenhancements$cycleRuntime;
        var waiting = runtime == null || job == null ? null
                : ((ExecutingCraftingJobCycleAccessor) job).appliedenhancements$getWaitingFor();
        long before = waiting == null || what == null ? 0 : waiting.list.get(what);
        long accepted = appliedenhancements$insertRetainedOutput(what, amount, mode, original);
        if (runtime != null && mode == Actionable.MODULATE && what != null) {
            // Standalone links return zero for final output routed back to ME, although
            // the CPU has settled its waiting count. Track that settlement as well.
            long after = waiting == null ? 0 : waiting.list.get(what);
            runtime.recordReturned(what, Math.max(0, Math.min(amount, before - after)));
            appliedenhancements$finishReturnedCycle();
        }
        return accepted;
    }

    @Unique
    private long appliedenhancements$insertRetainedOutput(
            AEKey what, long amount, Actionable mode, Operation<Long> original) {
        var runtime = appliedenhancements$cycleRuntime;
        var currentJob = job;
        if (runtime == null || !runtime.hasActiveSeedProtection()
                || currentJob == null
                || what == null
                || amount <= 0) {
            return original.call(what, amount, mode);
        }
        var jobAccess = (ExecutingCraftingJobCycleAccessor) currentJob;
        var finalOutput = jobAccess.appliedenhancements$getFinalOutput();
        if (finalOutput == null || !what.matches(finalOutput)) {
            return original.call(what, amount, mode);
        }

        long stored = inventory.extract(what, Long.MAX_VALUE, Actionable.SIMULATE);
        long retainable = runtime.amountToRetain(what, amount, stored);
        if (retainable <= 0) {
            return original.call(what, amount, mode);
        }
        var waitingFor = jobAccess.appliedenhancements$getWaitingFor();
        long accepted = waitingFor.extract(what, amount, Actionable.SIMULATE);
        long retained = Math.min(retainable, accepted);
        if (retained <= 0) {
            return original.call(what, amount, mode);
        }

        long forwardedAmount = amount - retained;
        if (mode == Actionable.SIMULATE) {
            long forwarded = forwardedAmount <= 0
                    ? 0
                    : original.call(what, forwardedAmount, mode);
            return Math.min(accepted, retained + forwarded);
        }

        long removed = waitingFor.extract(what, retained, Actionable.MODULATE);
        if (removed != retained) {
            throw new IllegalStateException(
                    "Cycle runtime waiting-for inventory changed during retention");
        }
        ((ElapsedTimeTrackerAccessor) jobAccess.appliedenhancements$getTimeTracker())
                .appliedenhancements$decrementItems(retained, what.getType());
        inventory.insert(what, retained, Actionable.MODULATE);
        cluster.markDirty();
        long forwarded = forwardedAmount <= 0
                ? 0
                : original.call(what, forwardedAmount, mode);
        return retained + forwarded;
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
        if (what != null && mode == Actionable.MODULATE) {
            if (total > 0) {
                appliedenhancements$diagActivity = appliedenhancements$now();
            }
            var diagFinal = job == null ? null
                    : ((ExecutingCraftingJobCycleAccessor) job).appliedenhancements$getFinalOutput();
            if (total < Math.min(amount, waitBefore) && (diagFinal == null || !what.matches(diagFinal))) {
                com.appliedenhancements.runtime.DiagLog.log("cpu.refuse." + what,
                        "cpu insert {}: offered {} accepted {} while waiting for {}", what, amount, total, waitBefore);
            }
        }
        if (total > 0 && mode == Actionable.MODULATE && what != null) {
            long now = appliedenhancements$now();
            if (appliedenhancements$chanceMarks.contains(what)) {
                appliedenhancements$topUp.onOutput(what, total, now);
            } else {
                appliedenhancements$topUp.touch(now);
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
        var jobAccess = (ExecutingCraftingJobCycleAccessor) job;
        var finalOutput = jobAccess.appliedenhancements$getFinalOutput();
        if (finalOutput == null || !what.matches(finalOutput)) {
            return 0;
        }
        if (!appliedenhancements$chanceMarks.contains(what)
                && !ChanceOutputBonus.hasMarkedProducer(jobAccess.appliedenhancements$getTasks().keySet(), what)) {
            return 0;
        }
        long remaining = jobAccess.appliedenhancements$getRemainingAmount();
        long bonus = ChanceOutputBonus.bonusToAccept(amount - accepted, remaining);
        if (bonus <= 0 || mode == Actionable.SIMULATE) {
            return Math.max(bonus, 0);
        }
        ((ElapsedTimeTrackerAccessor) jobAccess.appliedenhancements$getTimeTracker())
                .appliedenhancements$decrementItems(bonus, what.getType());
        jobAccess.appliedenhancements$setRemainingAmount(remaining - bonus);
        cluster.markDirty();
        if (remaining - bonus <= 0) {
            finishJob(true);
        }
        return bonus;
    }

    @Override
    public Result appliedenhancements$requestChanceRetry(AEKey key) {
        if (job == null) {
            com.appliedenhancements.AppliedEnhancements.LOGGER.info("Chance retry (AE2 CPU {}): NO_JOB, this CPU's job is null", cluster);
            return Result.NO_JOB;
        }
        if (key == null || !appliedenhancements$chanceMarks.contains(key)) {
            return Result.NOT_MARKED;
        }
        if (appliedenhancements$retry.active()) {
            return Result.ALREADY_RUNNING;
        }
        var jobAccess = (ExecutingCraftingJobCycleAccessor) job;
        var output = jobAccess.appliedenhancements$getFinalOutput();
        long outstanding = output != null && key.matches(output)
                ? jobAccess.appliedenhancements$getRemainingAmount()
                : getWaitingFor(key);
        var grid = cluster.getGrid();
        if (grid == null) {
            com.appliedenhancements.AppliedEnhancements.LOGGER.info("Chance retry (AE2 CPU {}): NO_JOB, the cluster has no grid", cluster);
            return Result.NO_JOB;
        }
        if (outstanding <= 0) {
            return Result.NOT_WAITING;
        }
        appliedenhancements$retry.start(grid, cluster.getLevel(), key, outstanding);
        return Result.STARTED;
    }

    /** After the normal tick: start a chance top-up wave when a marked pattern's outputs are overdue. */
    @WrapMethod(method = "tickCraftingLogic")
    private void appliedenhancements$chanceTopUp(
            appeng.api.networking.energy.IEnergyService energyService,
            CraftingService craftingService,
            Operation<Void> original) {
        original.call(energyService, craftingService);
        appliedenhancements$retry.collect(cluster.getGrid());
        if (job != null) {
            appliedenhancements$diagStall();
        }
        if (job == null || appliedenhancements$chanceMarks.isEmpty()) {
            return;
        }
        var grid = cluster.getGrid();
        long now = appliedenhancements$now();
        if (grid == null || now < 0) {
            return;
        }
        if (appliedenhancements$topUp.lastActivity() < 0) {
            appliedenhancements$topUp.touch(now);
        }
        com.appliedenhancements.runtime.ChanceTopUpRunner.tick(
                new com.appliedenhancements.runtime.Ae2TopUpJob(job, inventory),
                appliedenhancements$topUp, appliedenhancements$chanceMarks, grid, now);
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"))
    private void appliedenhancements$writeCycleRuntime(
            ValueOutput output,
            CallbackInfo callback) {
        if (job != null && !appliedenhancements$chanceMarks.isEmpty()) {
            output.store(APPLIEDENHANCEMENTS_CHANCE_MARKS_TAG, AEKey.CODEC.listOf(),
                    appliedenhancements$chanceMarks.toList());
        }
        if (appliedenhancements$cycleRuntime != null
                && appliedenhancements$cycleRuntime.hasActiveSeedProtection()) {
            // MC 26: ValueOutput carries no registries (ValueInput does, via lookup()).
            // The CPU is only saved by its core block entity, so the cluster's level
            // supplies the same registries the 1.21 `registries` parameter did.
            var level = cluster.getLevel();
            if (level == null) {
                com.appliedenhancements.runtime.AelisPlanningLog.error(
                        "Cannot save AELIS cycle runtime state: crafting CPU has no level");
                return;
            }
            // CompoundTag.CODEC is a passthrough, so this stores the same compound
            // under the same key as 1.21's data.put(...): saves stay compatible.
            output.store(
                    APPLIEDENHANCEMENTS_CYCLE_RUNTIME_TAG,
                    CompoundTag.CODEC,
                    AelisCycleExecutionApi.writeRuntime(
                            appliedenhancements$cycleRuntime, level.registryAccess()));
        }
    }

    @Inject(method = "readFromNBT", at = @At("RETURN"))
    private void appliedenhancements$readCycleRuntime(
            ValueInput input,
            CallbackInfo callback) {
        appliedenhancements$chanceMarks.load(job == null ? java.util.List.of()
                : input.read(APPLIEDENHANCEMENTS_CHANCE_MARKS_TAG, AEKey.CODEC.listOf())
                        .orElse(java.util.List.of()));
        Optional<CompoundTag> stored = job != null
                ? input.read(APPLIEDENHANCEMENTS_CYCLE_RUNTIME_TAG, CompoundTag.CODEC)
                : Optional.empty();
        boolean hasRuntime = stored.isPresent();
        appliedenhancements$cycleRuntime = hasRuntime
                ? AelisCycleExecutionApi.readRuntime(
                        stored.get(), input.lookup()).orElse(null)
                : null;
        if (hasRuntime && appliedenhancements$cycleRuntime == null) {
            com.appliedenhancements.runtime.AelisPlanningLog.error(
                    "Cancelling AE2 crafting job because its AELIS cycle runtime state is invalid");
            cancel();
        } else if (hasRuntime) {
            appliedenhancements$cycleRuntime = AelisCycleRuntimePreparation.prepareNative(
                    appliedenhancements$cycleRuntime, job, cluster.getLevel());
        }
    }

    @WrapMethod(method = "finishJob")
    private void appliedenhancements$awaitCycleReturns(boolean success, Operation<Void> original) {
        if (success && appliedenhancements$cycleRuntime != null
                && (!appliedenhancements$cycleRuntime.isCyclePhaseComplete()
                        || appliedenhancements$dispatchDepth > 0)) return;
        original.call(success);
    }

    @Unique
    private void appliedenhancements$finishReturnedCycle() {
        if (appliedenhancements$dispatchDepth == 0 && job != null && appliedenhancements$cycleRuntime != null
                && appliedenhancements$cycleRuntime.isCyclePhaseComplete()
                && ((ExecutingCraftingJobCycleAccessor) job).appliedenhancements$getRemainingAmount() == 0) {
            finishJob(true);
        }
    }

    @Inject(method = "finishJob", at = @At("TAIL"))
    private void appliedenhancements$clearCycleRuntime(
            boolean success, CallbackInfo callback) {
        appliedenhancements$cycleRuntime = null;
        appliedenhancements$chanceMarks.clear();
        appliedenhancements$topUp.clear();
        appliedenhancements$retry.cancel();
    }

}
