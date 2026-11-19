package com.appliedenhancements.packtests;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.me.helpers.MachineSource;
import appeng.server.testplots.CraftingPatternHelper;
import appeng.server.testplots.TestPlot;
import appeng.server.testplots.TestPlotClass;
import appeng.server.testworld.PlotBuilder;
import appeng.server.testworld.PlotTestHelper;
import appeng.server.testworld.TestCraftingJob;
import com.appliedenhancements.Config;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;

/**
 * Phase 7 B-b in the real pack: sugar from honey bottles (crafting, returns the glass bottle) with honey bottles
 * filled by a Productive Bees bottler (processing; the bottler keeps its output, an import bus pulls it). Reference
 * result from Patrick's world (2026-11-19 01:01): "AELIS plans minecraft:sugar in chunks … (demand per craft
 * {minecraft:glass_bottle=1})". The returned-item switch is a server setting, so run these one at a time.
 */
@TestPlotClass
public final class SugarChainTestPlots {
    private static final int SUGAR = 48; // = 16 crafts
    private static final int MAX_TICKS = 20 * 120;
    private static final BlockPos BOTTLER = new BlockPos(-2, 0, -3);

    private SugarChainTestPlots() {
    }

    @TestPlot(value = PackTestsMod.RIG_PREFIX + "sugar_chunks_on_8_bottles", maxTicks = MAX_TICKS)
    public static void chunksOn(PlotBuilder plot) {
        build(plot, 8);
        plot.test(helper -> runToCompletion(helper, true, 8));
    }

    @TestPlot(value = PackTestsMod.RIG_PREFIX + "sugar_chunks_off_8_bottles", maxTicks = MAX_TICKS)
    public static void chunksOff(PlotBuilder plot) {
        build(plot, 8);
        plot.test(helper -> runToCompletion(helper, false, 8));
    }

    /** No glass bottles in stock: the plan must miss exactly 1 bottle (reused for all crafts), not 16. */
    @TestPlot(value = PackTestsMod.RIG_PREFIX + "sugar_chunks_on_0_bottles", maxTicks = 20 * 30)
    public static void chunksOnNoBottles(PlotBuilder plot) {
        build(plot, 0);
        plot.test(helper -> {
            var holder = new Future<?>[1];
            helper.startSequence()
                    .thenExecute(() -> {
                        Config.AELIS_CHUNK_RETURNED_ITEM_FEEDBACK.set(true);
                        holder[0] = beginPlan(helper);
                    })
                    .thenWaitUntil(() -> helper.check(holder[0].isDone(), "planning"))
                    .thenExecute(() -> {
                        var plan = getPlan(helper, holder[0]);
                        long missing = plan.missingItems().get(AEItemKey.of(Items.GLASS_BOTTLE));
                        var all = new StringBuilder();
                        for (var entry : plan.missingItems()) {
                            all.append(entry.getKey()).append('x').append(entry.getLongValue()).append(' ');
                        }
                        Rigs.report("sugar_chunks_on_0_bottles", "missingGlassBottles=" + missing
                                + " missing=[" + all.toString().trim() + "] simulation=" + plan.simulation());
                        helper.check(missing == 1, "expected 1 missing glass bottle, got " + missing);
                    })
                    .thenSucceed();
        });
    }

    private static void runToCompletion(PlotTestHelper helper, boolean chunks, int bottles) {
        var sugar = AEItemKey.of(Items.SUGAR);
        var job = new TestCraftingJob(helper, BlockPos.ZERO, sugar, SUGAR);
        String rig = "sugar_chunks_" + (chunks ? "on" : "off") + "_" + bottles + "_bottles";
        // Every 5 s: what sits where (to see which step of the chain stops).
        helper.onEachTick(() -> {
            if (helper.getTick() % 100 == 0) {
                Rigs.report(rig, "tick=" + helper.getTick()
                        + " sugar=" + Rigs.stored(helper, sugar)
                        + " netGlass=" + Rigs.stored(helper, AEItemKey.of(Items.GLASS_BOTTLE))
                        + " netHoney=" + Rigs.stored(helper, AEItemKey.of(Items.HONEY_BOTTLE))
                        + " bottler=" + Rigs.contents(helper, BOTTLER)
                        + " assembler=" + Rigs.contents(helper, new BlockPos(2, 0, -3)));
            }
        });
        helper.startSequence()
                .thenExecute(() -> Config.AELIS_CHUNK_RETURNED_ITEM_FEEDBACK.set(chunks))
                .thenWaitUntil(job::tickUntilStarted)
                .thenWaitUntil(() -> {
                    long have = Rigs.stored(helper, sugar);
                    helper.check(have >= SUGAR, "expected " + SUGAR + " sugar, have " + have);
                })
                .thenExecute(() -> Rigs.report(rig, "sugarDone tick=" + helper.getTick()
                        + " sugar=" + Rigs.stored(helper, sugar)))
                // The last returned bottle(s) arrive after the last sugar: the CPU hands back what it still holds
                // when the job ends. Wait for that instead of checking in the same tick.
                .thenWaitUntil(() -> {
                    long glass = Rigs.stored(helper, AEItemKey.of(Items.GLASS_BOTTLE));
                    helper.check(glass == bottles, "all " + bottles + " glass bottles should be back, have " + glass);
                })
                .thenExecute(() -> Rigs.report(rig, "done tick=" + helper.getTick()
                        + " sugar=" + Rigs.stored(helper, sugar)
                        + " glassBottlesAfter=" + Rigs.stored(helper, AEItemKey.of(Items.GLASS_BOTTLE))
                        + " honeyBottlesLeft=" + Rigs.stored(helper, AEItemKey.of(Items.HONEY_BOTTLE))))
                .thenSucceed();
    }

