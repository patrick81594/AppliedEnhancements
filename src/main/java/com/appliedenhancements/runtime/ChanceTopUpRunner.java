package com.appliedenhancements.runtime;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.me.helpers.MachineSource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.appliedenhancements.AppliedEnhancements;
import com.appliedenhancements.Config;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Chance-output top-up (D3 step b): when every craft of a marked pattern has been pushed and nothing has
 * arrived for the idle time, forget the outputs that did not come, learn the pattern's yield from the
 * finished wave, and schedule enough extra crafts (inputs taken from network storage) to cover what the job
 * still needs. Runs once per CPU tick; cheap checks first.
 */
public final class ChanceTopUpRunner {
    private ChanceTopUpRunner() {
    }

    /**
     * Starts a crafting calculation for the first missing input that the network can craft. One request at a time
     * and at most one per idle period, so a slow chain is never flooded.
     */
    private static void requestMissingInputs(ChanceTopUpState<IPatternDetails> state, IGrid grid, AEKey[] keys,
            long[] missing, long now, long idleTicks) {
        if (!Config.CHANCE_TOP_UP_CRAFT_INPUTS.get() || !state.followUpAllowed(now, idleTicks)) {
            return;
        }
        var crafting = grid.getCraftingService();
        for (int i = 0; i < keys.length; i++) {
            if (missing[i] > 0 && keys[i] instanceof AEItemKey && crafting.isCraftable(keys[i])) {
                var src = new MachineSource(grid::getPivot);
                ICraftingSimulationRequester requester = () -> src;
                var level = grid.getPivot().getLevel();
                long amount = Math.min(missing[i], 4096);
                state.setPending(crafting.beginCraftingCalculation(level, requester, keys[i], amount,
                        CalculationStrategy.REPORT_MISSING_ITEMS), keys[i], amount, now);
                AppliedEnhancements.LOGGER.info("Chance top-up: planning a follow-up craft of {} x{} for the missing input",
                        keys[i], amount);
                return;
            }
        }
    }

    /** Submits the follow-up craft once its calculation is done (and only when nothing is missing for it). */
    private static void collectFollowUp(ChanceTopUpState<IPatternDetails> state, IGrid grid) {
        var future = state.pendingPlan();
        if (future == null || !future.isDone()) {
            return;
        }
        AEKey key = state.pendingKey();
        long amount = state.pendingAmount();
        state.clearPending();
        try {
            var plan = (ICraftingPlan) future.get();
            if (plan.simulation()) {
                AppliedEnhancements.LOGGER.info("Chance top-up: follow-up craft of {} x{} not possible (items missing)",
                        key, amount);
                return;
            }
            var result = grid.getCraftingService().submitJob(plan, null, null, false, new MachineSource(grid::getPivot));
            AppliedEnhancements.LOGGER.info("Chance top-up: follow-up craft of {} x{} {}", key, amount,
                    result.successful() ? "started" : "not started: " + result.errorCode());
        } catch (Exception e) {
            AppliedEnhancements.LOGGER.info("Chance top-up: follow-up craft of {} x{} failed: {}", key, amount, e.toString());
        }
    }

    private static IPatternDetails findMarkedProducer(IGrid grid, AEKey key) {
        for (var candidate : grid.getCraftingService().getCraftingFor(key)) {
            if (candidate.getDefinition() instanceof AEItemKey item && ChanceOutputMarker.isMarked(item.toStack())
                    && key.equals(candidate.getPrimaryOutput().what())) {
                return candidate;
            }
        }
        return null;
    }

    /** Runs one tick of the top-up logic; an unexpected failure is logged (rate-limited) and never reaches the CPU tick. */
    public static void tick(TopUpJob job, ChanceTopUpState<IPatternDetails> state, ChanceJobMarks marks, IGrid grid,
            long now) {
        try {
            tickInner(job, state, marks, grid, now);
        } catch (RuntimeException e) {
            AelisPlanningLog.error("Chance top-up failed and was skipped for this tick: {}", e.toString());
        }
    }

