package com.appliedenhancements.outputpull.mixin;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.menu.AEBaseMenu;
import com.appliedenhancements.outputpull.client.ClientProviderPullState;
import com.appliedenhancements.outputpull.client.ProviderPullFaceButton;
import com.appliedenhancements.outputpull.client.ProviderPullPanel;
import com.appliedenhancements.outputpull.client.ProviderPullPanelToggleButton;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Feature G: "Pull Outputs" side panel in the pattern provider screen (AE2's {@link PatternProviderScreen} and
 * subclasses such as ExtendedAE's GuiExPatternProvider).
 * <p>
 * Targets {@link AEBaseScreen} because PatternProviderScreen declares none of the hooked methods (init, drawBG,
 * getExclusionZones, hasClickedOutside, extractRenderState); every hook first checks
 * {@code this instanceof PatternProviderScreen}. A left-toolbar button opens/closes the panel; the six face
 * buttons are ordinary widgets (so AE2 draws their tooltips and forwards right clicks to them).
 */
@Mixin(value = AEBaseScreen.class, remap = false)
public abstract class PatternProviderScreenPullPanelMixin<T extends AEBaseMenu> extends AbstractContainerScreen<T> {
    @Unique
    private ProviderPullPanelToggleButton appliedenhancements$pullToggle;
    @Unique
    private List<ProviderPullFaceButton> appliedenhancements$pullButtons = List.of();

    private PatternProviderScreenPullPanelMixin(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Shadow
    protected abstract <B extends Button> B addToLeftToolbar(B button);

    @Shadow
    public abstract boolean isHandlingRightClick();

    /** Cached: the screen's class never changes. Null until the first check. */
    @Unique
    private Boolean appliedenhancements$pullScreen;

    @Unique
    private boolean appliedenhancements$isPullScreen() {
        if (appliedenhancements$pullScreen == null) {
            appliedenhancements$pullScreen = appliedenhancements$isProviderScreen(getClass());
        }
        return appliedenhancements$pullScreen;
    }

    /**
     * AE2's provider screen and its subclasses (ExtendedAE), plus AdvancedAE's two provider screens. AdvancedAE's
     * screens cannot be referenced here (optional mod), so they are matched by name up the class hierarchy.
     */
    @Unique
    private boolean appliedenhancements$isProviderScreen(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            if (c == PatternProviderScreen.class) {
                return true;
            }
            String name = c.getName();
            if (name.equals("net.pedroksl.advanced_ae.client.gui.AdvPatternProviderScreen")
                    || name.equals("net.pedroksl.advanced_ae.client.gui.SmallAdvPatternProviderScreen")) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private Rect2i appliedenhancements$pullBounds() {
        return ProviderPullPanel.bounds(font, leftPos, topPos, imageWidth, width);
    }

    /** HEAD: before AE2's widgets.populateScreen, so the vertical toolbar lays the new button out with the rest. */
    @Inject(method = "init", at = @At("HEAD"))
    private void appliedenhancements$addPullToggle(CallbackInfo ci) {
        if (!appliedenhancements$isPullScreen() || appliedenhancements$pullToggle != null) {
            return;
        }
        appliedenhancements$pullToggle = addToLeftToolbar(
                new ProviderPullPanelToggleButton(btn -> appliedenhancements$togglePullPanel()));
    }

    /** TAIL: init() cleared all widgets, so the face buttons are (re)created at the new window position. */
    @Inject(method = "init", at = @At("TAIL"))
    private void appliedenhancements$addPullButtons(CallbackInfo ci) {
        if (!appliedenhancements$isPullScreen()) {
            return;
        }
        var bounds = appliedenhancements$pullBounds();
        var buttons = new ArrayList<ProviderPullFaceButton>(6);
        for (Direction face : Direction.values()) {
            var button = new ProviderPullFaceButton(face, this::isHandlingRightClick);
            button.setPosition(ProviderPullPanel.buttonX(bounds, face), ProviderPullPanel.buttonY(bounds, face));
            addRenderableWidget(button);
            buttons.add(button);
        }
        appliedenhancements$pullButtons = List.copyOf(buttons);
        appliedenhancements$updatePullButtons();
        if (ClientProviderPullState.isPanelOpen()) {
            ClientProviderPullState.request();
        }
    }

    @Unique
    private void appliedenhancements$togglePullPanel() {
        boolean open = !ClientProviderPullState.isPanelOpen();
        ClientProviderPullState.setPanelOpen(open);
        if (open) {
            ClientProviderPullState.request();
        }
        appliedenhancements$updatePullButtons();
    }

    @Unique
    private void appliedenhancements$updatePullButtons() {
        boolean open = ClientProviderPullState.isPanelOpen();
        for (var button : appliedenhancements$pullButtons) {
            button.visible = open;
            // Faces the provider doesn't push to are greyed out and not clickable; faces without a machine stay
            // clickable (the machine may be placed later).
            button.active = open && ClientProviderPullState.pushesTo(button.face());
        }
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void appliedenhancements$refreshPullButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTicks, CallbackInfo ci) {
        if (appliedenhancements$isPullScreen()) {
            appliedenhancements$updatePullButtons();
        }
    }

    /** Panel background; drawBG runs before the widgets, so the face buttons end up on top of it. */
    @Inject(method = "drawBG", at = @At("TAIL"))
    private void appliedenhancements$drawPullPanel(GuiGraphicsExtractor graphics, int offsetX, int offsetY,
            int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (appliedenhancements$isPullScreen() && ClientProviderPullState.isPanelOpen()) {
            ProviderPullPanel.draw(graphics, font, appliedenhancements$pullBounds());
        }
    }

    /** Keep JEI's item list away from the open panel. */
    // cancellable is required for setReturnValue, also at RETURN (without it Mixin throws CancellationException
    // the first time JEI asks for the zones while the panel is open; found in the TEST instance 2026-11-19).
    @Inject(method = "getExclusionZones", at = @At("RETURN"), cancellable = true)
    private void appliedenhancements$excludePullPanel(CallbackInfoReturnable<List<Rect2i>> cir) {
        if (appliedenhancements$isPullScreen() && ClientProviderPullState.isPanelOpen()) {
            var zones = new ArrayList<>(cir.getReturnValue());
            zones.add(appliedenhancements$pullBounds());
            cir.setReturnValue(zones);
        }
    }

    /** Clicks on the panel background are "inside" the screen (no item drop with a carried stack). */
    @Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
    private void appliedenhancements$pullPanelIsInside(double mouseX, double mouseY, int screenX, int screenY,
            CallbackInfoReturnable<Boolean> cir) {
        if (appliedenhancements$isPullScreen() && ClientProviderPullState.isPanelOpen()
                && ProviderPullPanel.contains(appliedenhancements$pullBounds(), mouseX, mouseY)) {
            cir.setReturnValue(false);
        }
    }
}