    /**
     * CPU cube at the origin; sugar crafting pattern on provider + molecular assembler (east); honey-bottle
     * processing pattern on a provider next to a Productive Bees bottler (west), with an import bus pulling the
     * filled bottles out of the bottler; creative honey + {@code bottles} glass bottles in storage.
     */
    private static void build(PlotBuilder plot, int bottles) {
        Rigs.ae2CraftingCpu(plot);
        plot.cable("0 0 -2");
        plot.cable("0 0 -3");
        plot.cable("0 0 -4");
        plot.cable("0 0 -5");
        // Sugar = 1 honey bottle (crafting; returns the glass bottle).
        plot.blockEntity("1 0 -3", AEBlocks.PATTERN_PROVIDER, provider -> provider.getLogic().getPatternInv()
                .addItems(CraftingPatternHelper.encodeShapelessCraftingRecipe(
                        (net.minecraft.server.level.ServerLevel) provider.getLevel(),
                        Items.HONEY_BOTTLE.getDefaultInstance())));
        plot.block("2 0 -3", AEBlocks.MOLECULAR_ASSEMBLER);
        // Honey bottle = glass bottle + 250 mB honey (bottler; keeps its output).
        Fluid honey = honey();
        plot.blockEntity("-1 0 -3", AEBlocks.PATTERN_PROVIDER, provider -> provider.getLogic().getPatternInv()
                .addItems(PatternDetailsHelper.encodeProcessingPattern(
                        List.of(new GenericStack(AEItemKey.of(Items.GLASS_BOTTLE), 1),
                                new GenericStack(AEFluidKey.of(honey), 250)),
                        List.of(new GenericStack(AEItemKey.of(Items.HONEY_BOTTLE), 1)))));
        plot.block(new BlockPos(-2, 0, -3), Rigs.modBlock("productivebees:bottler"));
        plot.cable("-1 0 -4");
        // Filtered to honey bottles: an unfiltered AE2 26 import bus also imports FLUIDS (and any extractable
        // item), so it could drain the bottler's honey tank or pull its inputs back before they are processed.
        plot.cable("-2 0 -4").part(Direction.SOUTH, AEParts.IMPORT_BUS, bus -> {
            bus.getUpgrades().addItems(AEItems.SPEED_CARD.stack(4));
            bus.getConfig().insert(0, AEItemKey.of(Items.HONEY_BOTTLE), 1, appeng.api.config.Actionable.MODULATE);
        });
        var drive = plot.drive(new BlockPos(0, 0, -6));
        drive.addCreativeCell().add(honey);
        var cell = drive.addItemCell64k();
        if (bottles > 0) {
            cell.add(Items.GLASS_BOTTLE, bottles);
        }
        plot.creativeEnergyCell("1 0 -5");
    }

    private static Fluid honey() {
        return BuiltInRegistries.FLUID.getOptional(Identifier.parse("productivebees:honey"))
                .orElseThrow(() -> new IllegalStateException("productivebees:honey not in this pack"));
    }

    private static Future<ICraftingPlan> beginPlan(PlotTestHelper helper) {
        var grid = helper.getGrid(BlockPos.ZERO);
        var src = new MachineSource(grid::getPivot);
        ICraftingSimulationRequester requester = () -> src;
        return grid.getCraftingService().beginCraftingCalculation(grid.getPivot().getLevel(), requester,
                AEItemKey.of(Items.SUGAR), SUGAR, CalculationStrategy.REPORT_MISSING_ITEMS);
    }

    private static ICraftingPlan getPlan(PlotTestHelper helper, Future<?> future) {
        try {
            return (ICraftingPlan) future.get(0, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw helper.assertionException("planning failed: " + e, BlockPos.ZERO);
        }
    }
}
