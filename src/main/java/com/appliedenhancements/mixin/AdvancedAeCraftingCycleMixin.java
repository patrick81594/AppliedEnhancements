package com.appliedenhancements.mixin;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.inv.ListCraftingInventory;
import com.appliedenhancements.api.AelisCycleExecutionApi;
import com.appliedenhancements.api.AelisBatchExecutionContext;
import com.appliedenhancements.api.AelisCycleRuntimeController;
import com.appliedenhancements.runtime.AelisCycleDispatchScope;
import com.appliedenhancements.runtime.AdvancedAeCycleRecovery;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import appeng.me.service.CraftingService;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
// Apply after default-priority CPU observers, matching the Forge integration.
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic", remap = false, priority = 900)
public abstract class AdvancedAeCraftingCycleMixin implements com.appliedenhancements.runtime.ChanceTopUpControl {
    /** Middle-click retry: the separate job that helps this (source) job. Not saved. */
    @Unique private final com.appliedenhancements.runtime.ChanceRetryLink appliedenhancements$retry =
            new com.appliedenhancements.runtime.ChanceRetryLink();

    @Override
    public Result appliedenhancements$requestChanceRetry(AEKey key) {
        return com.appliedenhancements.runtime.AdvancedAeChanceOutputBonus.requestRetry(
                this, appliedenhancements$chanceMarks, appliedenhancements$retry, key,
                key == null ? 0 : getWaitingFor(key));
    }

    @Shadow public abstract ListCraftingInventory getInventory();
    @Shadow public abstract GenericStack getFinalJobOutput();
    @Shadow public abstract boolean hasJob();
    @Shadow public abstract long getWaitingFor(AEKey key);
    @Shadow public abstract void cancel();
    @Shadow private void finishJob(boolean success) { throw new AssertionError(); }

    @Unique private AelisCycleRuntimeController appliedenhancements$cycleRuntime;
    @Unique private int appliedenhancements$dispatchDepth;
    @Unique private boolean appliedenhancements$retainingCycleOutput;
    @Unique private final com.appliedenhancements.runtime.ChanceJobMarks appliedenhancements$chanceMarks =
            new com.appliedenhancements.runtime.ChanceJobMarks();
    @Unique private static final String APPLIEDENHANCEMENTS_CHANCE_MARKS_TAG = "appliedenhancementsChanceMarks";
    /** TEMPORARY diagnostics: last push/insert game time (stall detection). */
    @Unique private long appliedenhancements$diagActivity = -1;
    @Unique private final com.appliedenhancements.runtime.ChanceTopUpState<IPatternDetails> appliedenhancements$topUp =
            new com.appliedenhancements.runtime.ChanceTopUpState<>();
    @Unique private static final String APPLIEDENHANCEMENTS_CYCLE_TAG = "appliedenhancementsCycleRuntime";

    @ModifyVariable(method = "trySubmitJob", at = @At("HEAD"), argsOnly = true)
    private ICraftingPlan appliedenhancements$normalizeCyclePlan(ICraftingPlan plan) {
        return AelisCycleExecutionApi.preparePlan(plan);
    }

