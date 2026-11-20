package com.appliedenhancements.outputpull.client;

import appeng.client.gui.widgets.IconButton;
import appeng.util.Icon;
import com.appliedenhancements.outputpull.ProviderOutputPull;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/**
 * One provider face in the feature G "Pull Outputs" panel. Left click = next mode, right click = previous mode
 * (AE2 screens forward right clicks to widgets as left clicks and report them via
 * {@code AEBaseScreen.isHandlingRightClick()}, which {@code rightClick} reads). Client only.
 */
public final class ProviderPullFaceButton extends IconButton {
    private static final int BORDER = 0xFF373737;
    private static final int FILL_OFF = 0xFFA0A0A0;
    private static final int FILL_OFF_HOVER = 0xFFBDBDD6;
    private static final int FILL_ON = 0xFF4F8F4F;
    private static final int FILL_ON_HOVER = 0xFF66AA66;
    private static final int FILL_NO_MACHINE = 0xFF8B8B8B;
    private static final int FILL_NOT_PUSHING = 0xFF6B6B6B;

    private final Direction face;
    private final BooleanSupplier rightClick;

    public ProviderPullFaceButton(Direction face, BooleanSupplier rightClick) {
        super(btn -> {
        });
        this.face = face;
        this.rightClick = rightClick;
        this.width = ProviderPullPanel.BUTTON_SIZE;
        this.height = ProviderPullPanel.BUTTON_SIZE;
    }

    public Direction face() {
        return face;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        byte current = ClientProviderPullState.mode(face);
        byte next = rightClick.getAsBoolean()
                ? ProviderOutputPull.previous(current)
                : ProviderOutputPull.next(current);
        ClientProviderPullState.setMode(face, next);
    }

    @Override
    protected Icon getIcon() {
        return null;
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (!visible) {
            return;
        }
        int mode = ClientProviderPullState.mode(face);
        boolean pushing = ClientProviderPullState.pushesTo(face);
        boolean machine = ClientProviderPullState.hasMachine(face);
        boolean on = mode != ProviderOutputPull.OFF;
        boolean hovered = active && isHovered();

        int fill;
        if (!pushing) {
            fill = FILL_NOT_PUSHING;
        } else if (on) {
            fill = hovered ? FILL_ON_HOVER : FILL_ON;
        } else if (!machine) {
            fill = hovered ? FILL_OFF_HOVER : FILL_NO_MACHINE;
        } else {
            fill = hovered ? FILL_OFF_HOVER : FILL_OFF;
        }

        int x0 = getX();
        int y0 = getY();
        int x1 = x0 + width;
        int y1 = y0 + height;
        // MC 26 GUI is 2D: later draws are on top, so border → fill → text.
        graphics.fill(x0, y0, x1, y1, BORDER);
        graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill);

        var font = Minecraft.getInstance().font;

        // Face initial, half size, top-left corner.
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x0 + 2, y0 + 2);
            graphics.pose().scale(0.5F, 0.5F);
            graphics.text(font, ProviderPullPanel.directionInitial(face), 0, 0, 0xFF202020, false);
        } finally {
            graphics.pose().popMatrix();
        }

        // Mode code, centred.
        String code = ProviderPullPanel.modeCode(mode);
        int textX = x0 + (width - font.width(code)) / 2 + 1;
        int textY = y0 + (height - 8) / 2 + 1;
        boolean bright = pushing && machine;
        graphics.text(font, code, textX, textY, bright ? 0xFFFFFFFF : 0xFFD0D0D0, bright);
    }

    @Override
    public List<Component> getTooltipMessage() {
        int mode = ClientProviderPullState.mode(face);
        var lines = new ArrayList<Component>();
        lines.add(Component.translatable("gui.appliedenhancements.provider_pull.face",
                ProviderPullPanel.directionName(face)));
        lines.add(Component.translatable("gui.appliedenhancements.provider_pull.mode",
                ProviderPullPanel.modeName(mode)));
        if (ClientProviderPullState.current() != null) {
            if (!ClientProviderPullState.pushesTo(face)) {
                lines.add(Component.translatable("gui.appliedenhancements.provider_pull.not_pushing")
                        .withStyle(ChatFormatting.RED));
            } else if (ClientProviderPullState.hasMachine(face)) {
                lines.add(Component.translatable("gui.appliedenhancements.provider_pull.machine")
                        .withStyle(ChatFormatting.GREEN));
            } else {
                lines.add(Component.translatable("gui.appliedenhancements.provider_pull.no_machine")
                        .withStyle(ChatFormatting.YELLOW));
            }
        }
        lines.add(Component.translatable("gui.appliedenhancements.provider_pull.click_hint")
                .withStyle(ChatFormatting.GRAY));
        return List.copyOf(lines);
    }
}
