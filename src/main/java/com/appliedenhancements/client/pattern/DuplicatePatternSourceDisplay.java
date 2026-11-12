package com.appliedenhancements.client.pattern;

import appeng.client.gui.me.patternaccess.PatternSlot;
import com.appliedenhancements.api.PatternSlotRef;
import com.appliedenhancements.client.pattern.DuplicatePatternRows.PatternSource;
import java.util.Collection;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Renders a compact source-machine badge on filtered pattern-result slots. */
public final class DuplicatePatternSourceDisplay {
    private static final int BADGE_SIZE = 8;

    private DuplicatePatternSourceDisplay() {
    }

    public static void renderBadges(
            GuiGraphicsExtractor graphics,
            Collection<Slot> menuSlots,
            Map<PatternSlotRef, PatternSource> displaySources) {
        if (displaySources.isEmpty()) {
            return;
        }

        for (Slot slot : menuSlots) {
            if (!(slot instanceof PatternSlot patternSlot) || patternSlot.getItem().isEmpty()) {
                continue;
            }
            PatternSource source = displaySources.get(new PatternSlotRef(
                    patternSlot.getMachineInv().getServerId(),
                    patternSlot.getContainerSlot()));
            if (source == null) {
                continue;
            }

            int x = patternSlot.x + 16 - BADGE_SIZE;
            int y = patternSlot.y;
            graphics.fill(x - 1, y, x + BADGE_SIZE, y + BADGE_SIZE + 1, 0xD0202733);

            ItemStack icon = source.machine().group().icon() == null
                    ? ItemStack.EMPTY
                    : source.machine().group().icon().getReadOnlyStack();
            // Was z 240 = above the pattern item in the slot (1.21 3D GUI). MC 26 is 2D:
            // a new stratum puts the badge content above the items drawn before it.
            graphics.nextStratum();
            if (!icon.isEmpty()) {
                graphics.pose().pushMatrix();
                graphics.pose().translate(x, y);
                graphics.pose().scale(0.5F, 0.5F);
                try {
                    graphics.item(icon, 0, 0);
                } finally {
                    graphics.pose().popMatrix();
                }
            } else {
                var font = Minecraft.getInstance().font;
                String fallback = source.machine().group().name().getString();
                fallback = fallback.isEmpty() ? "?" : fallback.substring(0, 1);
                graphics.pose().pushMatrix();
                graphics.pose().translate(x + 1, y + 1);
                graphics.pose().scale(0.5F, 0.5F);
                try {
                    graphics.text(font, fallback, 0, 0, 0xFFFFFFFF, false);
                } finally {
                    graphics.pose().popMatrix();
                }
            }
        }
    }
}
