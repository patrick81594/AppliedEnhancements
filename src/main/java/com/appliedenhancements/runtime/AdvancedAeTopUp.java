package com.appliedenhancements.runtime;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.inv.ListCraftingInventory;
import com.appliedenhancements.AppliedEnhancements;
import com.appliedenhancements.Config;
import java.util.Map;
import net.minecraft.world.level.Level;

/**
 * Chance-output top-up for AdvancedAE's quantum CPU (counterpart of the AE2 CPU hook). AdvancedAE is not a
 * compile dependency, so the job is reached by reflection (see {@link AdvancedAeChanceOutputBonus}); any
 * reflection failure disables the top-up for the CPU type (logged once) instead of breaking ticking.
 */
public final class AdvancedAeTopUp {
    private static final String PROGRESS_CLASS =
            "net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob$TaskProgress";

    private static volatile boolean failed;

    private AdvancedAeTopUp() {
    }

    /** Game time of the CPU's level, or -1 when unknown. */
    public static long now(Object cpuLogic) {
        if (failed) {
            return -1;
        }
        try {
            var a = AdvancedAeChanceOutputBonus.access();
            return a.cpuLevel().invoke(a.logicCpu().get(cpuLogic)) instanceof Level level ? level.getGameTime() : -1;
        } catch (ReflectiveOperationException | RuntimeException e) {
            disable(e);
            return -1;
        }
    }

    public static void tick(Object cpuLogic, ChanceJobMarks marks, ChanceTopUpState<IPatternDetails> state,
            ListCraftingInventory inventory) {
        if (failed || marks.isEmpty() || !Config.ENABLE_CHANCE_OUTPUT_BONUS.get()
                || !Config.ENABLE_CHANCE_TOP_UP.get()) {
            return;
        }
        try {
            var a = AdvancedAeChanceOutputBonus.access();
            Object job = a.logicJob().get(cpuLogic);
            Object cpu = a.logicCpu().get(cpuLogic);
            if (job == null || !(a.cpuGrid().invoke(cpu) instanceof IGrid grid)
                    || !(a.cpuLevel().invoke(cpu) instanceof Level level)) {
                return;
            }
            long now = level.getGameTime();
            if (state.lastActivity() < 0) {
                state.touch(now);
            }
            ChanceTopUpRunner.tick(new Job(a, job, inventory), state, marks, grid, now);
        } catch (ReflectiveOperationException | RuntimeException e) {
            disable(e);
        }
    }

    /** TEMPORARY diagnostics: "waitingFor + final + remaining" of the job, or null without a job. */
    public static String jobText(Object cpuLogic) {
        try {
            var a = AdvancedAeChanceOutputBonus.access();
            Object job = a.logicJob().get(cpuLogic);
            if (job == null) {
                return null;
            }
            var waiting = (ListCraftingInventory) a.jobWaiting().get(job);
            if (waiting.list.isEmpty()) {
                return null;
            }
            return "final=" + a.jobFinal().get(job) + " remaining=" + a.jobRemaining().getLong(job)
                    + " waitingFor=" + DiagLog.counterText(waiting.list)
                    + " tasksLeft=" + ((Map<?, ?>) a.jobTasks().get(job)).size();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "<" + e + ">";
        }
    }

    private static void disable(Exception e) {
        failed = true;
        AppliedEnhancements.LOGGER.error("Chance top-up disabled for the AdvancedAE CPU: {}", e.toString());
    }

    /** {@link TopUpJob} over AdvancedAE's ExecutingCraftingJob. */
    private record Job(AdvancedAeChanceOutputBonus.Access a, Object job, ListCraftingInventory inventory)
            implements TopUpJob {
        @Override
        public AEKey finalKey() {
            try {
                return a.jobFinal().get(job) instanceof GenericStack output ? output.what() : null;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public long remaining() {
            try {
                return a.jobRemaining().getLong(job);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public boolean tasksEmpty() {
            try {
                return ((Map<?, ?>) a.jobTasks().get(job)).isEmpty();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public boolean hasTask(IPatternDetails pattern) {
            try {
                return ((Map<?, ?>) a.jobTasks().get(job)).containsKey(pattern);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public java.util.Set<AEKey> waitingKeys() {
            try {
                return new java.util.HashSet<>(((ListCraftingInventory) a.jobWaiting().get(job)).list.keySet());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public long waitingFor(AEKey key) {
            try {
                return ((ListCraftingInventory) a.jobWaiting().get(job)).list.get(key);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public long dropWaitingFor(AEKey key) {
            try {
                var waiting = (ListCraftingInventory) a.jobWaiting().get(job);
                long dropped = waiting.extract(key, Long.MAX_VALUE, appeng.api.config.Actionable.MODULATE);
                if (dropped > 0) {
                    // Take the dropped outputs off the job's total work (decrementItems would count them as done).
                    Object tracker = a.jobTracker().get(job);
                    var field = tracker.getClass().getDeclaredField("startedWorkByType");
                    field.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    var started = (it.unimi.dsi.fastutil.objects.Reference2LongMap<appeng.api.stacks.AEKeyType>) field.get(tracker);
                    started.put(key.getType(), Math.max(0, started.getLong(key.getType()) - dropped));
                }
                return dropped;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public void addCrafts(IPatternDetails pattern, long crafts) {
            try {
                JobTaskProgress.add((Map<IPatternDetails, ?>) a.jobTasks().get(job), pattern, crafts,
                        PROGRESS_CLASS, AdvancedAeTopUp.class.getClassLoader());
                var output = pattern.getPrimaryOutput();
                a.addMax().invoke(a.jobTracker().get(job), output.amount() * crafts, output.what().getType());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
