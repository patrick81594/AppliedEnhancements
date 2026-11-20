package com.appliedenhancements.outputpull.client;

import appeng.client.gui.widgets.IconButton;
import appeng.util.Icon;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Left-toolbar button of the pattern provider screen that opens/closes the feature G "Pull Outputs" panel. */
public final class ProviderPullPanelToggleButton extends IconButton {
    public ProviderPullPanelToggleButton(OnPress onPress) {
        super(onPress);
    }

    @Override
    protected Icon getIcon() {
        // A machine icon (AE2's, 10x10 and centred in the 16x16 button) instead of the plain arrow, which read as "back".
        return Icon.S_MACHINE;
    }

    @Override
    public List<Component> getTooltipMessage() {
        return List.of(
                Component.translatable("gui.appliedenhancements.provider_pull.title"),
                Component.translatable("gui.appliedenhancements.provider_pull.button")
                        .withStyle(ChatFormatting.GRAY));
    }
}
