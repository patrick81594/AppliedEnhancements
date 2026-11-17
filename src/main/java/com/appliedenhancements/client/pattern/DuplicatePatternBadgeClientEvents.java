package com.appliedenhancements.client.pattern;

import com.appliedenhancements.AppliedEnhancements;
import com.appliedenhancements.integration.ae2.DuplicatePatternSourceScreenBridge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Draws the duplicate-pattern source badges after the slots.
 *
 * <p>MC 26 draws a container screen in this order: labels (AE2's drawFG), then the slots, then this
 * event, then the carried item and tooltips. The GUI is 2D, so the last thing drawn is on top. In
 * 1.21.1 the badges were drawn in drawFG with z = 240; in MC 26 that put them under the pattern items.
 *
 * <p>{@code ContainerScreenEvent.Render.Foreground} fires at almost the same point but is deprecated
 * for removal since NeoForge 26.1.2, so this uses {@code ScreenEvent.Render.Foreground}. That one is
 * posted in screen coordinates: the bridge shifts to the GUI's top-left itself.
 */
@EventBusSubscriber(modid = AppliedEnhancements.MODID, value = Dist.CLIENT)
public final class DuplicatePatternBadgeClientEvents {
    private DuplicatePatternBadgeClientEvents() {
    }

    @SubscribeEvent
    public static void onScreenForeground(ScreenEvent.Render.Foreground event) {
        if (event.getScreen() instanceof DuplicatePatternSourceScreenBridge bridge) {
            bridge.appliedenhancements$renderDuplicateBadges(event.getGuiGraphics());
        }
    }
}
