package com.appliedenhancements.network;

import com.appliedenhancements.AppliedEnhancements;
import com.appliedenhancements.client.FeedbackToasts;
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
        event.register(FeedbackToastPayload.TYPE, ClientPayloadHandlers::showFeedbackToast);
        event.register(ProviderPullSidesPayload.TYPE, (payload, context) -> context.enqueueWork(
                () -> com.appliedenhancements.client.provider.ClientProviderPullState.update(payload)));
    }

    private static void showFeedbackToast(FeedbackToastPayload payload, IPayloadContext context) {
        // Only our own messages: never turn an arbitrary key from the network into a toast.
        if (!FeedbackToastPayload.isAllowedKey(payload.messageKey())) {
            return;
        }
        FeedbackToasts.Kind kind = switch (payload.kind()) {
            case 0 -> FeedbackToasts.Kind.PATTERN_QUICK_MOVE; // FeedbackToastPayload.Kind.PATTERN_QUICK_MOVE
            case 1 -> FeedbackToasts.Kind.CRAFTING_PLAN;      // FeedbackToastPayload.Kind.CRAFTING_PLAN
            default -> null;
        };
        if (kind == null) {
            return;
        }
        context.enqueueWork(() -> FeedbackToasts.show(kind,
                Component.translatable(payload.messageKey(), payload.count())));
    }
}
