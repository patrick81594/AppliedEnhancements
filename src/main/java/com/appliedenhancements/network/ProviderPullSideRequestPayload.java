package com.appliedenhancements.network;

import appeng.menu.implementations.PatternProviderMenu;
import com.appliedenhancements.AppliedEnhancements;
import com.appliedenhancements.Config;
import com.appliedenhancements.mixin.PatternProviderMenuAccessor;
import com.appliedenhancements.provider.ProviderOutputPull;
import com.appliedenhancements.provider.ProviderOutputPullHost;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → server: change the pull mode of one face of the pattern provider whose screen is open, or (face = -1)
 * only ask for the current state. The server always answers with {@link ProviderPullSidesPayload}. Only the
 * provider of the player's OPEN menu can be changed.
 */
public record ProviderPullSideRequestPayload(int face, int mode) implements CustomPacketPayload {
    public static final int QUERY_ONLY = -1;

    public static final Type<ProviderPullSideRequestPayload> TYPE =
            new Type<>(AppliedEnhancements.id("provider_pull_side_request"));
    public static final StreamCodec<ByteBuf, ProviderPullSideRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ProviderPullSideRequestPayload::face,
            ByteBufCodecs.VAR_INT, ProviderPullSideRequestPayload::mode,
            ProviderPullSideRequestPayload::new);

    public static void handle(ProviderPullSideRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof PatternProviderMenu menu)
                    || !(((PatternProviderMenuAccessor) menu).appliedenhancements$getLogic()
                            instanceof ProviderOutputPullHost host)) {
                return;
            }
            if (payload.face() >= 0 && payload.face() < 6 && ProviderOutputPull.isValid(payload.mode())) {
                host.appliedenhancements$setPullMode(Direction.from3DDataValue(payload.face()),
                        (byte) payload.mode());
            }
            PacketDistributor.sendToPlayer(player, stateOf(host, menu));
        });
    }

    static ProviderPullSidesPayload stateOf(ProviderOutputPullHost host, PatternProviderMenu menu) {
        var modes = new byte[6];
        int pushFaces = 0;
        int machineFaces = 0;
        var logic = ((PatternProviderMenuAccessor) menu).appliedenhancements$getLogic();
        var be = ((com.appliedenhancements.mixin.PatternProviderLogicAccessor) logic)
                .appliedenhancements$getHost().getBlockEntity();
        for (var face : Direction.values()) {
            int i = face.get3DDataValue();
            modes[i] = host.appliedenhancements$getPullMode(face);
            if (be.getLevel() != null && be.getLevel().getBlockEntity(be.getBlockPos().relative(face)) != null) {
                machineFaces |= 1 << i;
            }
        }
        for (var face : host.appliedenhancements$getPushFaces()) {
            pushFaces |= 1 << face.get3DDataValue();
        }
        return new ProviderPullSidesPayload(modes, pushFaces, machineFaces, Config.ENABLE_PROVIDER_OUTPUT_PULL.get());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
