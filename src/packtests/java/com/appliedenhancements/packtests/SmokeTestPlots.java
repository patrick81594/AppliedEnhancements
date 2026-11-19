package com.appliedenhancements.packtests;

import appeng.api.stacks.AEItemKey;
import appeng.core.definitions.AEBlocks;
import appeng.server.testplots.CraftingPatternHelper;
import appeng.server.testplots.TestPlot;
import appeng.server.testplots.TestPlotClass;
import appeng.server.testworld.PlotBuilder;
import appeng.server.testworld.TestCraftingJob;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;

/**
 * Smoke test for the pack-test infrastructure: if this passes on the test server, rigs are found, built,
 * powered and a crafting job runs through an AE2 CPU, a pattern provider and a molecular assembler.
 */
@TestPlotClass
public final class SmokeTestPlots {
    private SmokeTestPlots() {
    }

    /** 64 oak planks from oak logs (creative cell) with a crafting pattern on provider + assembler. */
    @TestPlot(value = PackTestsMod.RIG_PREFIX + "smoke_planks", maxTicks = 20 * 60)
    public static void smokePlanks(PlotBuilder plot) {
        Rigs.ae2CraftingCpu(plot); // grid origin (0,0,0) is part of the CPU
        plot.cable("0 0 -2");
        plot.blockEntity("0 0 -3", AEBlocks.PATTERN_PROVIDER, provider -> {
            var pattern = CraftingPatternHelper.encodeShapelessCraftingRecipe(
                    (ServerLevel) provider.getLevel(), Items.OAK_LOG.getDefaultInstance());
            provider.getLogic().getPatternInv().addItems(pattern);
        });
        plot.block("1 0 -3", AEBlocks.MOLECULAR_ASSEMBLER);
        plot.cable("0 0 -4");
        var drive = plot.drive(new BlockPos(0, 0, -5));
        drive.addCreativeCell().add(Items.OAK_LOG);
        drive.addItemCell64k();
        plot.creativeEnergyCell("0 -1 -5");

        plot.test(helper -> {
            var planks = AEItemKey.of(Items.OAK_PLANKS);
            var job = new TestCraftingJob(helper, BlockPos.ZERO, planks, 64);
            helper.startSequence()
                    .thenWaitUntil(job::tickUntilStarted)
                    .thenWaitUntil(() -> {
                        long stored = Rigs.stored(helper, planks);
                        helper.check(stored >= 64, "expected 64 oak planks in storage, have " + stored);
                    })
                    .thenExecute(() -> Rigs.report("smoke_planks", "planks=" + Rigs.stored(helper, planks)))
                    .thenSucceed();
        });
    }
}
