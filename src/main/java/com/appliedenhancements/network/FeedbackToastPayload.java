package com.appliedenhancements.network;

import com.appliedenhancements.AppliedEnhancements;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server → client: a feedback message the client shows as a toast.
 *
 * <p>These were chat messages on 1.21.1, sent while a terminal or the AELIS screen is open. Chat and the
 * action bar are part of the HUD, which Minecraft draws BEFORE the open screen, so the screen hid them.
 * Toasts are drawn after the screen.
 *
 * <p>Pure data on purpose: this class is loaded on dedicated servers too. The client handler lives in
 * {@link ClientPayloadHandlers}; it only accepts keys under {@link #ALLOWED_KEY_PREFIXES}.
 *
 * @param kind which feature (toast title): index into {@link Kind}
 * @param messageKey translation key of the message
 * @param count the message's {@code %s} argument (ignored by messages without one)
 */
public record FeedbackToastPayload(int kind, String messageKey, int count) implements CustomPacketPayload {
    /** Server-side mirror of the client's toast kinds (the client enum is not loadable on a server). */
    public enum Kind { PATTERN_QUICK_MOVE, CRAFTING_PLAN }

    public static final String[] ALLOWED_KEY_PREFIXES = {
            "message.appliedenhancements.",
            "gui.appliedenhancements.plan.",
    };

    public static final Type<FeedbackToastPayload> TYPE =
            new Type<>(AppliedEnhancements.id("feedback_toast"));
    public static final StreamCodec<ByteBuf, FeedbackToastPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FeedbackToastPayload::kind,
            ByteBufCodecs.STRING_UTF8, FeedbackToastPayload::messageKey,
            ByteBufCodecs.VAR_INT, FeedbackToastPayload::count,
            FeedbackToastPayload::new);

    public static void send(ServerPlayer player, Kind kind, String messageKey, int count) {
        PacketDistributor.sendToPlayer(player, new FeedbackToastPayload(kind.ordinal(), messageKey, count));
    }

    public static boolean isAllowedKey(String key) {
        for (String prefix : ALLOWED_KEY_PREFIXES) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
