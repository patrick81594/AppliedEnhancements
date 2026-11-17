package com.appliedenhancements.network;

import com.appliedenhancements.AppliedEnhancements;
import com.appliedenhancements.client.pattern.PatternQuickMoveToasts;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.ApiStatus;

/**
 * Registers the client-side handlers of payloads whose server side is registered in {@link NetworkHandler}.
 *
 * <p>NeoForge 26 splits payload registration: common code declares the payload (and the server handler),
 * and the client declares its handler in this client-only event, as AE2 26 does. This class is only loaded
 * on the physical client ({@code Dist.CLIENT}).
 */
@ApiStatus.Internal
@EventBusSubscriber(modid = AppliedEnhancements.MODID, value = Dist.CLIENT)
public final class ClientPayloadHandlers {
    private ClientPayloadHandlers() {
    }

    @SubscribeEvent
    public static void register(RegisterClientPayloadHandlersEvent event) {
        event.register(ExactCraftingAmountPayload.TYPE, ExactCraftingAmountPayload::handleOnClient);
        event.register(PatternQuickMoveResultPayload.TYPE, ClientPayloadHandlers::showQuickMoveResult);
    }

    private static void showQuickMoveResult(PatternQuickMoveResultPayload payload, IPayloadContext context) {
        // Only our own batch-move messages: never turn an arbitrary key from the network into a toast.
        if (!payload.messageKey().startsWith(PatternQuickMoveResultPayload.KEY_PREFIX)) {
            return;
        }
        context.enqueueWork(() -> PatternQuickMoveToasts.show(
                Component.translatable(payload.messageKey(), payload.count())));
    }
}
