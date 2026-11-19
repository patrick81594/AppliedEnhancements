package com.appliedenhancements.mixin;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import appeng.parts.automation.StackWorldBehaviors;
import com.appliedenhancements.Config;
import com.appliedenhancements.provider.ProviderOutputPull;
import com.appliedenhancements.provider.ProviderOutputPullHost;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.ArrayList;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Feature G on AE2's pattern provider logic (also used by ExtendedAE's extended pattern provider): after a craft
 * is pushed to a machine, remember the owed outputs per face; while something is owed, pull exactly those outputs
 * back out of the machine (from the configured machine side) into the provider's return inventory, which AE2 then
 * delivers to the network / crafting CPU as if the machine had pushed them back.
 *
 * <p>Input protection (an unfiltered import bus drained a bottler's honey tank in the pack tests): a key that is
 * an input of ANY pattern on this provider ({@code patternInputs}, items and fluids, substitutes included) is never
 * recorded and never pulled, even if it is also an output. Pulls never exceed what is owed for that face.
 */
@Mixin(value = PatternProviderLogic.class, remap = false)
public abstract class PatternProviderLogicPullMixin implements ProviderOutputPullHost {
    @Unique private static final String APPLIEDENHANCEMENTS_PULL_TAG = "appliedenhancementsOutputPull";

    @Shadow @Final private PatternProviderLogicHost host;
    @Shadow @Final private IManagedGridNode mainNode;
    @Shadow @Final private java.util.Set<AEKey> patternInputs;
    @Shadow private Direction sendDirection;
    @Shadow @Final private PatternProviderReturnInventory returnInv;
    @Shadow @Final private IActionSource actionSource;

    @Shadow
    protected abstract Set<Direction> getActiveSides();

    /** Pull mode per face, indexed by {@code Direction.get3DDataValue()}. */
    @Unique private final byte[] appliedenhancements$pullModes = new byte[6];
    /** Outputs still owed by the machine on each face. */
    @Unique private final KeyCounter[] appliedenhancements$owed = new KeyCounter[6];
    /** Game time of the last progress (record or arrival) per face, for the expiry. */
    @Unique private final long[] appliedenhancements$lastProgress = new long[6];
    /** Amounts this mixin moved into returnInv that have not reached the network yet (so they are not counted twice). */
    @Unique private final KeyCounter appliedenhancements$pulledPending = new KeyCounter();

    // ---------------------------------------------------------------- recording owed outputs on push

    @WrapOperation(method = "pushPattern", at = @At(value = "INVOKE",
            target = "Lappeng/api/implementations/blockentities/ICraftingMachine;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;Lnet/minecraft/core/Direction;)Z"))
    private boolean appliedenhancements$recordMachinePush(ICraftingMachine machine, IPatternDetails details,
            KeyCounter[] inputs, Direction machineSide, Operation<Boolean> original) {
        boolean pushed = original.call(machine, details, inputs, machineSide);
        if (pushed) {
            appliedenhancements$recordOwed(machineSide.getOpposite(), details);
        }
        return pushed;
    }

    @Inject(method = "pushPattern", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
            target = "Lappeng/helpers/patternprovider/PatternProviderLogic;sendDirection:Lnet/minecraft/core/Direction;",
            shift = At.Shift.AFTER))
    private void appliedenhancements$recordInventoryPush(IPatternDetails details, KeyCounter[] inputs,
            CallbackInfoReturnable<Boolean> callback) {
        if (sendDirection != null) {
            appliedenhancements$recordOwed(sendDirection, details);
        }
    }

    @Unique
    private void appliedenhancements$recordOwed(Direction face, IPatternDetails details) {
        int i = face.get3DDataValue();
        if (!Config.ENABLE_PROVIDER_OUTPUT_PULL.get() || appliedenhancements$pullModes[i] == ProviderOutputPull.OFF) {
            return;
        }
        boolean recorded = false;
        for (GenericStack output : details.getOutputs()) {
            if (output == null || output.amount() <= 0 || appliedenhancements$isPatternInput(output.what())) {
                continue;
            }
            appliedenhancements$owedFor(i).add(output.what(), output.amount());
            recorded = true;
        }
        if (recorded) {
            appliedenhancements$lastProgress[i] = appliedenhancements$gameTime();
            mainNode.ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
        }
    }

    // ---------------------------------------------------------------- ticking: only while something is owed

    @ModifyReturnValue(method = "hasWorkToDo", at = @At("RETURN"))
    private boolean appliedenhancements$workWhileOwed(boolean original) {
        return original || appliedenhancements$hasOwed();
    }

    @ModifyReturnValue(method = "doWork", at = @At("RETURN"))
    private boolean appliedenhancements$pullOwedOutputs(boolean original) {
        // Bitwise OR on purpose: always try to pull.
        return original | appliedenhancements$pull();
    }

    @Unique
    private boolean appliedenhancements$pull() {
        if (!appliedenhancements$hasOwed()) {
            return false;
        }
        var be = host.getBlockEntity();
        if (!(be.getLevel() instanceof ServerLevel level) || !Config.ENABLE_PROVIDER_OUTPUT_PULL.get()) {
            if (!Config.ENABLE_PROVIDER_OUTPUT_PULL.get()) {
                appliedenhancements$clearAllOwed();
            }
            return false;
        }
        long now = level.getGameTime();
        long expiryTicks = Config.PROVIDER_OUTPUT_PULL_EXPIRY_SECONDS.get() * 20L;
        boolean moved = false;
        for (var face : Direction.values()) {
            int i = face.get3DDataValue();
            KeyCounter owed = appliedenhancements$owed[i];
            if (owed == null || owed.isEmpty()) {
                continue;
            }
            if (appliedenhancements$lastProgress[i] < 0) {
                appliedenhancements$lastProgress[i] = now; // first pull after loading the world
            }
            Direction machineSide = ProviderOutputPull.machineSide(face, appliedenhancements$pullModes[i]);
            if (machineSide == null || now - appliedenhancements$lastProgress[i] > expiryTicks) {
                owed.reset(); // face turned off, or nothing arrived for too long (cancelled job, chance output)
                continue;
            }
            var machinePos = be.getBlockPos().relative(face);
            if (level.getCapability(AECapabilities.ME_STORAGE, machinePos, machineSide) != null) {
                continue; // an ME interface / subnet: pulling would take network items, not machine outputs
            }
            var strategies = StackWorldBehaviors.createExternalStorageStrategies(level, machinePos, machineSide);
            var entries = new ArrayList<GenericStack>();
            for (var entry : owed) {
                entries.add(new GenericStack(entry.getKey(), entry.getLongValue()));
            }
            for (var entry : entries) {
                AEKey key = entry.what();
                if (appliedenhancements$isPatternInput(key)) { // re-checked: patterns may have changed
                    owed.remove(key, entry.amount());
                    continue;
                }
                var strategy = strategies.get(key.getType());
                var storage = strategy == null ? null : strategy.createWrapper(false, () -> {
                });
                if (storage == null) {
                    continue;
                }
                long available = storage.extract(key, entry.amount(), Actionable.SIMULATE, actionSource);
                if (available <= 0) {
                    continue;
                }
                long room = returnInv.insert(key, available, Actionable.SIMULATE, actionSource);
                long amount = Math.min(available, room);
                if (amount <= 0) {
                    continue;
                }
                long extracted = storage.extract(key, amount, Actionable.MODULATE, actionSource);
                if (extracted <= 0) {
                    continue;
                }
                long stored = returnInv.insert(key, extracted, Actionable.MODULATE, actionSource);
                if (stored < extracted) { // should not happen after the simulation; give it back
                    storage.insert(key, extracted - stored, Actionable.MODULATE, actionSource);
                }
                if (stored > 0) {
                    owed.remove(key, stored);
                    appliedenhancements$pulledPending.add(key, stored);
                    appliedenhancements$lastProgress[i] = now;
                    moved = true;
                }
            }
            owed.removeZeros();
        }
        return moved;
    }

    // ---------------------------------------------------------------- count-down when outputs arrive otherwise

    /** Every stack the return inventory hands to the network passes here (our pulls and machine push-backs). */
    @Inject(method = "onStackReturnedToNetwork", at = @At("HEAD"))
    private void appliedenhancements$countDownReturned(GenericStack stack, CallbackInfo callback) {
        if (stack == null || stack.amount() <= 0) {
            return;
        }
        long amount = stack.amount();
        long ours = Math.min(amount, appliedenhancements$pulledPending.get(stack.what()));
        if (ours > 0) {
            appliedenhancements$pulledPending.remove(stack.what(), ours);
            appliedenhancements$pulledPending.removeZeros();
            amount -= ours; // already counted down when pulled
        }
        if (amount <= 0) {
            return;
        }
        long now = appliedenhancements$gameTime();
        for (int i = 0; i < 6 && amount > 0; i++) {
            KeyCounter owed = appliedenhancements$owed[i];
            if (owed == null) {
                continue;
            }
            long take = Math.min(amount, owed.get(stack.what()));
            if (take > 0) {
                owed.remove(stack.what(), take);
                owed.removeZeros();
                amount -= take;
                appliedenhancements$lastProgress[i] = now;
            }
        }
    }

    // ---------------------------------------------------------------- save / load

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void appliedenhancements$writePull(ValueOutput output, CallbackInfo callback) {
        boolean anyMode = false;
        for (byte mode : appliedenhancements$pullModes) {
            anyMode |= mode != ProviderOutputPull.OFF;
        }
        if (!anyMode && !appliedenhancements$hasOwed()) {
            return;
        }
        var root = output.child(APPLIEDENHANCEMENTS_PULL_TAG);
        var owedList = root.childrenList("owed");
        for (int i = 0; i < 6; i++) {
            root.putInt("mode" + i, appliedenhancements$pullModes[i]);
            KeyCounter owed = appliedenhancements$owed[i];
            if (owed == null) {
                continue;
            }
            for (var entry : owed) {
                var child = owedList.addChild();
                child.putInt("face", i);
                child.putLong("amount", entry.getLongValue());
                entry.getKey().toTagGeneric(child.child("key"));
            }
        }
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void appliedenhancements$readPull(ValueInput input, CallbackInfo callback) {
        java.util.Arrays.fill(appliedenhancements$pullModes, ProviderOutputPull.OFF);
        appliedenhancements$clearAllOwed();
        input.child(APPLIEDENHANCEMENTS_PULL_TAG).ifPresent(root -> {
            for (int i = 0; i < 6; i++) {
                int mode = root.getIntOr("mode" + i, ProviderOutputPull.OFF);
                appliedenhancements$pullModes[i] = ProviderOutputPull.isValid(mode) ? (byte) mode : ProviderOutputPull.OFF;
            }
            for (var child : root.childrenListOrEmpty("owed")) {
                int face = child.getIntOr("face", -1);
                long amount = child.getLongOr("amount", 0L);
                var key = child.child("key").map(AEKey::fromTagGeneric).orElse(null);
                if (face >= 0 && face < 6 && amount > 0 && key != null) {
                    appliedenhancements$owedFor(face).add(key, amount);
                }
            }
            // The level is usually not set yet while a chunk loads (game time would read 0 and the first pull
            // would expire everything just loaded): mark "unknown" and start the clock at the first pull.
            java.util.Arrays.fill(appliedenhancements$lastProgress, -1L);
        });
    }

    // ---------------------------------------------------------------- ProviderOutputPullHost

    @Override
    public byte appliedenhancements$getPullMode(Direction face) {
        return appliedenhancements$pullModes[face.get3DDataValue()];
    }

    @Override
    public void appliedenhancements$setPullMode(Direction face, byte mode) {
        int i = face.get3DDataValue();
        appliedenhancements$pullModes[i] = ProviderOutputPull.isValid(mode) ? mode : ProviderOutputPull.OFF;
        if (appliedenhancements$pullModes[i] == ProviderOutputPull.OFF && appliedenhancements$owed[i] != null) {
            appliedenhancements$owed[i].reset();
        }
        host.saveChanges();
    }

    @Override
    public KeyCounter appliedenhancements$getOwedOutputs(Direction face) {
        var copy = new KeyCounter();
        KeyCounter owed = appliedenhancements$owed[face.get3DDataValue()];
        if (owed != null) {
            copy.addAll(owed);
        }
        return copy;
    }

    @Override
    public Set<Direction> appliedenhancements$getPushFaces() {
        return getActiveSides();
    }

    // ---------------------------------------------------------------- helpers

    @Unique
    private boolean appliedenhancements$isPatternInput(AEKey key) {
        return patternInputs.contains(key.dropSecondary());
    }

    @Unique
    private KeyCounter appliedenhancements$owedFor(int face) {
        if (appliedenhancements$owed[face] == null) {
            appliedenhancements$owed[face] = new KeyCounter();
        }
        return appliedenhancements$owed[face];
    }

    @Unique
    private boolean appliedenhancements$hasOwed() {
        for (KeyCounter owed : appliedenhancements$owed) {
            if (owed != null && !owed.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private void appliedenhancements$clearAllOwed() {
        for (KeyCounter owed : appliedenhancements$owed) {
            if (owed != null) {
                owed.reset();
            }
        }
        appliedenhancements$pulledPending.reset();
    }

    @Unique
    private long appliedenhancements$gameTime() {
        var level = host.getBlockEntity().getLevel();
        return level == null ? 0L : level.getGameTime();
    }
}
