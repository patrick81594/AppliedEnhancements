package com.appliedenhancements.network;

import com.appliedenhancements.AppliedEnhancements;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: feature G state of the pattern provider whose screen is open. Pure data (also loaded on a
 * dedicated server); the client handler lives in {@link ClientPayloadHandlers}.
 *
 * @param modes       pull mode per provider face, indexed by {@code Direction.get3DDataValue()} (6 entries)
 * @param pushFaces   bit mask (1 << 3D value) of the faces the provider pushes to
 * @param machineFaces bit mask of the faces with a block entity next to the provider
 * @param enabled     the global config switch {@code pattern_provider.enable_output_pull}
 */
public record ProviderPullSidesPayload(byte[] modes, int pushFaces, int machineFaces, boolean enabled)
        implements CustomPacketPayload {
    public static final Type<ProviderPullSidesPayload> TYPE =
            new Type<>(AppliedEnhancements.id("provider_pull_sides"));
    public static final StreamCodec<ByteBuf, ProviderPullSidesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE_ARRAY, ProviderPullSidesPayload::modes,
            ByteBufCodecs.VAR_INT, ProviderPullSidesPayload::pushFaces,
            ByteBufCodecs.VAR_INT, ProviderPullSidesPayload::machineFaces,
            ByteBufCodecs.BOOL, ProviderPullSidesPayload::enabled,
            ProviderPullSidesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
