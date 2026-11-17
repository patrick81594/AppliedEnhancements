package com.appliedenhancements.integration.ae2;

import appeng.client.gui.me.patternaccess.PatternSlot;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Supplies source-machine tooltip lines and badges for compact duplicate pattern rows. */
public interface DuplicatePatternSourceScreenBridge {
    List<Component> appliedenhancements$getPatternSourceTooltip(PatternSlot slot);

    /**
     * Draws the source-machine badges. Must be called AFTER the slots were drawn: the MC 26 GUI is 2D,
     * so whatever is drawn later is on top (see DuplicatePatternBadgeClientEvents). Called in screen
     * coordinates; the implementation shifts to the GUI's top-left.
     */
    void appliedenhancements$renderDuplicateBadges(GuiGraphicsExtractor graphics);
}
