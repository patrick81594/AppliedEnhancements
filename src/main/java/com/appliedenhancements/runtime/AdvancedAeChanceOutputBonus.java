package com.appliedenhancements.runtime;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.crafting.inv.ListCraftingInventory;
import com.appliedenhancements.Config;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Chance-output bonus for AdvancedAE's quantum CPU (counterpart of the AE2 CPU hook in
 * CraftingCpuLogicBatchMixin).
 *
 * AdvancedAE is not a compile dependency, and its job/time-tracker members are package-private,
 * so everything is reached by reflection. Any reflection failure disables the bonus (logged once)
 * instead of breaking the CPU's insert.
 */
public final class AdvancedAeChanceOutputBonus {
    private static final String LOGIC = "net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic";
    private static final String JOB = "net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob";
    private static final String TRACKER = "net.pedroksl.advanced_ae.common.logic.ElapsedTimeTracker";

    private static volatile Access access;
    private static volatile boolean failed;

    private AdvancedAeChanceOutputBonus() {
    }

    record Access(Field logicJob, Field logicCpu, Field jobFinal, Field jobRemaining, Field jobTasks,
            Field jobTracker, Field jobWaiting, Method decrement, Method addMax, Method markDirty,
            Method updateOutput, Method cpuLevel, Method cpuGrid) {
    }

    static Access access() throws ReflectiveOperationException {
        var local = access;
        if (local == null) {
            var loader = AdvancedAeChanceOutputBonus.class.getClassLoader();
            var logic = Class.forName(LOGIC, false, loader);
            var job = Class.forName(JOB, false, loader);
            var tracker = Class.forName(TRACKER, false, loader);
            var cpuField = open(logic, "cpu");
            var cpuClass = cpuField.getType();
            var decrement = tracker.getDeclaredMethod("decrementItems", long.class, AEKeyType.class);
            decrement.setAccessible(true);
            var addMax = tracker.getDeclaredMethod("addMaxItems", long.class, AEKeyType.class);
            addMax.setAccessible(true);
            local = new Access(open(logic, "job"), cpuField, open(job, "finalOutput"), open(job, "remainingAmount"),
                    open(job, "tasks"), open(job, "timeTracker"), open(job, "waitingFor"), decrement, addMax,
                    cpuClass.getMethod("markDirty"), cpuClass.getMethod("updateOutput", GenericStack.class),
                    cpuClass.getMethod("getLevel"), cpuClass.getMethod("getGrid"));
            access = local;
        }
        return local;
    }

    private static Field open(Class<?> owner, String name) throws ReflectiveOperationException {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    /**
     * Extra items beyond what the CPU expected, accepted toward the job's requested total.
     *
     * @param amount   amount offered to insert
     * @param accepted amount the CPU's normal insert accepted
     * @return the bonus amount (0 if none); in MODULATE mode the CPU state is already updated
     */
    public static long insertBonus(Object cpuLogic, ChanceJobMarks marks, ListCraftingInventory inventory, AEKey what, long amount,
            long accepted, Actionable mode, Runnable finishJob) {
        if (failed || what == null || amount <= accepted || !Config.ENABLE_CHANCE_OUTPUT_BONUS.get()) {
            return 0;
        }
        try {
            var a = access();
            Object job = a.logicJob().get(cpuLogic);
            if (job == null || !(a.jobFinal().get(job) instanceof GenericStack output) || !what.matches(output)) {
                return 0;
            }
            @SuppressWarnings("unchecked")
            Iterable<IPatternDetails> patterns = ((Map<IPatternDetails, ?>) a.jobTasks().get(job)).keySet();
            if (!marks.contains(what) && !ChanceOutputBonus.hasMarkedProducer(patterns, what)) {
                return 0;
            }
            long remaining = a.jobRemaining().getLong(job);
            long bonus = ChanceOutputBonus.bonusToAccept(amount - accepted, remaining);
            if (bonus <= 0 || mode == Actionable.SIMULATE) {
                return Math.max(bonus, 0);
            }
            a.decrement().invoke(a.jobTracker().get(job), bonus, what.getType());
            long left = remaining - bonus;
            a.jobRemaining().setLong(job, left);
            Object cpu = a.logicCpu().get(cpuLogic);
            a.markDirty().invoke(cpu);
            a.updateOutput().invoke(cpu, left > 0 ? new GenericStack(output.what(), left) : null);
            if (left <= 0) {
                finishJob.run();
            }
            return bonus;
        } catch (ReflectiveOperationException | RuntimeException e) {
            failed = true;
            AelisPlanningLog.error("Chance-output bonus disabled for the AdvancedAE CPU: {}", e.toString());
            return 0;
        }
    }

    /** Middle-click retry for the AdvancedAE (Quantum) CPU; see {@link ChanceTopUpControl}. */
    public static ChanceTopUpControl.Result requestRetry(Object cpuLogic, ChanceJobMarks marks, ChanceRetryLink retry,
            AEKey key, long waitingFor) {
        if (failed) {
            return ChanceTopUpControl.Result.NO_JOB;
        }
        try {
            var a = access();
            Object job = a.logicJob().get(cpuLogic);
            if (job == null) {
                return ChanceTopUpControl.Result.NO_JOB;
            }
            if (key == null || !marks.contains(key)) {
                return ChanceTopUpControl.Result.NOT_MARKED;
            }
            if (retry.active()) {
                return ChanceTopUpControl.Result.ALREADY_RUNNING;
            }
            long outstanding = a.jobFinal().get(job) instanceof GenericStack output && key.matches(output)
                    ? a.jobRemaining().getLong(job)
                    : waitingFor;
            Object cpu = a.logicCpu().get(cpuLogic);
            if (!(a.cpuGrid().invoke(cpu) instanceof appeng.api.networking.IGrid grid)
                    || !(a.cpuLevel().invoke(cpu) instanceof net.minecraft.world.level.Level level)) {
                return ChanceTopUpControl.Result.NO_JOB;
            }
            if (outstanding <= 0) {
                return ChanceTopUpControl.Result.NOT_WAITING;
            }
            retry.start(grid, level, key, outstanding);
            return ChanceTopUpControl.Result.STARTED;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return ChanceTopUpControl.Result.NO_JOB;
        }
    }

    /** Submits a finished retry calculation; call every tick of the Quantum CPU. */
    public static void collectRetry(Object cpuLogic, ChanceRetryLink retry) {
        try {
            var a = access();
            Object cpu = a.logicCpu().get(cpuLogic);
            retry.collect(a.cpuGrid().invoke(cpu) instanceof appeng.api.networking.IGrid grid ? grid : null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // diagnostics elsewhere already report a broken reflection setup
        }
    }
}
