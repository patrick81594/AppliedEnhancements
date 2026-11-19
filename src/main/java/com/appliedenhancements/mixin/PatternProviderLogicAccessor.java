package com.appliedenhancements.mixin;

import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Feature G: the host (block entity or part) of a pattern provider logic. */
@Mixin(value = PatternProviderLogic.class, remap = false)
public interface PatternProviderLogicAccessor {
    @Accessor("host")
    PatternProviderLogicHost appliedenhancements$getHost();
}
