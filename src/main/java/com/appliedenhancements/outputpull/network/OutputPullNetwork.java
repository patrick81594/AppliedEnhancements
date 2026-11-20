package com.appliedenhancements.outputpull.network;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Payload registration of the output-pull module (pattern provider side panel). Called from the shared
 * {@code NetworkHandler}, so the module owns its payload types and the shared class only knows this one entry point.
 * The client-side handler lives in {@code OutputPullClientPayloads} (client-only event).
 */
public final class OutputPullNetwork {
    private OutputPullNetwork() {
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(ProviderPullSideRequestPayload.TYPE, ProviderPullSideRequestPayload.STREAM_CODEC,
                ProviderPullSideRequestPayload::handle);
        registrar.playToClient(ProviderPullSidesPayload.TYPE, ProviderPullSidesPayload.STREAM_CODEC);
    }
}
