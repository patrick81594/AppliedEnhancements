package com.appliedenhancements.client.pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/**
 * Shows pattern quick-move feedback as a toast (top-right, dark panel, light text).
 *
 * <p>The action bar and chat are part of the HUD, which Minecraft draws before the open screen, so the
 * pattern terminal covered them. Toasts are drawn after the screen (GameRenderer: HUD → screen → toasts).
 * One fixed id: a new message replaces the visible toast instead of stacking.
 */
public final class PatternQuickMoveToasts {
    private static final SystemToast.SystemToastId ID = new SystemToast.SystemToastId(); // 5 s
    private static final Component TITLE =
            Component.translatable("toast.appliedenhancements.pattern_quick_move.title");

    private PatternQuickMoveToasts() {
    }

    public static void show(Component message) {
        SystemToast.addOrUpdate(Minecraft.getInstance().getToastManager(), ID, TITLE, message);
    }
}
