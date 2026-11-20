package com.appliedenhancements.packtests;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.stacks.AEItemKey;
import appeng.server.testplots.CraftingPatternHelper;
import appeng.server.testplots.TestPlot;
import appeng.server.testplots.TestPlotClass;
import appeng.server.testworld.PlotBuilder;
import appeng.server.testworld.TestCraftingJob;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;

/**
 * Reproduces the suspected ExtendedAE Assembler Matrix bug (2026-11-20): after ONE partial output hand-over (the
 * network accepted less than the crafter offered) the crafter sets {@code blockCoolDown = 100} and, per the bytecode,
 * never counts it down again, so it never delivers another output until the world is reloaded.
 *
 * <p>
 * Rig: AE2 CPU + smallest matrix; the only item cell is completely full (64 oak logs + cobblestone), so crafted planks
 * cannot be stored. Job 1 (6 planks = 2 crafts of 4) makes the network take less than offered. Job 2 (4 planks) must
 * still finish; with the bug it never does.
 */
@TestPlotClass
public final class MatrixFlushTestPlots {
    private MatrixFlushTestPlots() {
    }

    @TestPlot(value = PackTestsMod.RIG_PREFIX + "matrix_partial_flush", maxTicks = 20 * 60)
    public static void partialFlush(PlotBuilder plot) {
        Rigs.ae2CraftingCpu(plot);
        plot.cable("0 0 -2");
        plot.cable("0 0 -3");
        plot.cable("0 0 -4");
        plot.cable("1 0 -4");
        plot.cable("2 0 -4");
        var patternCore = Rigs.assemblerMatrix(plot, 3, 0, -7);
        var drive = plot.drive(new BlockPos(0, 0, -5));
        var cell = drive.addItemCell64k();
        cell.add(Items.OAK_LOG, 64);
        try { // fill the rest of the cell: the insert stores what fits, then reports the rest as an error
            cell.add(Items.COBBLESTONE, 1_000_000);
        } catch (IllegalArgumentException expectedWhenFull) {
            // the cell is now full
        }
        plot.creativeEnergyCell("-1 0 -4");

        plot.test(helper -> {
            var planks = AEItemKey.of(Items.OAK_PLANKS);
            var job1 = new TestCraftingJob(helper, BlockPos.ZERO, planks, 6);
            var job2 = new TestCraftingJob(helper, BlockPos.ZERO, planks, 4);
            helper.startSequence()
                    .thenIdle(20)
                    .thenExecute(() -> Rigs.insertPattern(helper, patternCore,
                            CraftingPatternHelper.encodeShapelessCraftingRecipe(helper.getLevel(),
                                    Items.OAK_LOG.getDefaultInstance())))
                    .thenIdle(5)
                    .thenWaitUntil(job1::tickUntilStarted)
                    .thenWaitUntil(() -> helper.check(idle(helper), "job 1 did not finish"))
                    .thenExecute(() -> Rigs.report("matrix_partial_flush", "job1=done"))
                    .thenWaitUntil(job2::tickUntilStarted)
                    .thenWaitUntil(() -> helper.check(idle(helper),
                            "job 2 never finished: matrix crafter output stuck after a partial hand-over"))
                    .thenExecute(() -> Rigs.report("matrix_partial_flush", "job2=done"))
                    .thenSucceed();
        });
    }

    private static boolean idle(appeng.server.testworld.PlotTestHelper helper) {
        return helper.getGrid(BlockPos.ZERO).getCraftingService().getCpus().stream()
                .noneMatch(ICraftingCPU::isBusy);
    }
}
