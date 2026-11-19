package com.appliedenhancements.client.provider;

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
        // Arrow pointing back into the provider ("pull back"); right arrow while the panel is open (= close it).
        return ClientProviderPullState.isPanelOpen() ? Icon.ARROW_RIGHT : Icon.ARROW_LEFT;
    }

    @Override
    public List<Component> getTooltipMessage() {
        return List.of(
                Component.translatable("gui.appliedenhancements.provider_pull.title"),
                Component.translatable("gui.appliedenhancements.provider_pull.button")
                        .withStyle(ChatFormatting.GRAY));
    }
}
