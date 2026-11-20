package com.appliedenhancements.network;

import appeng.api.stacks.AEKey;
import appeng.menu.me.crafting.CraftingCPUMenu;
import com.appliedenhancements.AppliedEnhancements;
import com.appliedenhancements.mixin.CraftingCPUMenuAccessor;
import com.appliedenhancements.runtime.ChanceTopUpControl;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → server: the player middle-clicked an item in the crafting-status screen to try its chance top-up
 * again as if the retry limit had not been reached. Validated against the player's OPEN crafting-status menu;
 * only a running job's own marked chance output is reset.
 */
public record ChanceRetryPayload(AEKey key) implements CustomPacketPayload {
    public static final Type<ChanceRetryPayload> TYPE = new Type<>(AppliedEnhancements.id("chance_retry"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChanceRetryPayload> STREAM_CODEC =
            StreamCodec.composite(AEKey.STREAM_CODEC, ChanceRetryPayload::key, ChanceRetryPayload::new);

    public static void handle(ChanceRetryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof CraftingCPUMenu menu)
                    || !menu.stillValid(player)) {
                return;
            }
            var cpu = ((CraftingCPUMenuAccessor) menu).appliedenhancements$getCpu();
            if (cpu == null || !(cpu.craftingLogic instanceof ChanceTopUpControl control)) {
                return;
            }
            var result = control.appliedenhancements$requestChanceRetry(payload.key());
            String message = switch (result) {
                case STARTED -> "message.appliedenhancements.chance_retry.started";
                case NO_JOB -> "message.appliedenhancements.chance_retry.no_job";
                case ALREADY_RUNNING -> "message.appliedenhancements.chance_retry.already_running";
                case NOT_MARKED -> "message.appliedenhancements.chance_retry.not_marked";
            };
            FeedbackToastPayload.send(player, FeedbackToastPayload.Kind.CRAFTING_PLAN, message, 0);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
