package com.appliedenhancements.mixin;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.AEBaseMenu;
import appeng.parts.encoding.EncodingMode;
import com.appliedenhancements.client.pattern.ChanceOutputToggleButton;
import com.appliedenhancements.runtime.ChanceOutputMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * D3: "chance outputs" toggle beside the clear button of the processing encoding panel.
 *
 * Targets {@link AEBaseScreen} (like the pull panel mixin) because PatternEncodingTermScreen does not
 * declare init(). AE2's widget container rejects ids missing from its style file, so the button is an
 * ordinary screen widget, positioned from the style of the clear button.
 */
@Mixin(value = AEBaseScreen.class, remap = false)
public abstract class PatternEncodingTermScreenChanceMixin<T extends AEBaseMenu> extends AbstractContainerScreen<T> {
    @Unique
    private ChanceOutputToggleButton appliedenhancements$chanceButton;

    @Shadow
    @Final
    protected ScreenStyle style;

    private PatternEncodingTermScreenChanceMixin(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void appliedenhancements$addChanceButton(CallbackInfo ci) {
        appliedenhancements$chanceButton = null;
        if (!((Object) this instanceof PatternEncodingTermScreen<?> term)
                || !(term.getMenu() instanceof ChanceOutputMenu menu)) {
            return;
        }
        var button = new ChanceOutputToggleButton(menu::appliedenhancements$setChanceOutput);
        button.visible = false;
        addRenderableWidget(button);
        appliedenhancements$chanceButton = button;
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void appliedenhancements$updateChanceButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        var button = appliedenhancements$chanceButton;
        if (button == null || !((Object) this instanceof PatternEncodingTermScreen<?> term)) {
            return;
        }
        boolean processing = term.getMenu().getMode() == EncodingMode.PROCESSING;
        button.visible = processing;
        button.active = processing;
        if (processing) {
            var clear = style.getWidget("processingClearPattern")
                    .resolve(new Rect2i(leftPos, topPos, imageWidth, imageHeight));
            button.setPosition(clear.getX() - 12, clear.getY());
            button.setSelected(((ChanceOutputMenu) term.getMenu()).appliedenhancements$isChanceOutput());
        }
    }
}
