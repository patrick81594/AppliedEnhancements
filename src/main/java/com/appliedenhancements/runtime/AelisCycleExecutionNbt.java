package com.appliedenhancements.runtime;

import appeng.api.stacks.AEKey;
import com.appliedenhancements.api.AelisCycleExecutionPlan;
import com.appliedenhancements.api.AelisCycleRuntimeController;
import com.appliedenhancements.api.AelisCycleSeedPolicy;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;

public final class AelisCycleExecutionNbt {
    private static final int VERSION = 3;

    private AelisCycleExecutionNbt() {
    }

    /**
     * Same encoding as AE2 19's {@code AEKey.toTagGeneric(registries)}: {@link AEKey#CODEC} with
     * registry-aware NBT ops. AE2 26 only offers the ValueOutput variant, which uses the same
     * codec, so saves written before and after the port stay byte-compatible.
     */
    private static CompoundTag keyToTag(AEKey key, HolderLookup.Provider registries) {
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        return (CompoundTag) AEKey.CODEC.encodeStart(ops, key).getOrThrow();
    }

    /** Same decoding as AE2 19's {@code AEKey.fromTagGeneric}: {@code null} if it cannot be decoded. */
    private static AEKey keyFromTag(HolderLookup.Provider registries, CompoundTag tag) {
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        try {
            return AEKey.CODEC.decode(ops, tag).getOrThrow().getFirst();
        } catch (Exception e) {
            return null;
        }
    }

    public static CompoundTag write(
            AelisCycleRuntimeController runtime,
            HolderLookup.Provider registries) {
        var root = new CompoundTag();
        root.putInt("version", VERSION);
        var state = runtime.snapshot();
        root.putInt("stepIndex", state.stepIndex());
        root.putLong("remainingCrafts", state.remainingCrafts());
        root.putString("exactRemainingCrafts", state.exactRemainingCrafts().toString());
        root.put("pendingOutputs", writeAmounts(state.pendingOutputs(), registries));
        root.put("exactPendingOutputs", writeExactAmounts(state.exactPendingOutputs(), registries));
        root.putString("seedPolicy", runtime.plan().seedPolicy().name());

        AelisCycleExecutionPlan plan = runtime.plan();
        var steps = new ListTag();
        for (AelisCycleExecutionPlan.Step step : plan.steps()) {
            var tag = new CompoundTag();
            tag.put("pattern", keyToTag(step.patternDefinition(), registries));
            tag.putLong("crafts", step.crafts());
            tag.putString("exactCrafts", step.exactCrafts().toString());
            tag.put("inputs", writeAmounts(step.inputsPerCraft(), registries));
            tag.put("self", writeKeys(step.selfReplenishingInputs(), registries));
            steps.add(tag);
        }
        root.put("steps", steps);
        root.put("minimumSeeds", writeAmounts(plan.minimumSeeds(), registries));
        root.put("protectedKeys", writeKeys(plan.protectedKeys(), registries));
        if (plan.phase() != null) {
            var phase = new CompoundTag();
            phase.put("prerequisitePatterns", writeKeys(plan.phase().prerequisitePatterns(), registries));
            var outputs = new ListTag();
            for (var entry : plan.phase().outputsPerPattern().entrySet()) {
                var pattern = new CompoundTag();
                pattern.put("pattern", keyToTag(entry.getKey(), registries));
                pattern.put("outputs", writeAmounts(entry.getValue(), registries));
                outputs.add(pattern);
            }
            phase.put("outputsPerPattern", outputs);
            root.put("phase", phase);
        }
        return root;
    }

