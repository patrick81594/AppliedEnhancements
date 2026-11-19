package com.appliedenhancements.network;

import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionHost;
import appeng.menu.AEBaseMenu;
import com.appliedenhancements.AppliedEnhancements;
import com.appliedenhancements.Config;
import com.appliedenhancements.provider.ProviderOutputPull;
import com.appliedenhancements.provider.ProviderOutputPullHost;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/**
 * Client → server: change the pull mode of one face of the pattern provider whose screen is open, or (face = -1)
 * only ask for the current state. The server always answers with {@link ProviderPullSidesPayload}. Only the
 * provider of the player's OPEN menu can be changed.
 *
 * <p>Works for every provider with feature G: the open menu's target must be a grid host whose crafting-provider
 * service is a {@link ProviderOutputPullHost} (AE2, ExtendedAE and AdvancedAE providers alike).
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
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ProviderOutputPullHost host = openProvider(player);
            if (host == null) {
                return;
            }
            if (payload.face() >= 0 && payload.face() < 6 && ProviderOutputPull.isValid(payload.mode())) {
                host.appliedenhancements$setPullMode(Direction.from3DDataValue(payload.face()),
                        (byte) payload.mode());
            }
            PacketDistributor.sendToPlayer(player, stateOf(host));
        });
    }

    /** The pull host of the provider whose menu the player has open, or null if the open menu is not one. */
    @Nullable
    static ProviderOutputPullHost openProvider(ServerPlayer player) {
        if (!(player.containerMenu instanceof AEBaseMenu menu)
                || !(menu.getTarget() instanceof IActionHost actionHost)) {
            return null;
        }
        IGridNode node = actionHost.getActionableNode();
        if (node == null) {
            return null;
        }
        return node.getService(ICraftingProvider.class) instanceof ProviderOutputPullHost host ? host : null;
    }

    static ProviderPullSidesPayload stateOf(ProviderOutputPullHost host) {
        var modes = new byte[6];
        int pushFaces = 0;
        int machineFaces = 0;
        BlockEntity be = host.appliedenhancements$getPullBlockEntity();
        var level = be == null ? null : be.getLevel();
        for (var face : Direction.values()) {
            int i = face.get3DDataValue();
            modes[i] = host.appliedenhancements$getPullMode(face);
            if (level != null && level.getBlockEntity(be.getBlockPos().relative(face)) != null) {
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
