package com.appliedenhancements.outputpull.client;

import com.appliedenhancements.AppliedEnhancements;
import com.appliedenhancements.outputpull.network.ProviderPullSidesPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import org.jetbrains.annotations.ApiStatus;

/** Client-side payload handler of the output-pull module (client-only event, like the shared ClientPayloadHandlers). */
@ApiStatus.Internal
@EventBusSubscriber(modid = AppliedEnhancements.MODID, value = Dist.CLIENT)
public final class OutputPullClientPayloads {
    private OutputPullClientPayloads() {
    }

    @SubscribeEvent
    public static void register(RegisterClientPayloadHandlersEvent event) {
        event.register(ProviderPullSidesPayload.TYPE, (payload, context) -> context.enqueueWork(
                () -> ClientProviderPullState.update(payload)));
    }
}
