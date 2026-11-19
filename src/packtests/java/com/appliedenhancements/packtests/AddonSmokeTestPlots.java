package com.appliedenhancements.packtests;

import appeng.api.stacks.AEItemKey;
import appeng.core.definitions.AEBlocks;
import appeng.server.testplots.CraftingPatternHelper;
import appeng.server.testplots.TestPlot;
import appeng.server.testplots.TestPlotClass;
import appeng.server.testworld.PlotBuilder;
import appeng.server.testworld.TestCraftingJob;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;

/**
 * Smoke tests with optional-addon hardware: the same 64-planks craft on an AdvancedAE Quantum Computer, and on an
 * ExtendedAE Assembler Matrix (the only crafter in that rig).
 */
@TestPlotClass
public final class AddonSmokeTestPlots {
    private AddonSmokeTestPlots() {
    }

    /** CPU = a single AdvancedAE quantum core (no AE2 CPU in the rig); crafter = provider + molecular assembler. */
    @TestPlot(value = PackTestsMod.RIG_PREFIX + "smoke_planks_quantum", maxTicks = 20 * 60)
    public static void quantum(PlotBuilder plot) {
        plot.cable("0 0 0");
        // A formed quantum core only connects up and down.
        plot.block(new BlockPos(0, 1, 0), Rigs.modBlock("advanced_ae:quantum_core"));
        plot.cable("0 0 -1");
        plot.cable("0 0 -2");
        plot.cable("0 0 -3");
        plot.block("1 0 -2", AEBlocks.PATTERN_PROVIDER);
        plot.block("2 0 -2", AEBlocks.MOLECULAR_ASSEMBLER);
        var drive = plot.drive(new BlockPos(-1, 0, -2));
        drive.addCreativeCell().add(Items.OAK_LOG);
        drive.addItemCell64k();
        plot.creativeEnergyCell("-1 0 -3");

        plot.test(helper -> {
            var planks = AEItemKey.of(Items.OAK_PLANKS);
            var job = new TestCraftingJob(helper, BlockPos.ZERO, planks, 64);
            helper.startSequence()
                    .thenExecute(() -> Rigs.insertPattern(helper, new BlockPos(1, 0, -2),
                            CraftingPatternHelper.encodeShapelessCraftingRecipe(helper.getLevel(),
                                    Items.OAK_LOG.getDefaultInstance())))
                    .thenIdle(5)
                    .thenWaitUntil(job::tickUntilStarted)
                    .thenWaitUntil(() -> {
                        long stored = Rigs.stored(helper, planks);
                        helper.check(stored >= 64, "expected 64 oak planks, have " + stored);
                    })
                    .thenExecute(() -> Rigs.report("smoke_planks_quantum", "planks=" + Rigs.stored(helper, planks)))
                    .thenSucceed();
        });
    }

    /** CPU = AE2 crafting CPU; crafter = the smallest ExtendedAE Assembler Matrix (no provider/assembler). */
    @TestPlot(value = PackTestsMod.RIG_PREFIX + "smoke_planks_matrix", maxTicks = 20 * 60)
    public static void matrix(PlotBuilder plot) {
        Rigs.ae2CraftingCpu(plot); // x/z -1..1, y 0..2; origin is part of it
        plot.cable("0 0 -2");
        plot.cable("0 0 -3");
        plot.cable("0 0 -4");
        plot.cable("1 0 -4");
        plot.cable("2 0 -4");
        // Matrix x 3..5, y 0..2, z -7..-4: the cable at (2,0,-4) touches its frame edge (3,0,-4).
        var patternCore = Rigs.assemblerMatrix(plot, 3, 0, -7);
        var drive = plot.drive(new BlockPos(0, 0, -5));
        drive.addCreativeCell().add(Items.OAK_LOG);
        drive.addItemCell64k();
        plot.creativeEnergyCell("-1 0 -4");

        plot.test(helper -> {
            var planks = AEItemKey.of(Items.OAK_PLANKS);
            var job = new TestCraftingJob(helper, BlockPos.ZERO, planks, 64);
            helper.startSequence()
                    .thenIdle(20) // let the matrix form and join the grid
                    .thenExecute(() -> Rigs.insertPattern(helper, patternCore,
                            CraftingPatternHelper.encodeShapelessCraftingRecipe(helper.getLevel(),
                                    Items.OAK_LOG.getDefaultInstance())))
                    .thenIdle(5)
                    .thenWaitUntil(job::tickUntilStarted)
                    .thenWaitUntil(() -> {
                        long stored = Rigs.stored(helper, planks);
                        helper.check(stored >= 64, "expected 64 oak planks, have " + stored);
                    })
                    .thenExecute(() -> Rigs.report("smoke_planks_matrix", "planks=" + Rigs.stored(helper, planks)))
                    .thenSucceed();
        });
    }
}
