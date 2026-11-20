package com.appliedenhancements.chance;

import appeng.api.stacks.AEKey;
import com.appliedenhancements.AppliedEnhancements;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Learned yield of marked patterns, saved with the world: for every marked pattern (its item key, which
 * includes the marker and the encoded recipe) how many outputs came from how many crafts.
 */
public final class ChanceYieldData extends SavedData {
    private record Entry(AEKey pattern, long outputs, long crafts) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(b -> b.group(
                AEKey.CODEC.fieldOf("pattern").forGetter(Entry::pattern),
                Codec.LONG.fieldOf("outputs").forGetter(Entry::outputs),
                Codec.LONG.fieldOf("crafts").forGetter(Entry::crafts)).apply(b, Entry::new));
    }

    private static final Codec<ChanceYieldData> CODEC = RecordCodecBuilder.create(b -> b.group(
            Entry.CODEC.listOf().fieldOf("patterns").forGetter(ChanceYieldData::entries))
            .apply(b, ChanceYieldData::new));

    private static final SavedDataType<ChanceYieldData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(AppliedEnhancements.MOD_ID, "chance_yield"),
            ChanceYieldData::new, CODEC);

    private final Map<AEKey, long[]> stats = new HashMap<>();

    private ChanceYieldData() {
    }

    private ChanceYieldData(List<Entry> entries) {
        for (var entry : entries) {
            stats.put(entry.pattern(), new long[] {entry.outputs(), entry.crafts()});
        }
    }

    private List<Entry> entries() {
        var result = new ArrayList<Entry>();
        stats.forEach((key, value) -> result.add(new Entry(key, value[0], value[1])));
        return result;
    }

    public static ChanceYieldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** Adds the outputs and crafts of an ended wave. */
    public void record(AEKey pattern, long outputs, long crafts) {
        if (crafts <= 0) {
            return;
        }
        var value = stats.computeIfAbsent(pattern, k -> new long[2]);
        value[0] += Math.max(outputs, 0);
        value[1] += crafts;
        setDirty();
    }

    public double estimate(AEKey pattern) {
        var value = stats.get(pattern);
        return value == null ? ChanceTopUp.estimateYield(0, 0) : ChanceTopUp.estimateYield(value[0], value[1]);
    }
}
