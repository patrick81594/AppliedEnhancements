package com.appliedenhancements.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.stacks.AEItemKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

// Needs the registered data component: runs in the FML  task.
@Tag("fml")
@com.appliedenhancements.test.BootstrapMinecraft
class ChanceOutputMarkerTest {
    @Test
    void unmarkedByDefault() {
        assertFalse(ChanceOutputMarker.isMarked(new ItemStack(Items.PAPER)));
    }

    @Test
    void markerRoundTripsAndClears() {
        var stack = ChanceOutputMarker.withMarker(new ItemStack(Items.PAPER), true);
        assertTrue(ChanceOutputMarker.isMarked(stack));
        ChanceOutputMarker.withMarker(stack, false);
        assertFalse(ChanceOutputMarker.isMarked(stack));
    }

    @Test
    void markedAndUnmarkedAreDifferentItemKeys() {
        var plain = AEItemKey.of(new ItemStack(Items.PAPER));
        var marked = AEItemKey.of(ChanceOutputMarker.withMarker(new ItemStack(Items.PAPER), true));
        assertNotEquals(plain, marked);
        assertEquals(plain, AEItemKey.of(ChanceOutputMarker.withMarker(marked.toStack(), false)));
    }
}
