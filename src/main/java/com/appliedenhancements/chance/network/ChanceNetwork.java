package com.appliedenhancements.chance.network;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Payload registration entry point of the chance module (called from the shared {@code NetworkHandler}). */
public final class ChanceNetwork {
    private ChanceNetwork() {
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(ChanceRetryPayload.TYPE, ChanceRetryPayload.STREAM_CODEC, ChanceRetryPayload::handle);
    }
}