    public static AelisCycleRuntimeController read(
            CompoundTag root,
            HolderLookup.Provider registries) {
        if (root == null || root.getIntOr("version", 0) < 1 || root.getIntOr("version", 0) > VERSION) {
            return null;
        }
        try {
            var steps = new ArrayList<AelisCycleExecutionPlan.Step>();
            ListTag stepTags = root.getListOrEmpty("steps");
            for (int index = 0; index < stepTags.size(); index++) {
                CompoundTag tag = stepTags.getCompoundOrEmpty(index);
                AEKey pattern = keyFromTag(
                        registries, tag.getCompoundOrEmpty("pattern"));
                if (pattern == null) {
                    return null;
                }
                steps.add(new AelisCycleExecutionPlan.Step(
                        pattern,
                        root.getIntOr("version", 0) >= 3
                                ? new BigInteger(tag.getStringOr("exactCrafts", ""))
                                : BigInteger.valueOf(tag.getLongOr("crafts", 0L)),
                        readAmounts(tag.getListOrEmpty("inputs"), registries),
                        readKeys(tag.getListOrEmpty("self"), registries)));
            }
            AelisCycleSeedPolicy seedPolicy = root.getString("seedPolicy").isPresent() // = 1.21 contains(key, TAG_STRING)
                    ? AelisCycleSeedPolicy.valueOf(root.getStringOr("seedPolicy", ""))
                    : AelisCycleSeedPolicy.MAX_THROUGHPUT;
            AelisCycleExecutionPlan.Phase phase = null;
            if (root.getIntOr("version", 0) >= 2 && root.getCompound("phase").isPresent()) { // = 1.21 contains(key, TAG_COMPOUND)
                var phaseTag = root.getCompoundOrEmpty("phase");
                var outputs = new LinkedHashMap<AEKey, Map<AEKey, Long>>();
                var patternTags = phaseTag.getListOrEmpty("outputsPerPattern");
                for (int index = 0; index < patternTags.size(); index++) {
                    var patternTag = patternTags.getCompoundOrEmpty(index);
                    var pattern = keyFromTag(registries, patternTag.getCompoundOrEmpty("pattern"));
                    var amounts = readAmounts(patternTag.getListOrEmpty("outputs"), registries);
                    if (pattern == null || outputs.putIfAbsent(pattern, amounts) != null) {
                        throw new IllegalArgumentException("Invalid cycle phase output entry");
                    }
                }
                phase = new AelisCycleExecutionPlan.Phase(
                        readKeys(phaseTag.getListOrEmpty("prerequisitePatterns"), registries),
                        outputs);
            }
            var plan = new AelisCycleExecutionPlan(
                    steps,
                    readAmounts(
                            root.getListOrEmpty("minimumSeeds"), registries),
                    readKeys(
                            root.getListOrEmpty("protectedKeys"), registries),
                    seedPolicy,
                    phase);
            return AelisCycleRuntimeController.withCyclePhase(
                    plan,
                    new AelisCycleRuntimeController.State(
                            root.getIntOr("stepIndex", 0),
                            root.getIntOr("version", 0) >= 3
                                    ? new BigInteger(root.getStringOr("exactRemainingCrafts", ""))
                                    : BigInteger.valueOf(root.getLongOr("remainingCrafts", 0L)),
                            root.getIntOr("version", 0) >= 3
                                    ? readExactAmounts(root.getListOrEmpty("exactPendingOutputs"), registries)
                                    : root.getIntOr("version", 0) >= 2
                                    ? toExactAmounts(readAmounts(root.getListOrEmpty("pendingOutputs"), registries))
                                    : Map.of()));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static ListTag writeAmounts(
            Map<AEKey, Long> values,
            HolderLookup.Provider registries) {
        var result = new ListTag();
        for (var entry : values.entrySet()) {
            var tag = new CompoundTag();
            tag.put("key", keyToTag(entry.getKey(), registries));
            tag.putLong("amount", entry.getValue());
            result.add(tag);
        }
        return result;
    }

    private static ListTag writeExactAmounts(Map<AEKey, BigInteger> values, HolderLookup.Provider registries) {
        var result = new ListTag();
        for (var entry : values.entrySet()) {
            var tag = new CompoundTag();
            tag.put("key", keyToTag(entry.getKey(), registries));
            tag.putString("amount", entry.getValue().toString());
            result.add(tag);
        }
        return result;
    }

    private static Map<AEKey, BigInteger> readExactAmounts(ListTag values, HolderLookup.Provider registries) {
        var result = new LinkedHashMap<AEKey, BigInteger>();
        for (int index = 0; index < values.size(); index++) {
            var tag = values.getCompoundOrEmpty(index);
            AEKey key = keyFromTag(registries, tag.getCompoundOrEmpty("key"));
            var amount = new BigInteger(tag.getStringOr("amount", ""));
            if (key == null || amount.signum() <= 0 || result.putIfAbsent(key, amount) != null) {
                throw new IllegalArgumentException("Invalid exact cycle amount entry");
            }
        }
        return Map.copyOf(result);
    }

    private static Map<AEKey, BigInteger> toExactAmounts(Map<AEKey, Long> values) {
        var result = new LinkedHashMap<AEKey, BigInteger>();
        values.forEach((key, amount) -> result.put(key, BigInteger.valueOf(amount)));
        return Map.copyOf(result);
    }

    private static Map<AEKey, Long> readAmounts(
            ListTag values,
            HolderLookup.Provider registries) {
        var result = new LinkedHashMap<AEKey, Long>();
        for (int index = 0; index < values.size(); index++) {
            CompoundTag tag = values.getCompoundOrEmpty(index);
            AEKey key = keyFromTag(registries, tag.getCompoundOrEmpty("key"));
            long amount = tag.getLongOr("amount", 0L);
            if (key == null || amount <= 0 || result.putIfAbsent(key, amount) != null) {
                throw new IllegalArgumentException("Invalid cycle amount entry");
            }
        }
        return Map.copyOf(result);
    }

    private static ListTag writeKeys(
            Iterable<AEKey> values,
            HolderLookup.Provider registries) {
        var result = new ListTag();
        for (AEKey value : values) {
            var tag = new CompoundTag();
            tag.put("key", keyToTag(value, registries));
            result.add(tag);
        }
        return result;
    }

    private static java.util.Set<AEKey> readKeys(
            ListTag values,
            HolderLookup.Provider registries) {
        var result = new LinkedHashSet<AEKey>();
        for (int index = 0; index < values.size(); index++) {
            AEKey key = keyFromTag(
                    registries, values.getCompoundOrEmpty(index).getCompoundOrEmpty("key"));
            if (key == null || !result.add(key)) {
                throw new IllegalArgumentException("Invalid cycle key entry");
            }
        }
        return java.util.Set.copyOf(result);
    }
}
