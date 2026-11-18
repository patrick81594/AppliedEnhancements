package com.github.appliedenhancements.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.appliedenhancements.integration.ae2.AelisCalculationPath;
import com.github.appliedenhancements.integration.ae2.CraftingCalculationProgressHandle;
import com.github.appliedenhancements.integration.ae2.CraftingCalculationProgressPhase;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

/** Every progress update the server can send must decode on the client: a failed decode disconnects. */
class CraftingCalculationProgressPayloadTest {
    private static CraftingCalculationProgressPayload roundTrip(CraftingCalculationProgressPayload payload) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        CraftingCalculationProgressPayload.STREAM_CODEC.encode(buffer, payload);
        return CraftingCalculationProgressPayload.STREAM_CODEC.decode(buffer);
    }

    @Test
    void handBackToTheHostPlannerDecodesAsIdle() {
        // The exact state the server sends when AELIS hands a request back to AE2 (seen in game: disconnect).
        var handle = new CraftingCalculationProgressHandle(3);
        handle.beginAttempt(false);
        handle.beginAelisCompilation();
        handle.stopTracking();
        var sent = new CraftingCalculationProgressPayload(7, handle.snapshot(1));
        assertEquals(CraftingCalculationProgressPhase.IDLE, sent.progress().phase());
        assertEquals(AelisCalculationPath.AELIS, sent.progress().path());

        var received = roundTrip(sent);

        assertEquals(sent, received);
    }

    @Test
    void runningAelisProgressRoundTrips() {
        var handle = new CraftingCalculationProgressHandle(4);
        handle.beginAttempt(true);
        handle.beginAelisExecution(10);
        handle.executionStep();
        var sent = new CraftingCalculationProgressPayload(2, handle.snapshot(5));

        assertEquals(sent, roundTrip(sent));
    }

    @Test
    void invalidMessagesAreStillRejected() {
        // Written byte by byte in the payload's wire format, because the snapshot constructor itself refuses
        // invalid values: an unknown phase id, and completed > total.
        assertThrows(DecoderException.class, () -> decodeRaw(4, 0, -1));
        assertThrows(DecoderException.class, () -> decodeRaw(
                CraftingCalculationProgressPhase.AELIS_EXECUTING.networkId(), 11, 10));
    }

    private static CraftingCalculationProgressPayload decodeRaw(int phaseId, long completed, long total) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        buffer.writeVarInt(1);                                        // containerId
        buffer.writeLong(1);                                          // generation
        buffer.writeLong(1);                                          // revision
        buffer.writeByte(phaseId);
        buffer.writeByte(AelisCalculationPath.AELIS.networkId());
        buffer.writeLong(0);                                          // processedSteps
        buffer.writeLong(0);                                          // discoveredNodes
        buffer.writeLong(completed);
        buffer.writeLong(total);
        buffer.writeLong(0);                                          // elapsedMillis
        buffer.writeVarInt(1);                                        // attempt
        buffer.writeBoolean(false);                                   // simulation
        return CraftingCalculationProgressPayload.STREAM_CODEC.decode(buffer);
    }
}
