package com.appliedenhancements.chance.client;

import appeng.client.gui.me.crafting.CraftingCPUScreen;
import com.appliedenhancements.AppliedEnhancements;
import com.appliedenhancements.chance.ChanceLog;
import com.appliedenhancements.chance.network.ChanceRetryPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Middle click on an item in the crafting-status screen: try the chance top-up of that item again. */
@EventBusSubscriber(modid = AppliedEnhancements.MODID, value = Dist.CLIENT)
public final class ChanceRetryClientEvents {
    private ChanceRetryClientEvents() {
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 2) {
            return;
        }
        ChanceLog.info("Chance retry: middle click on screen {}", event.getScreen().getClass().getName());
        if (!(event.getScreen() instanceof CraftingCPUScreen screen)) {
            return;
        }
        var hovered = screen.getStackUnderMouse(event.getMouseX(), event.getMouseY());
        if (hovered == null || hovered.stack() == null) {
            return;
        }
        ChanceLog.info("Chance retry: requesting a retry of {}", hovered.stack().what());
        ClientPacketDistributor.sendToServer(new ChanceRetryPayload(hovered.stack().what()));
        event.setCanceled(true);
    }
}
