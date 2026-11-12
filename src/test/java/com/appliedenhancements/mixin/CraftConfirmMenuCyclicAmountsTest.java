package com.appliedenhancements.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import appeng.api.stacks.AEKey;
import com.appliedenhancements.test.TestAEKey;
import java.math.BigInteger;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The CraftConfirmMenuMixin part of the former
 * AelisExactCyclicPlanningTest#exactCyclicAmountsReachClientRowsAndTooltipsWithoutClampingOrMixingOrdinaryWork.
 * It instantiates the Mixin class directly, which only works in plain JUnit (no running Mixin system),
 * so it lives in plainTest. Same inputs as the payload round trip there (cyclic = Long.MAX_VALUE as long,
 * exact cyclic and crafted amounts as BigInteger); TestAEKey replaces Items.EMERALD because plain JUnit
 * has no item registry.
 */
class CraftConfirmMenuCyclicAmountsTest {
    @Test
    void exactCyclicAmountsSurviveTheMenuWithoutClampingAndKeepASnapshot() {
        AEKey key = new TestAEKey("emerald");
        var cyclic = new BigInteger("44444444444444444444448");
        var crafted = cyclic.multiply(BigInteger.TWO).add(BigInteger.valueOf(17));

        var menu = new CraftConfirmMenuMixin() {};
        menu.appliedenhancements$setCyclicCraftAmounts(Map.of(key, Long.MAX_VALUE));
        menu.appliedenhancements$setBigIntegerCyclicCraftAmounts(Map.of(key, cyclic));
        menu.appliedenhancements$setBigIntegerCraftAmounts(Map.of(key, crafted));
        assertEquals(cyclic, menu.appliedenhancements$getBigIntegerCyclicCraftAmounts().get(key));
        assertEquals(crafted, menu.appliedenhancements$getBigIntegerCraftAmounts().get(key));

        var snapshot = menu.appliedenhancements$getBigIntegerCyclicCraftAmounts();
        assertSame(snapshot, menu.appliedenhancements$getBigIntegerCyclicCraftAmounts());
        menu.appliedenhancements$setCyclicCraftAmounts(Map.of(key, 7L));
        menu.appliedenhancements$setBigIntegerCyclicCraftAmounts(Map.of());
        assertEquals(BigInteger.valueOf(7), menu.appliedenhancements$getBigIntegerCyclicCraftAmounts().get(key));
        assertEquals(cyclic, snapshot.get(key));
    }
}
