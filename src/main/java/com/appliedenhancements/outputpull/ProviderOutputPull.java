package com.appliedenhancements.outputpull;

import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Feature G: a pattern provider pulls the outputs of the crafts it pushed back out of the machine (for machines
 * that keep their outputs). One "pull mode" per provider face:
 * <ul>
 *   <li>{@link #OFF} (default): nothing is pulled from that face;</li>
 *   <li>{@link #SAME_FACE}: pull from the machine side that touches the provider;</li>
 *   <li>{@code 2..7}: pull from a specific side of the machine ({@link #sideMode(Direction)}), for machines that
 *       give outputs only on another side.</li>
 * </ul>
 * Only declared pattern outputs that are still owed are pulled, never a key that is an input of any pattern on
 * that provider (items or fluids). Global switch: config {@code pattern_provider.enable_output_pull}.
 */
public final class ProviderOutputPull {
    public static final byte OFF = 0;
    public static final byte SAME_FACE = 1;
    public static final int MODE_COUNT = 8;

    private ProviderOutputPull() {
    }

    public static byte sideMode(Direction machineSide) {
        return (byte) (2 + machineSide.get3DDataValue());
    }

    public static boolean isValid(int mode) {
        return mode >= 0 && mode < MODE_COUNT;
    }

    /** Machine side to pull from for a provider face, or null when the face is off. */
    @Nullable
    public static Direction machineSide(Direction providerFace, int mode) {
        if (mode == OFF || !isValid(mode)) {
            return null;
        }
        if (mode == SAME_FACE) {
            return providerFace.getOpposite();
        }
        return Direction.from3DDataValue(mode - 2);
    }

    /** Next mode when the player clicks (off → same face → down → up → north → south → west → east → off). */
    public static byte next(int mode) {
        return (byte) ((mode + 1) % MODE_COUNT);
    }

    public static byte previous(int mode) {
        return (byte) ((mode + MODE_COUNT - 1) % MODE_COUNT);
    }
}
