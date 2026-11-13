package com.appliedenhancements.network;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Handles network packet registration for Applied Enhancements.
 */
public final class NetworkHandler {
    private NetworkHandler() {
    }

    public static void register(PayloadRegistrar registrar) {
        // NeoForge 26: this 3-argument form registers the SERVER handler only. The client handler is
        // registered in ClientPayloadHandlers; without it the client refuses to finish loading
        // ("Some clientbound payloads are missing client-side handlers").
        registrar.playBidirectional(ExactCraftingAmountPayload.TYPE, ExactCraftingAmountPayload.STREAM_CODEC,
                ExactCraftingAmountPayload::handleOnServer);
        registrar.playToServer(AelisPlanGraphRequest.TYPE, AelisPlanGraphRequest.STREAM_CODEC, AelisPlanGraphRequest::handle);
        registrar.playToClient(AelisPlanGraphPayload.TYPE, AelisPlanGraphPayload.STREAM_CODEC, AelisPlanGraphPayload::handle);
        registrar.playToServer(ForceCraftingStartPayload.TYPE, ForceCraftingStartPayload.STREAM_CODEC,
                ForceCraftingStartPayload::handle);
        registrar.playToServer(
                PatternBatchMovePayload.TYPE,
                PatternBatchMovePayload.STREAM_CODEC,
                PatternBatchMovePayload::handle
        );
        registrar.playToServer(
                NetworkItemExtractPayload.TYPE,
                NetworkItemExtractPayload.STREAM_CODEC,
                NetworkItemExtractPayload::handle
        );
    }
}
