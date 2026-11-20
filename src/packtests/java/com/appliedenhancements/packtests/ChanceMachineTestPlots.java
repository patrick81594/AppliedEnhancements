package com.appliedenhancements.packtests;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.server.testplots.TestPlot;
import appeng.server.testplots.TestPlotClass;
import appeng.server.testworld.PlotBuilder;
import appeng.server.testworld.PlotTestHelper;
import appeng.server.testworld.TestCraftingJob;
import com.appliedenhancements.Config;
import com.appliedenhancements.outputpull.ProviderOutputPull;
import com.appliedenhancements.outputpull.ProviderOutputPullHost;
import com.appliedenhancements.chance.ChanceOutputMarker;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Phase 7 D3 with a REAL chance machine: the LTX Industries hydrosieve (single block, 125 mB water + gravel ->
 * 1 flint + 50% second flint; peridotite dust + 500 mB hydrochloric acid -> 25% raw olivine, nothing guaranteed).
 * The machine keeps its outputs (no auto-eject), so every side is opened for item output and the outputs are
 * taken either by the provider's pull mode (feature G) or by an import bus. The patterns are marked as chance
 * patterns; the chance settings are server-wide, so run these rigs one at a time.
 */
@TestPlotClass
public final class ChanceMachineTestPlots {
    private static final BlockPos PROVIDER = new BlockPos(1, 0, -3);
    private static final BlockPos SIEVE = new BlockPos(2, 0, -3);
    /** The sieve is EAST of the provider. */
    private static final Direction SIEVE_FACE = Direction.EAST;
    /** Stage-2 provider WEST of the cable column and its chest (the two-stage rig). */
    private static final BlockPos PROVIDER2 = new BlockPos(-1, 0, -3);
    private static final BlockPos CHEST2 = new BlockPos(-2, 0, -3);

    /** The hydrosieve without upgrades is slow; the rigs shorten its craft time (see {@link #accelerate}). */
    private static final int FAST_CRAFT_TICKS = 60;

    private ChanceMachineTestPlots() {
    }

    /** 8 flint; the provider's pull mode takes the outputs (owed outputs only, so no bonus flint is pulled). */
    @TestPlot(value = PackTestsMod.RIG_PREFIX + "chance_flint_pull", maxTicks = 20 * 180)
    public static void flintPull(PlotBuilder plot) {
        build(plot, flintPattern(), AEItemKey.of(Items.GRAVEL), Items.GRAVEL, Fluids.WATER, false);
        plot.test(helper -> run(helper, "chance_flint_pull", AEItemKey.of(Items.FLINT), 8, true, 8));
    }

    /** 8 flint; an import bus takes everything the sieve makes, so the 50% bonus flint counts toward the job. */
    @TestPlot(value = PackTestsMod.RIG_PREFIX + "chance_flint_bus", maxTicks = 20 * 180)
    public static void flintBus(PlotBuilder plot) {
        build(plot, flintPattern(), AEItemKey.of(Items.GRAVEL), Items.GRAVEL, Fluids.WATER, true);
        plot.test(helper -> run(helper, "chance_flint_bus", AEItemKey.of(Items.FLINT), 8, false, 8));
    }

    /** 2 raw olivine at 25% with nothing guaranteed: only the top-up waves can finish the job. */
    @TestPlot(value = PackTestsMod.RIG_PREFIX + "chance_olivine_hcl_pull", maxTicks = 20 * 300)
    public static void olivinePull(PlotBuilder plot) {
        var olivine = item("ltxi:raw_olivine");
        var dust = item("ltxi:peridotite_dust");
        var hcl = fluid("ltxi:hydrochloric_acid");
        build(plot, chancePattern(dust, hcl, 500, olivine), AEItemKey.of(dust), dust, hcl, false);
        plot.test(helper -> run(helper, "chance_olivine_hcl_pull", AEItemKey.of(olivine), 2, true, 8));
    }

