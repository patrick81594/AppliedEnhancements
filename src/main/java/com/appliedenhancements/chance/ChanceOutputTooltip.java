package com.appliedenhancements.chance;

import com.appliedenhancements.Config;
import net.minecraft.network.chat.Component;

/**
 * The text of the "chance outputs" line that is added to a marked processing pattern's tooltip.
 *
 * <p>
 * AE2 builds a pattern tooltip as a {@code PatternDetailsTooltip} and shows its properties (for example "Substitutes
 * alternate items") in its own block; the Mixin {@code EncodedPatternItemChanceTooltipMixin} adds this line as one more
 * property in the same way, so it shows on its own line and nothing AE2 or other mods already show is changed.
 */
public final class ChanceOutputTooltip {
    public static final String KEY_ON = "tooltip.appliedenhancements.chance_output";
    public static final String KEY_INACTIVE = "tooltip.appliedenhancements.chance_output.inactive";

    private ChanceOutputTooltip() {
    }

    /** True when the feature is switched on in the config; true while the config is not loaded yet. */
    public static boolean featureEnabled() {
        try {
            return Config.ENABLE_CHANCE_OUTPUT_BONUS.get();
        } catch (RuntimeException configNotLoaded) {
            return true;
        }
    }

    /**
     * The property to add, or {@code null} for an unmarked pattern. A marked pattern whose feature is switched off
     * in the config still says so, so the player knows why it has no effect.
     */
    public static Component property(boolean marked, boolean featureEnabledInConfig) {
        if (!marked) {
            return null;
        }
        return Component.translatable(featureEnabledInConfig ? KEY_ON : KEY_INACTIVE);
    }
}
