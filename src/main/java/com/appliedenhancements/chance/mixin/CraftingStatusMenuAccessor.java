package com.appliedenhancements.chance.mixin;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.menu.me.crafting.CraftingStatusMenu;
import java.util.WeakHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The crafting-status menu numbers its CPUs; the selected one is {@code getSelectedCpuSerial()}. (Its own
 * {@code selectedCpu} field is never assigned by AE2, so it cannot be used.)
 */
@Mixin(value = CraftingStatusMenu.class, remap = false)
public interface CraftingStatusMenuAccessor {
    @Accessor("cpuSerialMap")
    WeakHashMap<ICraftingCPU, Integer> appliedenhancements$getCpuSerialMap();
}
