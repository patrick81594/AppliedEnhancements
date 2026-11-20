package com.appliedenhancements.chance.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsTooltip;
import appeng.crafting.pattern.AEProcessingPattern;
import com.appliedenhancements.chance.ChanceOutputMarker;
import com.appliedenhancements.chance.ChanceOutputTooltip;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Shows on a chance-marked processing pattern's tooltip that the setting is on, the same way AE2 shows "Substitutes
 * alternate items": AE2 asks the pattern details for a {@link PatternDetailsTooltip} and renders its properties in a
 * block of their own. We add one property to that object after AE2 built it, so the line stays separate from, and
 * after, everything already there.
 */
@Mixin(targets = "appeng.crafting.pattern.EncodedPatternItem", remap = false)
public abstract class EncodedPatternItemChanceTooltipMixin {
    @WrapOperation(method = "appendHoverText", require = 1, at = @At(value = "INVOKE",
            target = "Lappeng/api/crafting/IPatternDetails;getTooltip(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/TooltipFlag;)Lappeng/api/crafting/PatternDetailsTooltip;"))
    private PatternDetailsTooltip appliedenhancements$addChanceProperty(IPatternDetails details, Level level,
            TooltipFlag flags, Operation<PatternDetailsTooltip> original) {
        PatternDetailsTooltip tooltip = original.call(details, level, flags);
        if (tooltip != null && details instanceof AEProcessingPattern) {
            var property = ChanceOutputTooltip.property(
                    ChanceOutputMarker.isMarked(details.getDefinition().getReadOnlyStack()),
                    ChanceOutputTooltip.featureEnabled());
            if (property != null) {
                tooltip.addProperty(property);
            }
        }
        return tooltip;
    }
}
