package com.appliedenhancements.mixin;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.menu.me.crafting.CraftingStatusMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The CPU picked in the crafting-status screen (any CPU type, including AdvancedAE's Quantum CPU). */
@Mixin(value = CraftingStatusMenu.class, remap = false)
public interface CraftingStatusMenuAccessor {
    @Accessor("selectedCpu")
    ICraftingCPU appliedenhancements$getSelectedCpu();
}
