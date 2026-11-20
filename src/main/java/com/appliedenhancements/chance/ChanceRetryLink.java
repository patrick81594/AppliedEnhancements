package com.appliedenhancements.chance;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import appeng.me.helpers.MachineSource;
import java.util.concurrent.Future;
import net.minecraft.world.level.Level;

/**
 * The separate "retry" job that helps a stalled source job (middle click in the CPU screen): it requests the
 * outstanding amount of one item, and is cancelled when the source job ends. One instance per CPU logic; not
 * saved, so a reload forgets the link (the retry job then just finishes on its own).
 */
public final class ChanceRetryLink {
    private Future<?> plan;
    private ICraftingLink link;
    private AEKey key;
    private long amount;

    /** True while a calculation is running or the retry job is still queued/running. */
    public boolean active() {
        return plan != null || (link != null && !link.isDone() && !link.isCanceled());
    }

    /** Starts the crafting calculation for {@code amount} of {@code key}; the job is submitted by {@link #collect}. */
    public void start(IGrid grid, Level level, AEKey key, long amount) {
        var src = new MachineSource(grid::getPivot);
        ICraftingSimulationRequester requester = () -> src;
        this.plan = grid.getCraftingService().beginCraftingCalculation(level, requester, key, amount,
                CalculationStrategy.REPORT_MISSING_ITEMS);
        this.key = key;
        this.amount = amount;
    }

    /** Submits the retry job once its calculation is done. Call every tick of the source CPU. */
    public void collect(IGrid grid) {
        var future = plan;
        if (future == null || !future.isDone()) {
            return;
        }
        plan = null;
        if (grid == null) {
            return;
        }
        try {
            var result = (ICraftingPlan) future.get();
            if (result.simulation()) {
                ChanceLog.info("Chance retry of {} x{}: not possible (items missing)", key, amount);
                return;
            }
            var submit = grid.getCraftingService().submitJob(result, null, null, false,
                    new MachineSource(grid::getPivot));
            if (submit.successful()) {
                link = submit.link();
            }
            ChanceLog.info("Chance retry job for {} x{} {}", key, amount,
                    submit.successful() ? "started" : "not started: " + submit.errorCode());
        } catch (Exception e) {
            ChanceLog.info("Chance retry job for {} failed: {}", key, e.toString());
        }
    }

    /** The source job ended (done or cancelled): the retry job that was helping it is cancelled too. */
    public void cancel() {
        var future = plan;
        plan = null;
        if (future != null) {
            future.cancel(true);
        }
        var current = link;
        link = null;
        if (current != null && !current.isDone() && !current.isCanceled()) {
            current.cancel();
            ChanceLog.info("Chance retry job for {} cancelled: its source job ended", key);
        }
    }
}