    private static void tickInner(TopUpJob job, ChanceTopUpState<IPatternDetails> state, ChanceJobMarks marks,
            IGrid grid, long now) {
        if (!Config.ENABLE_CHANCE_OUTPUT_BONUS.get() || !Config.ENABLE_CHANCE_TOP_UP.get()) {
            return;
        }
        collectFollowUp(state, grid);
        AEKey key = job.finalKey();
        if (key == null || !marks.contains(key) || !job.tasksEmpty() || job.remaining() <= 0) {
            return;
        }
        long idleTicks = Config.CHANCE_TOP_UP_IDLE_SECONDS.get() * 20L * state.idleMultiplier();
        if (!ChanceTopUp.isIdle(now, state.lastActivity(), idleTicks)) {
            return;
        }
        IPatternDetails pattern = state.producerOf(key);
        if (pattern == null) {
            // After a reload the bookkeeping is gone: take the marked pattern that produces the output.
            pattern = findMarkedProducer(grid, key);
        }
        var server = ServerLifecycleHooks.getCurrentServer();
        if (pattern == null || server == null) {
            return;
        }

        // End of a wave: learn from what arrived. The outputs that did not come are only dropped below, once top-up
        // crafts can really be pushed, so a job that waits for inputs keeps showing what it is waiting for.
        long[] seen = state.endWave(pattern, key);
        var yieldData = ChanceYieldData.get(server);
        var patternKey = pattern.getDefinition();
        yieldData.record(patternKey, seen[0], seen[1]);
        double yield = yieldData.estimate(patternKey);

        state.seedPlannedIfUnknown(pattern, job.remaining());
        long allowed = state.allowedExtra(pattern, Config.CHANCE_TOP_UP_MAX_FACTOR.get());
        long wanted = ChanceTopUp.craftsNeeded(job.remaining(), yield, allowed);
        AppliedEnhancements.LOGGER.info("Chance top-up wave for {}: remaining {}, wave {} outputs / {} crafts, yield {}, allowed extra {}, wanted {}",
                key, job.remaining(), seen[0], seen[1], String.format("%.3f", yield), allowed, wanted);
        if (wanted <= 0) {
            AppliedEnhancements.LOGGER.info("Chance top-up limit reached for {} (remaining {}, yield {}, allowed extra {}); the job keeps waiting",
                    key, job.remaining(), String.format("%.3f", yield), allowed);
            state.touch(now);
            return;
        }

        var inputs = pattern.getInputs();
        var keys = new AEKey[inputs.length];
        var perCraft = new long[inputs.length];
        var available = new long[inputs.length];
        var storage = grid.getStorageService().getInventory();
        for (int i = 0; i < inputs.length; i++) {
            var possible = inputs[i].getPossibleInputs();
            if (possible.length == 0) {
                state.touch(now);
                return;
            }
            keys[i] = possible[0].what();
            perCraft[i] = possible[0].amount() * inputs[i].getMultiplier();
            available[i] = storage.extract(keys[i], perCraft[i] * wanted, Actionable.SIMULATE, IActionSource.empty());
        }
        long crafts = ChanceTopUp.affordableCrafts(wanted, perCraft, available);
        if (crafts <= 0) {
            // Wait and retry after another idle period (inputs may arrive in the meantime). Inputs the network can
            // craft (e.g. a dust ground from stock) are requested as an ordinary job; the next wave finds them.
            AppliedEnhancements.LOGGER.info("Chance top-up for {}: network storage lacks inputs, retrying later", key);
            requestMissingInputs(state, grid, keys, ChanceTopUp.missingForCrafts(wanted, perCraft, available), now,
                    idleTicks);
            state.touch(now);
            return;
        }
        long dropped = job.dropWaitingFor(key);
        for (int i = 0; i < inputs.length; i++) {
            long got = storage.extract(keys[i], perCraft[i] * crafts, Actionable.MODULATE, IActionSource.empty());
            if (got > 0) {
                job.inventory().insert(keys[i], got, Actionable.MODULATE);
            }
        }
        job.addCrafts(pattern, crafts);
        state.addExtra(pattern, crafts);
        state.touch(now);
        AppliedEnhancements.LOGGER.info("Chance top-up: {} more crafts for {} (yield {}, dropped {} stale, wave saw {} outputs from {} crafts)",
                crafts, key, String.format("%.3f", yield), dropped, seen[0], seen[1]);
    }
}
