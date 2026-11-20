package com.appliedenhancements.chance;

import com.mojang.serialization.Codec;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.appliedenhancements.AppliedEnhancements;

/**
 * Marks an encoded processing pattern as a chance-output pattern.
 *
 * The marker is a data component on the pattern item stack. A marked and an unmarked copy
 * of the same pattern are different item keys, so the CPU can tell them apart. The marker
 * has no effect unless {@code Config.ENABLE_CHANCE_OUTPUT_BONUS} is enabled.
 *
 * Absent (or false) means not marked, so existing patterns keep their current behaviour.
 */
public final class ChanceOutputMarker {
    private static final DeferredRegister<DataComponentType<?>> DR =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, AppliedEnhancements.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> MARKED =
            DR.register("chance_output_marked",
                    () -> DataComponentType.<Boolean>builder()
                            .persistent(Codec.BOOL)
                            .networkSynchronized(ByteBufCodecs.BOOL)
                            .build());

    private ChanceOutputMarker() {
    }

    /** Call once from the mod constructor, before registries are frozen. */
    public static void register(IEventBus modEventBus) {
        DR.register(modEventBus);
    }

    public static boolean isMarked(ItemStack stack) {
        return stack.getOrDefault(MARKED.get(), false);
    }

    /** Sets or clears the marker on the given stack and returns the same stack. */
    public static ItemStack withMarker(ItemStack stack, boolean marked) {
        if (marked) {
            stack.set(MARKED.get(), true);
        } else {
            stack.remove(MARKED.get());
        }
        return stack;
    }
}
