package com.appliedenhancements.runtime;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
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

    private static IPatternDetails findMarkedProducer(IGrid grid, AEKey key) {
        for (var candidate : grid.getCraftingService().getCraftingFor(key)) {
            if (candidate.getDefinition() instanceof AEItemKey item && ChanceOutputMarker.isMarked(item.toStack())
                    && key.equals(candidate.getPrimaryOutput().what())) {
                return candidate;
            }
        }
        return null;
    }

    public static void tick(TopUpJob job, ChanceTopUpState<IPatternDetails> state, ChanceJobMarks marks, IGrid grid,
            long now) {
        if (!Config.ENABLE_CHANCE_OUTPUT_BONUS.get() || !Config.ENABLE_CHANCE_TOP_UP.get()) {
            return;
        }
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

        // End of a wave: drop what will not come, learn from what did.
        long dropped = job.dropWaitingFor(key);
        long[] seen = state.endWave(pattern, key);
        var yieldData = ChanceYieldData.get(server);
        var patternKey = pattern.getDefinition();
        yieldData.record(patternKey, seen[0], seen[1]);
        double yield = yieldData.estimate(patternKey);

        state.seedPlannedIfUnknown(pattern, job.remaining());
        long allowed = state.allowedExtra(pattern, Config.CHANCE_TOP_UP_MAX_FACTOR.get());
        long wanted = ChanceTopUp.craftsNeeded(job.remaining(), yield, allowed);
        AppliedEnhancements.LOGGER.info("Chance top-up wave for {}: remaining {}, wave {} outputs / {} crafts, yield {}, allowed extra {}, wanted {}, dropped {} stale",
                key, job.remaining(), seen[0], seen[1], String.format("%.3f", yield), allowed, wanted, dropped);
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
            // Wait and retry after another idle period (inputs may arrive in the meantime).
            AppliedEnhancements.LOGGER.info("Chance top-up for {}: network storage lacks inputs, retrying later", key);
            state.touch(now);
            return;
        }
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
