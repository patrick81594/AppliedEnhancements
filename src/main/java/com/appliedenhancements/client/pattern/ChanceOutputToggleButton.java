package com.appliedenhancements.client.pattern;

import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.IconButton;
import appeng.util.Icon;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

/** Small "%" toggle next to the clear button of the processing encoding panel (D3 chance outputs). */
public final class ChanceOutputToggleButton extends IconButton {
    private static final int SIZE = 10;

    private final Consumer<Boolean> listener;
    private boolean selected;

    public ChanceOutputToggleButton(Consumer<Boolean> listener) {
        super(null);
        this.listener = listener;
        this.width = SIZE;
        this.height = SIZE;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        listener.accept(!selected);
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
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
        Icon background = isHovered()
                ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER
                : Icon.TOOLBAR_BUTTON_BACKGROUND;
        Blitter.icon(background).dest(getX(), getY(), SIZE, SIZE).blit(graphics);
        var font = Minecraft.getInstance().font;
        graphics.text(font, "%", getX() + (SIZE - font.width("%")) / 2, getY() + 1,
                selected ? 0xFF55FF55 : 0xFFE0E0E0, false);
    }

    @Override
    public Rect2i getTooltipArea() {
        return new Rect2i(getX(), getY(), SIZE, SIZE);
    }

    @Override
    public List<Component> getTooltipMessage() {
        return List.of(
                Component.translatable("gui.appliedenhancements.chance_output"),
                Component.translatable(selected
                        ? "gui.appliedenhancements.chance_output.enabled"
                        : "gui.appliedenhancements.chance_output.disabled"),
                Component.translatable("gui.appliedenhancements.chance_output.note")
                        .withStyle(ChatFormatting.GRAY));
    }
}
