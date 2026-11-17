package com.appliedenhancements.network;

import com.appliedenhancements.AppliedEnhancements;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: the result of a pattern batch move, shown as a toast.
 *
 * <p>Was a chat message on 1.21.1. Chat and the action bar are part of the HUD, which Minecraft draws
 * BEFORE the open screen, so the pattern terminal hid them. Toasts are drawn after the screen.
 *
 * <p>Pure data on purpose: this class is loaded on dedicated servers too. The client handler lives in
 * {@link ClientPayloadHandlers}.
 *
 * @param messageKey translation key, always under {@link #KEY_PREFIX}
 * @param count number of moved patterns (ignored by failure messages)
 */
public record PatternQuickMoveResultPayload(String messageKey, int count) implements CustomPacketPayload {
    public static final String KEY_PREFIX = "message.appliedenhancements.pattern_batch_move.";

    public static final Type<PatternQuickMoveResultPayload> TYPE =
            new Type<>(AppliedEnhancements.id("pattern_quick_move_result"));
    public static final StreamCodec<ByteBuf, PatternQuickMoveResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PatternQuickMoveResultPayload::messageKey,
            ByteBufCodecs.VAR_INT, PatternQuickMoveResultPayload::count,
            PatternQuickMoveResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
