package com.appliedenhancements.mixin;

import com.appliedenhancements.integration.ae2.PatternQuickMoveScreenBridge;
import com.appliedenhancements.integration.ae2.NetworkItemContextMenuScreenBridge;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenQuickMoveMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void appliedenhancements$handleQuickMoveClick(
            MouseButtonEvent event,
            boolean doubleClick,
            CallbackInfoReturnable<Boolean> callback) {
        // MC 26: input arrives as an event; unpack it so the bridges keep their 1.21 shape.
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if ((Object) this instanceof NetworkItemContextMenuScreenBridge bridge
                && bridge.appliedenhancements$networkItemMenuMouseClicked(
                        mouseX, mouseY, button)) {
            callback.setReturnValue(true);
            return;
        }
        if ((Object) this instanceof PatternQuickMoveScreenBridge bridge
                && bridge.appliedenhancements$quickMoveMouseClicked(
                        mouseX, mouseY, button)) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void appliedenhancements$handleQuickMoveDrag(
            MouseButtonEvent event,
            double dragX,
            double dragY,
            CallbackInfoReturnable<Boolean> callback) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if ((Object) this instanceof PatternQuickMoveScreenBridge bridge
                && bridge.appliedenhancements$quickMoveMouseDragged(
                        mouseX, mouseY, button, dragX, dragY)) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void appliedenhancements$handleQuickMoveRelease(
            MouseButtonEvent event,
            CallbackInfoReturnable<Boolean> callback) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if ((Object) this instanceof PatternQuickMoveScreenBridge bridge
                && bridge.appliedenhancements$quickMoveMouseReleased(
                        mouseX, mouseY, button)) {
            callback.setReturnValue(true);
        }
    }

    // MC 26: Screen.render was renamed to extractRenderState (same parameters).
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void appliedenhancements$renderQuickMoveOverlay(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo callback) {
        if ((Object) this instanceof PatternQuickMoveScreenBridge bridge) {
            bridge.appliedenhancements$renderQuickMoveOverlay(
                    graphics, mouseX, mouseY, partialTick);
        }
        if ((Object) this instanceof NetworkItemContextMenuScreenBridge bridge) {
            bridge.appliedenhancements$renderNetworkItemMenu(
                    graphics, mouseX, mouseY, partialTick);
        }
    }
}
