package com.appliedenhancements.outputpull;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.parts.automation.StackWorldBehaviors;
import com.appliedenhancements.Config;
import java.util.ArrayList;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * Feature G state and logic, shared by every pattern provider type that supports "Pull Outputs". Each provider
 * logic keeps one instance and forwards its hooks to it; the logic-specific parts (how a push is recorded, how the
 * logic is saved and ticked) stay in the Mixins, and everything else lives here so that AE2's and AdvancedAE's
 * providers behave the same.
 *
 * <p>Input protection (an unfiltered import bus drained a bottler's honey tank in the pack tests): a key that is
 * an input of ANY pattern on this provider ({@link Host#isPullInput}, items and fluids, substitutes included) is never
 * recorded and never pulled, even if it is also an output. Pulls never exceed what is owed for that face.
 */
public final class OutputPullState {
    /** NBT key of the saved state inside the provider's own save data. */
    public static final String TAG = "appliedenhancementsOutputPull";

    /** What the provider logic must offer; implemented by the Mixin on that logic. */
    public interface Host {
        /** The block entity (or part's block entity) the provider lives in; null while it is not set up. */
        @Nullable
        BlockEntity appliedenhancements$pullBlockEntity();

        /** True for keys that are inputs of any pattern on this provider. */
        boolean appliedenhancements$isPullInput(AEKey key);

        /** Wakes the grid's tick manager so the ticker runs while something is owed. */
        void appliedenhancements$alertPull();

        /** Inserts into the provider's return inventory (AE2 delivers it to the network / CPU). */
        long appliedenhancements$insertPullReturn(AEKey key, long amount, Actionable mode);

        IActionSource appliedenhancements$pullActionSource();

        /** The faces this provider pushes to. */
        Set<Direction> appliedenhancements$pullSides();

        /** Persists the provider after a setting changed. */
        void appliedenhancements$pullSave();
    }

    private final Host host;
    /** Pull mode per face, indexed by {@code Direction.get3DDataValue()}. */
    private final byte[] modes = new byte[6];
    /** Outputs still owed by the machine on each face. */
    private final KeyCounter[] owed = new KeyCounter[6];
    /** Game time of the last progress (record or arrival) per face, for the expiry; -1 = unknown after a load. */
    private final long[] lastProgress = new long[6];
    /** Amounts this state moved into the return inventory that have not reached the network yet. */
    private final KeyCounter pulledPending = new KeyCounter();

    public OutputPullState(Host host) {
        this.host = host;
    }

    // ---------------------------------------------------------------- settings

    public byte mode(Direction face) {
        return modes[face.get3DDataValue()];
    }

    public void setMode(Direction face, byte mode) {
        int i = face.get3DDataValue();
        modes[i] = ProviderOutputPull.isValid(mode) ? mode : ProviderOutputPull.OFF;
        if (modes[i] == ProviderOutputPull.OFF && owed[i] != null) {
            owed[i].reset();
        }
        host.appliedenhancements$pullSave();
    }

    /** Copy of the outputs still owed on a face (for tests and diagnostics). */
    public KeyCounter owedCopy(Direction face) {
        var copy = new KeyCounter();
        KeyCounter counter = owed[face.get3DDataValue()];
        if (counter != null) {
            copy.addAll(counter);
        }
        return copy;
    }

    // ---------------------------------------------------------------- recording owed outputs on push

    /** Called after a craft was pushed out on {@code face} (the provider's face, not the machine's side). */
    public void recordOwed(Direction face, IPatternDetails details) {
        int i = face.get3DDataValue();
        if (!Config.ENABLE_PROVIDER_OUTPUT_PULL.get() || modes[i] == ProviderOutputPull.OFF) {
            return;
        }
        boolean recorded = false;
        for (GenericStack output : details.getOutputs()) {
            if (output == null || output.amount() <= 0 || host.appliedenhancements$isPullInput(output.what())) {
                continue;
            }
            owedFor(i).add(output.what(), output.amount());
            recorded = true;
        }
        if (recorded) {
            lastProgress[i] = gameTime();
            host.appliedenhancements$alertPull();
        }
    }

    // ---------------------------------------------------------------- ticking

    public boolean hasOwed() {
        for (KeyCounter counter : owed) {
            if (counter != null && !counter.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Pulls what is owed from the machines; returns true when something moved into the return inventory. */
    public boolean pull() {
        if (!hasOwed()) {
            return false;
        }
        BlockEntity be = host.appliedenhancements$pullBlockEntity();
        if (be == null || !(be.getLevel() instanceof ServerLevel level) || !Config.ENABLE_PROVIDER_OUTPUT_PULL.get()) {
            if (!Config.ENABLE_PROVIDER_OUTPUT_PULL.get()) {
                clearAllOwed();
            }
            return false;
        }
        long now = level.getGameTime();
        long expiryTicks = Config.PROVIDER_OUTPUT_PULL_EXPIRY_SECONDS.get() * 20L;
        boolean moved = false;
        for (var face : Direction.values()) {
            int i = face.get3DDataValue();
            KeyCounter counter = owed[i];
            if (counter == null || counter.isEmpty()) {
                continue;
            }
            if (lastProgress[i] < 0) {
                lastProgress[i] = now; // first pull after loading the world
            }
            Direction machineSide = ProviderOutputPull.machineSide(face, modes[i]);
            if (machineSide == null || now - lastProgress[i] > expiryTicks) {
                counter.reset(); // face turned off, or nothing arrived for too long (cancelled job, chance output)
                continue;
            }
            var machinePos = be.getBlockPos().relative(face);
            if (level.getCapability(AECapabilities.ME_STORAGE, machinePos, machineSide) != null) {
                continue; // an ME interface / subnet: pulling would take network items, not machine outputs
            }
            var strategies = StackWorldBehaviors.createExternalStorageStrategies(level, machinePos, machineSide);
            var entries = new ArrayList<GenericStack>();
            for (var entry : counter) {
                entries.add(new GenericStack(entry.getKey(), entry.getLongValue()));
            }
            for (var entry : entries) {
                AEKey key = entry.what();
                if (host.appliedenhancements$isPullInput(key)) { // re-checked: patterns may have changed
                    counter.remove(key, entry.amount());
                    continue;
                }
                var strategy = strategies.get(key.getType());
                var storage = strategy == null ? null : strategy.createWrapper(false, () -> {
                });
                if (storage == null) {
                    continue;
                }
                IActionSource source = host.appliedenhancements$pullActionSource();
                long available = storage.extract(key, entry.amount(), Actionable.SIMULATE, source);
                if (available <= 0) {
                    continue;
                }
                long room = host.appliedenhancements$insertPullReturn(key, available, Actionable.SIMULATE);
                long amount = Math.min(available, room);
                if (amount <= 0) {
                    continue;
                }
                long extracted = storage.extract(key, amount, Actionable.MODULATE, source);
                if (extracted <= 0) {
                    continue;
                }
                long stored = host.appliedenhancements$insertPullReturn(key, extracted, Actionable.MODULATE);
                if (stored < extracted) { // should not happen after the simulation; give it back
                    storage.insert(key, extracted - stored, Actionable.MODULATE, source);
                }
                if (stored > 0) {
                    counter.remove(key, stored);
                    pulledPending.add(key, stored);
                    lastProgress[i] = now;
                    moved = true;
                }
            }
            counter.removeZeros();
        }
        return moved;
    }

    // ---------------------------------------------------------------- count-down when outputs arrive otherwise

    /** Every stack the return inventory hands to the network passes here (our pulls and machine push-backs). */
    public void countDownReturned(@Nullable GenericStack stack) {
        if (stack == null || stack.amount() <= 0) {
            return;
        }
        long amount = stack.amount();
        long ours = Math.min(amount, pulledPending.get(stack.what()));
        if (ours > 0) {
            pulledPending.remove(stack.what(), ours);
            pulledPending.removeZeros();
            amount -= ours; // already counted down when pulled
        }
        if (amount <= 0) {
            return;
        }
        long now = gameTime();
        for (int i = 0; i < 6 && amount > 0; i++) {
            KeyCounter counter = owed[i];
            if (counter == null) {
                continue;
            }
            long take = Math.min(amount, counter.get(stack.what()));
            if (take > 0) {
                counter.remove(stack.what(), take);
                counter.removeZeros();
                amount -= take;
                lastProgress[i] = now;
            }
        }
    }

    // ---------------------------------------------------------------- save / load

    public void write(ValueOutput output) {
        boolean anyMode = false;
        for (byte mode : modes) {
            anyMode |= mode != ProviderOutputPull.OFF;
        }
        if (!anyMode && !hasOwed()) {
            return;
        }
        var root = output.child(TAG);
        var owedList = root.childrenList("owed");
        for (int i = 0; i < 6; i++) {
            root.putInt("mode" + i, modes[i]);
            KeyCounter counter = owed[i];
            if (counter == null) {
                continue;
            }
            for (var entry : counter) {
                var child = owedList.addChild();
                child.putInt("face", i);
                child.putLong("amount", entry.getLongValue());
                entry.getKey().toTagGeneric(child.child("key"));
            }
        }
    }

    public void read(ValueInput input) {
        java.util.Arrays.fill(modes, ProviderOutputPull.OFF);
        clearAllOwed();
        input.child(TAG).ifPresent(root -> {
            for (int i = 0; i < 6; i++) {
                int mode = root.getIntOr("mode" + i, ProviderOutputPull.OFF);
                modes[i] = ProviderOutputPull.isValid(mode) ? (byte) mode : ProviderOutputPull.OFF;
            }
            for (var child : root.childrenListOrEmpty("owed")) {
                int face = child.getIntOr("face", -1);
                long amount = child.getLongOr("amount", 0L);
                var key = child.child("key").map(AEKey::fromTagGeneric).orElse(null);
                if (face >= 0 && face < 6 && amount > 0 && key != null) {
                    owedFor(face).add(key, amount);
                }
            }
            // The level is usually not set yet while a chunk loads (game time would read 0 and the first pull
            // would expire everything just loaded): mark "unknown" and start the clock at the first pull.
            java.util.Arrays.fill(lastProgress, -1L);
        });
    }

    // ---------------------------------------------------------------- helpers

    private KeyCounter owedFor(int face) {
        if (owed[face] == null) {
            owed[face] = new KeyCounter();
        }
        return owed[face];
    }

    private void clearAllOwed() {
        for (KeyCounter counter : owed) {
            if (counter != null) {
                counter.reset();
            }
        }
        pulledPending.reset();
    }

    private long gameTime() {
        BlockEntity be = host.appliedenhancements$pullBlockEntity();
        Level level = be == null ? null : be.getLevel();
        return level == null ? 0L : level.getGameTime();
    }

    /** Faces this state's provider pushes to; for the screen. */
    public Set<Direction> pushSides() {
        return host.appliedenhancements$pullSides();
    }
}
