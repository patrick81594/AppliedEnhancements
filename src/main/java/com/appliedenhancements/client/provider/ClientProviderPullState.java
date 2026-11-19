package com.appliedenhancements.client.provider;

import com.appliedenhancements.network.ProviderPullSideRequestPayload;
import com.appliedenhancements.network.ProviderPullSidesPayload;
import com.appliedenhancements.provider.ProviderOutputPull;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Client copy of the feature G state of the pattern provider whose screen is open (the server answers every
 * request with {@link ProviderPullSidesPayload}). Client only.
 */
public final class ClientProviderPullState {
    @Nullable
    private static ProviderPullSidesPayload current;
    private static int version;
    /** Whether the "Pull Outputs" side panel is open; kept across screens so it reopens the way it was left. */
    private static boolean panelOpen;

    private ClientProviderPullState() {
    }

    public static boolean isPanelOpen() {
        return panelOpen;
    }

    public static void setPanelOpen(boolean open) {
        panelOpen = open;
    }

    public static void update(ProviderPullSidesPayload payload) {
        current = payload;
        version++;
    }

    /** Ask the server for the state of the open provider (call when the screen/panel opens). */
    public static void request() {
        current = null;
        ClientPacketDistributor.sendToServer(
                new ProviderPullSideRequestPayload(ProviderPullSideRequestPayload.QUERY_ONLY, 0));
    }

    /** Change one face; the server answers with the new state. */
    public static void setMode(Direction face, byte mode) {
        ClientPacketDistributor.sendToServer(new ProviderPullSideRequestPayload(face.get3DDataValue(), mode));
    }

    @Nullable
    public static ProviderPullSidesPayload current() {
        return current;
    }

    /** Increases on every update, so widgets can notice changes cheaply. */
    public static int version() {
        return version;
    }

    public static byte mode(Direction face) {
        var state = current;
        return state == null ? ProviderOutputPull.OFF : state.modes()[face.get3DDataValue()];
    }

    public static boolean pushesTo(Direction face) {
        var state = current;
        return state != null && (state.pushFaces() & (1 << face.get3DDataValue())) != 0;
    }

    public static boolean hasMachine(Direction face) {
        var state = current;
        return state != null && (state.machineFaces() & (1 << face.get3DDataValue())) != 0;
    }

    public static boolean enabled() {
        var state = current;
        return state != null && state.enabled();
    }

    public static void clear() {
        current = null;
    }
}