    /**
     * TWO-STAGE chain where the INTERMEDIATE is the chance output: peridotite dust + HCl -> 25% raw olivine
     * (marked, real hydrosieve) -> stage 2 "1 raw olivine -> 1 stone" in a chest that a rig tick turns into stone
     * (stand-in machine; the provider's pull mode brings the stone back). Requesting 4 stone stalls the job on the
     * intermediate: the final output's pattern is never "all pushed" while olivine is missing.
     */
    @TestPlot(value = PackTestsMod.RIG_PREFIX + "chance_intermediate_olivine_stone", maxTicks = 20 * 420)
    public static void intermediateOlivine(PlotBuilder plot) {
        var olivine = item("ltxi:raw_olivine");
        var dust = item("ltxi:peridotite_dust");
        var hcl = fluid("ltxi:hydrochloric_acid");
        build(plot, chancePattern(dust, hcl, 500, olivine), AEItemKey.of(dust), dust, hcl, false);
        var stage2 = PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(olivine), 1)),
                List.of(new GenericStack(AEItemKey.of(Items.STONE), 1)));
        plot.blockEntity("-1 0 -3", AEBlocks.PATTERN_PROVIDER,
                provider -> provider.getLogic().getPatternInv().addItems(stage2.copy()));
        plot.block(CHEST2, net.minecraft.world.level.block.Blocks.CHEST);
        plot.test(helper -> runTwoStage(helper, "chance_intermediate_olivine_stone", AEItemKey.of(olivine),
                AEItemKey.of(Items.STONE), 4, 8));
    }

    private static void runTwoStage(PlotTestHelper helper, String rig, AEItemKey intermediate, AEItemKey out,
            long amount, int idleSeconds) {
        var job = new TestCraftingJob(helper, BlockPos.ZERO, out, amount);
        helper.onEachTick(() -> {
            accelerate(helper);
            standInMachine(helper, intermediate);
            if (helper.getTick() % 100 == 0) {
                Rigs.report(rig, "tick=" + helper.getTick() + " stone=" + Rigs.stored(helper, out)
                        + " olivineNet=" + Rigs.stored(helper, intermediate)
                        + " sieve=" + Rigs.contents(helper, SIEVE) + " chest=" + Rigs.contents(helper, CHEST2));
            }
        });
        helper.startSequence()
                .thenExecute(() -> {
                    Config.ENABLE_CHANCE_OUTPUT_BONUS.set(true);
                    Config.ENABLE_CHANCE_TOP_UP.set(true);
                    Config.CHANCE_TOP_UP_IDLE_SECONDS.set(idleSeconds);
                    Config.ENABLE_PROVIDER_OUTPUT_PULL.set(true);
                    openItemSides(helper.getLevel().getBlockEntity(helper.absolutePos(SIEVE)));
                    host(helper, PROVIDER).appliedenhancements$setPullMode(SIEVE_FACE, ProviderOutputPull.SAME_FACE);
                    host(helper, PROVIDER2).appliedenhancements$setPullMode(Direction.WEST, ProviderOutputPull.SAME_FACE);
                })
                .thenWaitUntil(job::tickUntilStarted)
                .thenWaitUntil(() -> {
                    long have = Rigs.stored(helper, out);
                    helper.check(have >= amount, "expected " + amount + " " + out + ", have " + have);
                })
                .thenExecute(() -> Rigs.report(rig, "done tick=" + helper.getTick() + " stone="
                        + Rigs.stored(helper, out)))
                .thenSucceed();
    }

    /** Stand-in for the stage-2 machine: turns one pushed raw olivine in the chest into one stone every 10 ticks. */
    private static void standInMachine(PlotTestHelper helper, AEItemKey intermediate) {
        if (helper.getTick() % 10 != 0
                || !(helper.getLevel().getBlockEntity(helper.absolutePos(CHEST2)) instanceof net.minecraft.world.Container chest)) {
            return;
        }
        for (int i = 0; i < chest.getContainerSize(); i++) {
            var stack = chest.getItem(i);
            if (intermediate.matches(stack)) {
                stack.shrink(1);
                if (stack.isEmpty()) {
                    chest.setItem(i, ItemStack.EMPTY);
                }
                for (int j = 0; j < chest.getContainerSize(); j++) {
                    var target = chest.getItem(j);
                    if (target.isEmpty()) {
                        chest.setItem(j, new ItemStack(Items.STONE));
                        return;
                    }
                    if (target.is(Items.STONE) && target.getCount() < target.getMaxStackSize()) {
                        target.grow(1);
                        return;
                    }
                }
                return;
            }
        }
    }

    private static void run(PlotTestHelper helper, String rig, AEItemKey out, long amount, boolean pull,
            int idleSeconds) {
        var job = new TestCraftingJob(helper, BlockPos.ZERO, out, amount);
        helper.onEachTick(() -> {
            accelerate(helper);
            if (helper.getTick() % 100 == 0) {
                Rigs.report(rig, "tick=" + helper.getTick() + " net=" + Rigs.stored(helper, out)
                        + " sieve=" + Rigs.contents(helper, SIEVE));
            }
        });
        helper.startSequence()
                .thenExecute(() -> {
                    Config.ENABLE_CHANCE_OUTPUT_BONUS.set(true);
                    Config.ENABLE_CHANCE_TOP_UP.set(true);
                    Config.CHANCE_TOP_UP_IDLE_SECONDS.set(idleSeconds);
                    Config.ENABLE_PROVIDER_OUTPUT_PULL.set(pull);
                    openItemSides(helper.getLevel().getBlockEntity(helper.absolutePos(SIEVE)));
                    if (pull) {
                        host(helper, PROVIDER).appliedenhancements$setPullMode(SIEVE_FACE, ProviderOutputPull.SAME_FACE);
                    }
                })
                .thenWaitUntil(job::tickUntilStarted)
                .thenWaitUntil(() -> {
                    long have = Rigs.stored(helper, out);
                    helper.check(have >= amount, "expected " + amount + " " + out + ", have " + have);
                })
                .thenExecute(() -> Rigs.report(rig, "done tick=" + helper.getTick() + " net="
                        + Rigs.stored(helper, out) + " sieve=" + Rigs.contents(helper, SIEVE)))
                .thenSucceed();
    }

    /**
     * Keeps the sieve's time per craft at {@code FAST_CRAFT_TICKS} (its recipe default is about 30 s; the machine
     * exposes setTicksPerOperation, the same hook its speed upgrades use). Reflection: LTX is not a dependency.
     */
    private static void accelerate(PlotTestHelper helper) {
        var be = helper.getLevel().getBlockEntity(helper.absolutePos(SIEVE));
        if (be == null) {
            return;
        }
        try {
            var set = be.getClass().getMethod("setTicksPerOperation", int.class);
            if (helper.getTick() == 1) {
                int before = (Integer) be.getClass().getMethod("getTicksPerOperation").invoke(be);
                Rigs.report("chance_machine", "ticksPerOperation before=" + before + " set to " + FAST_CRAFT_TICKS);
            }
            set.invoke(be, FAST_CRAFT_TICKS);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot speed up the hydrosieve: " + e, e);
        }
    }

    /**
     * CPU cube at the origin, provider (marked pattern) east of the cable column, the hydrosieve east of the
     * provider, a debug energy generator east of the sieve. Storage: 64 of the input item and a creative cell with
     * the fluid. {@code bus}: an import bus on the sieve's north side, filtered to the pattern's output.
     */
    private static void build(PlotBuilder plot, ItemStack pattern, AEItemKey inputItem, Item stockItem,
            Fluid fluid, boolean bus) {
        Rigs.ae2CraftingCpu(plot);
        plot.cable("0 0 -2");
        plot.cable("0 0 -3");
        plot.cable("0 0 -4");
        plot.cable("0 0 -5");
        plot.blockEntity("1 0 -3", AEBlocks.PATTERN_PROVIDER,
                provider -> provider.getLogic().getPatternInv().addItems(pattern.copy()));
        plot.block(SIEVE, Rigs.modBlock("ltxi:hydrosieve"));
        plot.block("3 0 -3", Rigs.modBlock("ae2:debug_energy_gen"));
        if (bus) {
            plot.cable("1 0 -4");
            plot.cable("2 0 -4").part(Direction.SOUTH, AEParts.IMPORT_BUS, part -> {
                part.getUpgrades().addItems(AEItems.SPEED_CARD.stack(4));
                var primary = outputOf(pattern);
                part.getConfig().insert(0, primary, 1, appeng.api.config.Actionable.MODULATE);
            });
        }
        var drive = plot.drive(new BlockPos(0, 0, -6));
        drive.addCreativeCell().add(fluid);
        drive.addItemCell64k().add(stockItem, 64);
        plot.creativeEnergyCell("1 0 -5");
    }

    private static AEItemKey outputOf(ItemStack pattern) {
        var details = PatternDetailsHelper.decodePattern(pattern, null);
        if (details != null && details.getPrimaryOutput().what() instanceof AEItemKey key) {
            return key;
        }
        throw new IllegalStateException("pattern has no item output");
    }

    /** 1 gravel + 125 mB water -> 1 flint (the guaranteed part; the sieve adds a 50% second flint). */
    private static ItemStack flintPattern() {
        return chancePattern(Items.GRAVEL, Fluids.WATER, 125, Items.FLINT);
    }

    private static ItemStack chancePattern(Item input, Fluid fluid, int mb, Item output) {
        var stack = PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(input), 1), new GenericStack(AEFluidKey.of(fluid), mb)),
                List.of(new GenericStack(AEItemKey.of(output), 1)));
        return ChanceOutputMarker.withMarker(stack, true);
    }

    /**
     * Every side of the sieve gets INPUT_AND_OUTPUT item access (default: input only, no side exposes the output
     * slots). LTX Industries is not a compile dependency, so this goes through reflection.
     */
    private static void openItemSides(BlockEntity sieve) {
        try {
            var resourceType = Class.forName("liedge.ltxindustries.blockentity.base.ResourceType");
            var configType = Class.forName("liedge.ltxindustries.blockentity.base.BlockIOConfiguration");
            var sideType = Class.forName("liedge.limacore.blockentity.RelativeHorizontalSide");
            var accessType = Class.forName("liedge.limacore.blockentity.IOAccess");
            Object items = enumConstant(resourceType, "ITEMS");
            Object both = enumConstant(accessType, "INPUT_AND_OUTPUT");
            Object config = sieve.getClass().getMethod("getIOConfiguration", resourceType).invoke(sieve, items);
            var setAccess = configType.getMethod("setIOAccess", sideType, accessType);
            for (Object side : sideType.getEnumConstants()) {
                config = setAccess.invoke(config, side, both);
            }
            boolean ok = (Boolean) sieve.getClass().getMethod("setIOConfiguration", resourceType, configType)
                    .invoke(sieve, items, config);
            if (!ok) {
                throw new IllegalStateException("the hydrosieve rejected the item IO configuration");
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot configure the hydrosieve: " + e, e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumConstant(Class<?> type, String name) {
        return Enum.valueOf((Class) type, name);
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getOptional(Identifier.parse(id))
                .orElseThrow(() -> new IllegalStateException("item not in this pack: " + id));
    }

    private static Fluid fluid(String id) {
        return BuiltInRegistries.FLUID.getOptional(Identifier.parse(id))
                .orElseThrow(() -> new IllegalStateException("fluid not in this pack: " + id));
    }

    /** The pull host of the provider (same lookup as the pull rigs). */
    private static ProviderOutputPullHost host(PlotTestHelper helper, BlockPos providerPos) {
        var be = helper.getLevel().getBlockEntity(helper.absolutePos(providerPos));
        if (be instanceof IActionHost actionHost && actionHost.getActionableNode() != null
                && actionHost.getActionableNode().getService(ICraftingProvider.class)
                        instanceof ProviderOutputPullHost host) {
            return host;
        }
        throw helper.assertionException("no pull-capable pattern provider at " + providerPos + ": " + be, providerPos);
    }
}
