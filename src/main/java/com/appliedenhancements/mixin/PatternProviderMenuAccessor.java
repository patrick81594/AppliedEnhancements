package com.appliedenhancements.mixin;

import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.menu.implementations.PatternProviderMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Feature G: the logic of the open pattern provider menu (server side). */
@Mixin(value = PatternProviderMenu.class, remap = false)
public interface PatternProviderMenuAccessor {
    @Accessor("logic")
    PatternProviderLogic appliedenhancements$getLogic();
}
