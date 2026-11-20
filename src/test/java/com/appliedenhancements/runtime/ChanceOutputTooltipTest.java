package com.appliedenhancements.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

class ChanceOutputTooltipTest {
    @Test
    void unmarkedPatternGetsNoProperty() {
        assertNull(ChanceOutputTooltip.property(false, true));
        assertNull(ChanceOutputTooltip.property(false, false));
    }

    @Test
    void markedPatternGetsTheOnProperty() {
        Component property = ChanceOutputTooltip.property(true, true);
        assertNotNull(property);
        assertEquals(ChanceOutputTooltip.KEY_ON, ((TranslatableContents) property.getContents()).getKey());
    }

    @Test
    void markedPatternWithFeatureOffInConfigSaysSo() {
        Component property = ChanceOutputTooltip.property(true, false);
        assertNotNull(property);
        assertEquals(ChanceOutputTooltip.KEY_INACTIVE, ((TranslatableContents) property.getContents()).getKey());
    }
}