    @Inject(method = "trySubmitJob", at = @At("RETURN"))
    private void appliedenhancements$startCycle(IGrid grid, ICraftingPlan plan,
            IActionSource source, ICraftingRequester requester,
            CallbackInfoReturnable<ICraftingSubmitResult> callback) {
        if (callback.getReturnValue() != null && callback.getReturnValue().successful()) {
            appliedenhancements$cycleRuntime = AelisCycleExecutionApi.getPlan(plan)
                    .map(AelisCycleRuntimeController::withCyclePhase).orElse(null);
        }
    }

    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/crafting/execution/CraftingCpuHelper;extractPatternInputs(Lappeng/api/crafting/IPatternDetails;Lappeng/crafting/inv/ICraftingInventory;Lnet/minecraft/world/level/Level;Lappeng/api/stacks/KeyCounter;Lappeng/api/stacks/KeyCounter;)[Lappeng/api/stacks/KeyCounter;"))
    private KeyCounter[] appliedenhancements$guardInputs(IPatternDetails pattern,
            ICraftingInventory inventory, Level level, KeyCounter outputs,
            KeyCounter remainders, Operation<KeyCounter[]> original) {
        var guarded = AelisCycleExecutionApi.guardInputs(appliedenhancements$cycleRuntime, pattern.getDefinition(), inventory);
        if (guarded == null) {
            com.appliedenhancements.runtime.DiagLog.log("quantum.guard.null." + pattern.getPrimaryOutput().what(),
                    "quantum cpu inputs {}: cycle guard returned no inventory", pattern.getPrimaryOutput().what());
            return null;
        }
        var extracted = original.call(pattern, guarded, level, outputs, remainders);
        if (extracted == null) {
            com.appliedenhancements.runtime.DiagLog.log("quantum.extract.null." + pattern.getPrimaryOutput().what(),
                    "quantum cpu inputs {}: extractPatternInputs returned null (inputs not in CPU) patternClass={}",
                    pattern.getPrimaryOutput().what(), pattern.getClass().getName());
        }
        return extracted;
    }

    /** TEMPORARY diagnostics (2026-11-18, Assembler Matrix): which providers does the quantum CPU see? */
    @WrapOperation(method = "executeCrafting", require = 0, at = @At(value = "INVOKE",
            target = "Lappeng/me/service/CraftingService;getProviders(Lappeng/api/crafting/IPatternDetails;)Ljava/lang/Iterable;"))
    private Iterable<ICraftingProvider> appliedenhancements$diagProviders(CraftingService service,
            IPatternDetails pattern, Operation<Iterable<ICraftingProvider>> original) {
        var providers = original.call(service, pattern);
        if (!com.appliedenhancements.runtime.DiagLog.enabled()) {
            return providers;
        }
        var names = new java.util.ArrayList<String>();
        for (var provider : providers) {
            names.add(provider.getClass().getSimpleName()
                    + (com.appliedenhancements.runtime.DiagLog.isMatrix(provider) ? "(busy=" + provider.isBusy() + ")" : ""));
        }
        com.appliedenhancements.runtime.DiagLog.log("quantum.providers." + pattern.getPrimaryOutput().what() + "." + names.size(),
                "quantum cpu getProviders {} -> {} provider(s) {} patternClass={}", pattern.getPrimaryOutput().what(),
                names.size(), names, pattern.getClass().getName());
        return providers;
    }

    @WrapMethod(method = "executeCrafting")
    private int appliedenhancements$withCycleScope(int maxPatterns, CraftingService craftingService,
            IEnergyService energyService, Level level, Operation<Integer> original) {
        try (var scope = AelisCycleDispatchScope.open(() -> appliedenhancements$cycleRuntime)) {
            int executed;
            appliedenhancements$dispatchDepth++;
            try {
                executed = original.call(maxPatterns, craftingService, energyService, level);
            } finally {
                appliedenhancements$dispatchDepth--;
            }
            appliedenhancements$finishReturnedCycle();
            return executed;
        }
    }

    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"))
    private boolean appliedenhancements$advanceCycle(ICraftingProvider provider,
            IPatternDetails pattern, KeyCounter[] inputs, Operation<Boolean> original) {
        AelisBatchExecutionContext.Dispatch dispatch;
        try {
            dispatch = AelisBatchExecutionContext.beginProviderDispatch(
                    appliedenhancements$cycleRuntime, pattern.getDefinition(), inputs);
        } catch (IllegalStateException invalidBatch) {
            com.appliedenhancements.runtime.AelisPlanningLog.warn("Rejected invalid AELIS quantum CPU batch: {}", invalidBatch.getMessage());
            return false;
        }
        try (dispatch) {
            boolean accepted = original.call(provider, pattern, inputs);
            if (com.appliedenhancements.runtime.DiagLog.isMatrix(provider) || !accepted) {
                com.appliedenhancements.runtime.DiagLog.log(
                        "quantum.push." + accepted + "." + provider.getClass().getName() + "." + pattern.getPrimaryOutput().what(),
                        "quantum cpu pushPattern {} to {} -> {}", pattern.getPrimaryOutput().what(),
                        provider.getClass().getName(), accepted);
            }
            if (accepted) {
                dispatch.accepted();
                appliedenhancements$diagActivity = com.appliedenhancements.runtime.AdvancedAeTopUp.now(this);
                if (appliedenhancements$chanceMarks.recordIfMarked(pattern)) {
                    appliedenhancements$topUp.onPush(pattern, pattern.getPrimaryOutput().what(),
                            com.appliedenhancements.runtime.AdvancedAeTopUp.now(this));
                }
            }
            return accepted;
        }
    }

    @WrapMethod(method = "insert")
    private long appliedenhancements$retainOutput(AEKey key, long amount, Actionable mode,
            Operation<Long> original) {
        var runtime = appliedenhancements$cycleRuntime;
        long diagWait = key == null ? 0 : getWaitingFor(key);
        long before = runtime == null || key == null ? 0 : getWaitingFor(key);
        long accepted = appliedenhancements$insertRetainedOutput(key, amount, mode, original);
        if (runtime != null && mode == Actionable.MODULATE && key != null) {
            runtime.recordReturned(key, Math.max(0, Math.min(amount, before - getWaitingFor(key))));
            appliedenhancements$finishReturnedCycle();
        }
        // Chance outputs of marked patterns: extra items count toward the requested total.
        // The bonus counts toward the job but is not claimed: the caller stores the surplus in network storage.
        // What the CPU itself counted (see CraftingCpuLogicBatchMixin): the return value is 0 for a final output
        // whose requester took nothing, although the item was already counted against the job.
        long consumed = key == null ? 0
                : mode == Actionable.MODULATE ? Math.max(0, diagWait - getWaitingFor(key))
                        : Math.min(amount, diagWait);
        long bonus = com.appliedenhancements.runtime.AdvancedAeChanceOutputBonus.insertBonus(
                this, appliedenhancements$chanceMarks, getInventory(), key, amount, Math.max(accepted, consumed), mode,
                () -> finishJob(true));
        long delivered = Math.max(accepted, consumed) + bonus;
        if (key != null && mode == Actionable.MODULATE && accepted < Math.min(amount, diagWait) && !(getFinalJobOutput() != null && key.matches(getFinalJobOutput()))) {
            com.appliedenhancements.runtime.DiagLog.log("quantum.refuse." + key,
                    "quantum cpu insert {}: offered {} accepted {} while waiting for {}", key, amount, accepted, diagWait);
        }
        if (delivered > 0 && mode == Actionable.MODULATE && key != null) {
            long now = com.appliedenhancements.runtime.AdvancedAeTopUp.now(this);
            appliedenhancements$diagActivity = now;
            if (appliedenhancements$chanceMarks.contains(key)) {
                appliedenhancements$topUp.onOutput(key, delivered, now);
            } else {
                appliedenhancements$topUp.touch(now);
            }
        }
        return accepted;
    }

    /** After the normal tick: start a chance top-up wave when a marked pattern's outputs are overdue. */
    @WrapMethod(method = "tickCraftingLogic")
    private void appliedenhancements$chanceTopUp(IEnergyService energyService, CraftingService craftingService,
            Operation<Void> original) {
        original.call(energyService, craftingService);
        com.appliedenhancements.runtime.AdvancedAeChanceOutputBonus.collectRetry(this, appliedenhancements$retry);
        if (hasJob()) {
            long now = com.appliedenhancements.runtime.AdvancedAeTopUp.now(this);
            if (now >= 0 && appliedenhancements$diagActivity < 0) {
                appliedenhancements$diagActivity = now;
            } else if (now >= 0 && now - appliedenhancements$diagActivity >= 1200
                    && com.appliedenhancements.runtime.DiagLog.every("cpu.stall.quantum", 30000)) {
                var text = com.appliedenhancements.runtime.AdvancedAeTopUp.jobText(this);
                if (text != null) {
                    com.appliedenhancements.runtime.DiagLog.log("cpu.stall.quantum.log",
                            "quantum cpu job STALLED {}s: {} cpuInventory={}",
                            (now - appliedenhancements$diagActivity) / 20, text,
                            com.appliedenhancements.runtime.DiagLog.counterText(getInventory().list));
                }
            }
        }
        com.appliedenhancements.runtime.AdvancedAeTopUp.tick(this, appliedenhancements$chanceMarks,
                appliedenhancements$topUp, getInventory());
    }

    @Unique
    private long appliedenhancements$insertRetainedOutput(AEKey key, long amount, Actionable mode,
            Operation<Long> original) {
        var runtime = appliedenhancements$cycleRuntime;
        var output = getFinalJobOutput();
        if (runtime == null || !runtime.hasActiveSeedProtection() || amount <= 0
                || key == null || output == null || !key.matches(output)) {
            return original.call(key, amount, mode);
        }
        long stored = getInventory().extract(key, Long.MAX_VALUE, Actionable.SIMULATE);
        amount = Math.min(amount, getWaitingFor(key));
        if (amount <= 0) {
            return 0;
        }
        long retain = runtime.amountToRetain(key, amount, stored);
        if (retain <= 0) {
            return original.call(key, amount, mode);
        }
        long retained;
        boolean previous = appliedenhancements$retainingCycleOutput;
        appliedenhancements$retainingCycleOutput = true;
        try {
            // AdvancedAE handles waiting counts, elapsed work and inventory insertion itself.
            retained = original.call(key, retain, mode);
        } finally {
            appliedenhancements$retainingCycleOutput = previous;
        }
        if (retained < retain || amount == retain) {
            return retained;
        }
        return retained + original.call(key, amount - retain, mode);
    }

    @WrapOperation(method = "insert", at = @At(value = "INVOKE",
            target = "Lappeng/api/stacks/AEKey;matches(Lappeng/api/stacks/GenericStack;)Z"))
    private boolean appliedenhancements$storeProtectedOutput(AEKey key, GenericStack output,
            Operation<Boolean> original) {
        return !appliedenhancements$retainingCycleOutput && original.call(key, output);
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"))
    private void appliedenhancements$saveCycle(ValueOutput output, CallbackInfo callback) {
        if (hasJob() && !appliedenhancements$chanceMarks.isEmpty()) {
            output.store(APPLIEDENHANCEMENTS_CHANCE_MARKS_TAG, AEKey.CODEC.listOf(),
                    appliedenhancements$chanceMarks.toList());
        }
        if (hasJob() && appliedenhancements$cycleRuntime != null
                && appliedenhancements$cycleRuntime.hasActiveSeedProtection()) {
            // MC 26: ValueOutput carries no registries, and AdvancedAE's `cpu` field type
            // is not on our compile classpath. Every ServerLevel is built with
            // server.registryAccess(), so the server gives the same registries the
            // 1.21 `registries` parameter did.
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                com.appliedenhancements.runtime.AelisPlanningLog.error(
                        "Cannot save AELIS quantum CPU cycle state: no running server");
                return;
            }
            // CompoundTag.CODEC is a passthrough: same compound under the same key as
            // 1.21's tag.put(...), so saves stay compatible.
            output.store(APPLIEDENHANCEMENTS_CYCLE_TAG, CompoundTag.CODEC,
                    AelisCycleExecutionApi.writeRuntime(
                            appliedenhancements$cycleRuntime, server.registryAccess()));
        }
    }

    @Inject(method = "readFromNBT", at = @At("RETURN"))
    private void appliedenhancements$loadCycle(ValueInput input, CallbackInfo callback) {
        appliedenhancements$chanceMarks.load(!hasJob() ? java.util.List.of()
                : input.read(APPLIEDENHANCEMENTS_CHANCE_MARKS_TAG, AEKey.CODEC.listOf())
                        .orElse(java.util.List.of()));
        Optional<CompoundTag> stored = hasJob()
                ? input.read(APPLIEDENHANCEMENTS_CYCLE_TAG, CompoundTag.CODEC)
                : Optional.empty();
        boolean saved = stored.isPresent();
        appliedenhancements$cycleRuntime = saved
                ? AelisCycleExecutionApi.readRuntime(stored.get(), input.lookup()).orElse(null) : null;
        if (saved && appliedenhancements$cycleRuntime == null) {
            com.appliedenhancements.runtime.AelisPlanningLog.error("Invalid AELIS quantum CPU cycle state; cancelling job safely");
            cancel();
        } else if (saved) {
            AdvancedAeCycleRecovery.reconcile(this, appliedenhancements$cycleRuntime);
        } else if (!saved && hasJob()) {
            appliedenhancements$cycleRuntime = AdvancedAeCycleRecovery.recover(this, getInventory());
        }
        appliedenhancements$cycleRuntime = AdvancedAeCycleRecovery.prepare(
                this, appliedenhancements$cycleRuntime);
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
        if (appliedenhancements$dispatchDepth == 0 && hasJob() && appliedenhancements$cycleRuntime != null
                && appliedenhancements$cycleRuntime.isCyclePhaseComplete()
                && AdvancedAeCycleRecovery.isOutputComplete(this)) {
            finishJob(true);
        }
    }

    @Inject(method = "finishJob", at = @At("TAIL"))
    private void appliedenhancements$finishCycle(boolean success, CallbackInfo callback) {
        appliedenhancements$cycleRuntime = null;
        appliedenhancements$chanceMarks.clear();
        appliedenhancements$topUp.clear();
        appliedenhancements$retry.cancel();
    }
}
