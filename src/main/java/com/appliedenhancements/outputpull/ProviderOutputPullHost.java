package com.appliedenhancements.outputpull;

import appeng.api.stacks.KeyCounter;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Implemented (via Mixin) by pattern provider logics that support feature G. Server side only; the client
 * receives the state through {@code ProviderPullSidesPayload}.
 */
public interface ProviderOutputPullHost {
    /** Pull mode of a provider face, see {@link ProviderOutputPull}. */
    byte appliedenhancements$getPullMode(Direction face);

    void appliedenhancements$setPullMode(Direction face, byte mode);

    /** Copy of the outputs still owed by the machine on that face (for tests and diagnostics). */
    KeyCounter appliedenhancements$getOwedOutputs(Direction face);

    /** Faces this provider pushes to (all six, or one for a directional provider / cable part). */
    Set<Direction> appliedenhancements$getPushFaces();

    /** The block entity (or the part's block entity) the provider lives in; null if not set up. */
    @Nullable
    BlockEntity appliedenhancements$getPullBlockEntity();
}
