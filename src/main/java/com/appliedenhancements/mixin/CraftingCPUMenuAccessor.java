package com.appliedenhancements.mixin;

import appeng.menu.me.crafting.CraftingCPUMenu;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Gives the server-side retry request access to the CPU the open crafting-status menu shows. */
@Mixin(value = CraftingCPUMenu.class, remap = false)
public interface CraftingCPUMenuAccessor {
    @Accessor("cpu")
    CraftingCPUCluster appliedenhancements$getCpu();
}
