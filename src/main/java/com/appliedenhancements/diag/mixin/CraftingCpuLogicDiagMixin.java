package com.appliedenhancements.diag.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
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
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import net.minecraft.world.level.Level;

import com.appliedenhancements.diag.DiagLog;

/**
 * Diagnostics observer on AE2's crafting CPU ({@code [ACE/diag] cpu ...} lines: refused pushes, providers per
 * pattern, inputs that could not be extracted, refused outputs, stalled jobs). It only observes: every handler
 * returns what the inner chain returned, and with {@code diagnostics.enabled} off each one costs a single config read.
 *
 * Priority 1120 = applied after the AELIS mixin (1100) and the chance mixin (1110), so these wrappers are the OUTER
 * ones and see the final results of the whole chain (a {@code null} from extractPatternInputs may come from the AELIS
 * cycle guard, AE2 itself, or the AELIS "not trackable" re-inject: the three old separate lines are one line now).
 * All injectors are {@code require = 0}: a diagnostic must never stop the game from starting.
 */
@Mixin(value = CraftingCpuLogic.class, remap = false, priority = 1120)
public abstract class CraftingCpuLogicDiagMixin {
    @Shadow
    private ExecutingCraftingJob job;

    @Shadow
    @Final
    private ListCraftingInventory inventory;

    @Shadow
    @Final
    private CraftingCPUCluster cluster;

    @Shadow
    public abstract long getWaitingFor(AEKey what);

    /** Last push/insert game time (stall detection). */
    @Unique
    private long appliedenhancements$diagActivity = -1;

    @Unique
    private long appliedenhancements$diagNow() {
        var level = cluster.getLevel();
        return level == null ? -1 : level.getGameTime();
    }

    @WrapOperation(method = "executeCrafting", require = 0, at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"))
    private boolean appliedenhancements$diagPush(ICraftingProvider provider, IPatternDetails details,
            KeyCounter[] inputHolder, Operation<Boolean> original) {
        boolean pushed = original.call(provider, details, inputHolder);
        if (DiagLog.enabled()) {
            if (DiagLog.isMatrix(provider) || !pushed) {
                DiagLog.log("cpu.push." + pushed + "." + provider.getClass().getName() + "." + details.getPrimaryOutput().what(),
                        "cpu pushPattern {} to {} -> {}", details.getPrimaryOutput().what(),
                        provider.getClass().getName(), pushed);
            }
            if (pushed) {
                appliedenhancements$diagActivity = appliedenhancements$diagNow();
            }
        }
        return pushed;
    }

    /** Which providers does the CPU see per pattern? */
    @WrapOperation(method = "executeCrafting", require = 0, at = @At(value = "INVOKE",
            target = "Lappeng/me/service/CraftingService;getProviders(Lappeng/api/crafting/IPatternDetails;)Ljava/lang/Iterable;"))
    private Iterable<ICraftingProvider> appliedenhancements$diagProviders(CraftingService service,
            IPatternDetails details, Operation<Iterable<ICraftingProvider>> original) {
        var providers = original.call(service, details);
        if (!DiagLog.enabled()) {
            return providers;
        }
        var names = new java.util.ArrayList<String>();
        for (var provider : providers) {
            names.add(provider.getClass().getSimpleName()
                    + (DiagLog.isMatrix(provider) ? "(busy=" + provider.isBusy() + ")" : ""));
        }
        DiagLog.log("cpu.providers." + details.getPrimaryOutput().what() + "." + names.size(),
                "cpu getProviders {} -> {} provider(s) {} patternClass={}", details.getPrimaryOutput().what(),
                names.size(), names, details.getClass().getName());
        return providers;
    }

    @WrapOperation(method = "executeCrafting", require = 0, at = @At(value = "INVOKE",
            target = "Lappeng/crafting/execution/CraftingCpuHelper;extractPatternInputs(Lappeng/api/crafting/IPatternDetails;Lappeng/crafting/inv/ICraftingInventory;Lnet/minecraft/world/level/Level;Lappeng/api/stacks/KeyCounter;Lappeng/api/stacks/KeyCounter;)[Lappeng/api/stacks/KeyCounter;"))
    private KeyCounter[] appliedenhancements$diagExtract(IPatternDetails details, ICraftingInventory sourceInventory,
            Level level, KeyCounter expectedOutputs, KeyCounter expectedContainerItems,
            Operation<KeyCounter[]> original) {
        var extracted = original.call(details, sourceInventory, level, expectedOutputs, expectedContainerItems);
        if (extracted == null && DiagLog.enabled()) {
            DiagLog.log("cpu.extract.null." + details.getPrimaryOutput().what(),
                    "cpu inputs {}: extractPatternInputs returned null (inputs not in CPU, cycle guard returned no inventory, "
                            + "or expected outputs not trackable) patternClass={}",
                    details.getPrimaryOutput().what(), details.getClass().getName());
        }
        return extracted;
    }

    /** Outputs the CPU refuses (not waited for) and the last-activity clock of the stall detection. */
    @WrapMethod(method = "insert", require = 0)
    private long appliedenhancements$diagInsert(AEKey what, long amount, Actionable mode, Operation<Long> original) {
        if (!DiagLog.enabled() || what == null || mode != Actionable.MODULATE) {
            return original.call(what, amount, mode);
        }
        long waitBefore = getWaitingFor(what);
        long accepted = original.call(what, amount, mode);
        // What the CPU counted (not the return value, see CraftingCpuLogicChanceMixin). A chance bonus is not
        // included: it only exists for the final output, which the refusal line skips anyway.
        long consumed = Math.max(0, waitBefore - getWaitingFor(what));
        if (consumed > 0) {
            appliedenhancements$diagActivity = appliedenhancements$diagNow();
        }
        var finalOutput = job == null ? null : DiagLog.probe(job, "finalOutput");
        if (consumed < Math.min(amount, waitBefore)
                && (!(finalOutput instanceof appeng.api.stacks.GenericStack output) || !what.matches(output))) {
            DiagLog.log("cpu.refuse." + what, "cpu insert {}: offered {} accepted {} while waiting for {}",
                    what, amount, consumed, waitBefore);
        }
        return accepted;
    }

    /** Stalled jobs: nothing pushed or received for a minute while the job still waits for something. */
    @WrapMethod(method = "tickCraftingLogic", require = 0)
    private void appliedenhancements$diagTick(IEnergyService energyService, CraftingService craftingService,
            Operation<Void> original) {
        original.call(energyService, craftingService);
        if (job == null || !DiagLog.enabled()) {
            return;
        }
        long now = appliedenhancements$diagNow();
        if (now < 0 || appliedenhancements$diagActivity < 0) {
            appliedenhancements$diagActivity = now;
            return;
        }
        if (now - appliedenhancements$diagActivity < 1200 || !DiagLog.every("cpu.stall.ae2", 30000)) {
            return;
        }
        var text = DiagLog.jobText(job);
        if (text != null) {
            DiagLog.log("cpu.stall.ae2.log", "cpu job STALLED {}s: {} cpuInventory={}",
                    (now - appliedenhancements$diagActivity) / 20, text, DiagLog.counterText(inventory.list));
        }
    }
}
