package com.appliedenhancements.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/**
 * Shows short feedback messages as toasts (top-right, dark panel, light text).
 *
 * <p>The action bar and chat are part of the HUD, which Minecraft draws BEFORE the open screen
 * (GameRenderer: HUD → screen → toasts), so terminals and the AELIS screen covered them. These messages
 * are sent while such a screen is open, so they use toasts, which are drawn after the screen.
 *
 * <p>One toast id per feature: a new message replaces the visible toast of the same feature instead of
 * stacking. Messages from the server arrive as {@code FeedbackToastPayload}.
 */
public final class FeedbackToasts {
    /** Which feature a message belongs to; picks the toast title and the toast slot. */
    public enum Kind {
        PATTERN_QUICK_MOVE("toast.appliedenhancements.pattern_quick_move.title"),
        ITEM_MENU("toast.appliedenhancements.item_menu.title"),
        CRAFTING_PLAN("gui.appliedenhancements.plan.title");

        private final String titleKey;
        private final SystemToast.SystemToastId id = new SystemToast.SystemToastId(); // 5 s

        Kind(String titleKey) {
            this.titleKey = titleKey;
        }
    }

    private FeedbackToasts() {
    }

    public static void show(Kind kind, Component message) {
        SystemToast.addOrUpdate(Minecraft.getInstance().getToastManager(), kind.id,
                Component.translatable(kind.titleKey), message);
    }
}
