package com.appliedenhancements.packtests;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEBlocks;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.server.testplots.CraftingPatternHelper;
import appeng.server.testplots.TestPlot;
import appeng.server.testplots.TestPlotClass;
import appeng.server.testworld.PlotBuilder;
import appeng.server.testworld.PlotTestHelper;
import appeng.server.testworld.TestCraftingJob;
import com.appliedenhancements.Config;
import com.appliedenhancements.provider.ProviderOutputPull;
import com.appliedenhancements.provider.ProviderOutputPullHost;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

/**
 * Feature G (pattern provider pulls outputs back): the sugar chain of {@link SugarChainTestPlots}, but WITHOUT an
 * import bus — the honey-bottle provider pulls the filled bottles out of the Productive Bees bottler itself (like
 * Patrick's Pipez pipes). Also checks the input protection: a key that is an input of any pattern on the provider
 * is never pulled. These tests change server-wide settings: run them one at a time.
 *
 * <p>Provider types: AE2's (block), ExtendedAE's extended provider (same AE2 logic) and AdvancedAE's Advanced and
 * Small Advanced providers (their own logic, reached through the same generic pull lookup).
 */
@TestPlotClass
public final class OutputPullTestPlots {
    private static final int SUGAR = 48;
    private static final int MAX_TICKS = 20 * 120;
    private static final BlockPos HONEY_PROVIDER = new BlockPos(-1, 0, -3);
    private static final BlockPos BOTTLER = new BlockPos(-2, 0, -3);
    /** The bottler is WEST of the honey-bottle provider. */
    private static final Direction BOTTLER_FACE = Direction.WEST;

    private OutputPullTestPlots() {
    }

    @TestPlot(value = PackTestsMod.RIG_PREFIX + "pull_sugar_ae2_same_face", maxTicks = MAX_TICKS)
    public static void ae2SameFace(PlotBuilder plot) {
        build(plot, null);
        plot.test(helper -> runSugar(helper, "pull_sugar_ae2_same_face", true, ProviderOutputPull.SAME_FACE));
    }

    @TestPlot(value = PackTestsMod.RIG_PREFIX + "pull_sugar_ae2_planner_off", maxTicks = MAX_TICKS)
    public static void ae2PlannerOff(PlotBuilder plot) {
        build(plot, null);
        plot.test(helper -> runSugar(helper, "pull_sugar_ae2_planner_off", false, ProviderOutputPull.SAME_FACE));
    }

    @TestPlot(value = PackTestsMod.RIG_PREFIX + "pull_sugar_exprovider", maxTicks = MAX_TICKS)
    public static void extendedProvider(PlotBuilder plot) {
        build(plot, "extendedae:ex_pattern_provider");
        plot.test(helper -> runSugar(helper, "pull_sugar_exprovider", true, ProviderOutputPull.SAME_FACE));
    }

    /** AdvancedAE's Advanced Pattern Provider: its own logic, so the Mixin for that logic must do the work. */
    @TestPlot(value = PackTestsMod.RIG_PREFIX + "pull_sugar_adv_provider", maxTicks = MAX_TICKS)
    public static void advProvider(PlotBuilder plot) {
        build(plot, "advanced_ae:adv_pattern_provider");
        plot.test(helper -> runSugar(helper, "pull_sugar_adv_provider", true, ProviderOutputPull.SAME_FACE));
    }

    /** AdvancedAE's Small Advanced Pattern Provider (a subclass of the Advanced one in AdvancedAE). */
    @TestPlot(value = PackTestsMod.RIG_PREFIX + "pull_sugar_small_adv_provider", maxTicks = MAX_TICKS)
    public static void smallAdvProvider(PlotBuilder plot) {
        build(plot, "advanced_ae:small_adv_pattern_provider");
        plot.test(helper -> runSugar(helper, "pull_sugar_small_adv_provider", true, ProviderOutputPull.SAME_FACE));
    }

    /** Pull from the bottler's BOTTOM side although the provider touches its east side. */
    @TestPlot(value = PackTestsMod.RIG_PREFIX + "pull_sugar_ae2_bottom_side", maxTicks = MAX_TICKS)
    public static void ae2BottomSide(PlotBuilder plot) {
        build(plot, null);
        plot.test(helper -> runSugar(helper, "pull_sugar_ae2_bottom_side", true,
                ProviderOutputPull.sideMode(Direction.DOWN)));
    }

    /**
     * Input protection: the honey-bottle provider also holds a pattern that USES honey bottles, so honey bottles
     * are a pattern input there and must never be pulled. One honey bottle is requested; after 10 s it must still
     * sit in the bottler's output and nothing may be recorded as owed.
     */
    @TestPlot(value = PackTestsMod.RIG_PREFIX + "pull_never_takes_inputs", maxTicks = 20 * 30)
    public static void neverTakesInputs(PlotBuilder plot) {
        build(plot, null);
        plot.test(helper -> {
            var honeyBottle = AEItemKey.of(Items.HONEY_BOTTLE);
            var job = new TestCraftingJob(helper, BlockPos.ZERO, honeyBottle, 1);
            helper.startSequence()
                    .thenExecute(() -> {
                        Config.ENABLE_PROVIDER_OUTPUT_PULL.set(true);
                        // A second pattern on the same provider whose INPUT is a honey bottle.
                        Rigs.insertPattern(helper, HONEY_PROVIDER, PatternDetailsHelper.encodeProcessingPattern(
                                List.of(new GenericStack(honeyBottle, 1)),
                                List.of(new GenericStack(AEItemKey.of(Items.HONEYCOMB), 1))));
                        host(helper).appliedenhancements$setPullMode(BOTTLER_FACE, ProviderOutputPull.SAME_FACE);
                    })
                    .thenWaitUntil(job::tickUntilStarted)
                    .thenIdle(200)
                    .thenExecute(() -> {
                        var bottler = Rigs.contents(helper, BOTTLER);
                        var owed = host(helper).appliedenhancements$getOwedOutputs(BOTTLER_FACE);
                        Rigs.report("pull_never_takes_inputs", "bottler=" + bottler + " owed=" + describe(owed)
                                + " netHoney=" + Rigs.stored(helper, honeyBottle));
                        helper.check(bottler.contains("minecraft:honey_bottle"),
                                "the honey bottle (a pattern input) must stay in the bottler: " + bottler);
                        helper.check(owed.isEmpty(), "nothing may be owed for a pattern input: " + describe(owed));
                    })
                    .thenSucceed();
        });
    }

    /** Global switch off: a configured face must not pull (old behaviour: the output stays in the bottler). */
    @TestPlot(value = PackTestsMod.RIG_PREFIX + "pull_global_switch_off", maxTicks = 20 * 30)
    public static void globalSwitchOff(PlotBuilder plot) {
        build(plot, null);
        plot.test(helper -> {
            var honeyBottle = AEItemKey.of(Items.HONEY_BOTTLE);
            var job = new TestCraftingJob(helper, BlockPos.ZERO, honeyBottle, 1);
            helper.startSequence()
                    .thenExecute(() -> {
                        Config.ENABLE_PROVIDER_OUTPUT_PULL.set(false);
                        host(helper).appliedenhancements$setPullMode(BOTTLER_FACE, ProviderOutputPull.SAME_FACE);
                    })
                    .thenWaitUntil(job::tickUntilStarted)
                    .thenIdle(200)
                    .thenExecute(() -> {
                        var bottler = Rigs.contents(helper, BOTTLER);
                        Rigs.report("pull_global_switch_off", "bottler=" + bottler
                                + " netHoney=" + Rigs.stored(helper, honeyBottle));
                        helper.check(bottler.contains("minecraft:honey_bottle"),
                                "with the switch off the output must stay in the bottler: " + bottler);
                    })
                    .thenSucceed();
        });
    }

    private static void runSugar(PlotTestHelper helper, String rig, boolean chunks, byte pullMode) {
        var sugar = AEItemKey.of(Items.SUGAR);
        var glass = AEItemKey.of(Items.GLASS_BOTTLE);
        var job = new TestCraftingJob(helper, BlockPos.ZERO, sugar, SUGAR);
        helper.onEachTick(() -> {
            if (helper.getTick() % 100 == 0) {
                Rigs.report(rig, "tick=" + helper.getTick() + " sugar=" + Rigs.stored(helper, sugar)
                        + " netGlass=" + Rigs.stored(helper, glass)
                        + " bottler=" + Rigs.contents(helper, BOTTLER)
                        + " owed=" + describe(host(helper).appliedenhancements$getOwedOutputs(BOTTLER_FACE)));
            }
        });
        helper.startSequence()
                .thenExecute(() -> {
                    Config.ENABLE_PROVIDER_OUTPUT_PULL.set(true);
                    Config.AELIS_CHUNK_RETURNED_ITEM_FEEDBACK.set(chunks);
                    host(helper).appliedenhancements$setPullMode(BOTTLER_FACE, pullMode);
                })
                .thenWaitUntil(job::tickUntilStarted)
                .thenWaitUntil(() -> {
                    long have = Rigs.stored(helper, sugar);
                    helper.check(have >= SUGAR, "expected " + SUGAR + " sugar, have " + have);
                })
                .thenExecute(() -> Rigs.report(rig, "sugarDone tick=" + helper.getTick()))
                .thenWaitUntil(() -> {
                    long back = Rigs.stored(helper, glass);
                    helper.check(back == 8, "all 8 glass bottles should be back, have " + back);
                })
                .thenExecute(() -> {
                    var bottler = Rigs.contents(helper, BOTTLER);
                    Rigs.report(rig, "done tick=" + helper.getTick() + " sugar=" + Rigs.stored(helper, sugar)
                            + " glassBottlesAfter=" + Rigs.stored(helper, glass) + " bottlerAfter=" + bottler);
                    // Nothing that is an input may have been pulled: the bottler ends empty only because the
                    // crafts consumed bottles and honey, so no glass bottle or honey may remain stuck in it either.
                    helper.check(!bottler.contains("minecraft:glass_bottle"), "bottles stuck in the bottler: " + bottler);
                })
                .thenSucceed();
    }

    /**
     * Same layout as the sugar rig without the import bus. {@code providerId} = null: AE2's pattern provider;
     * otherwise the block id of an addon provider holding the honey-bottle pattern (its BE must have a pattern
     * container, which all of them have).
     */
    private static void build(PlotBuilder plot, @Nullable String providerId) {
        Rigs.ae2CraftingCpu(plot);
        plot.cable("0 0 -2");
        plot.cable("0 0 -3");
        plot.cable("0 0 -4");
        plot.cable("0 0 -5");
        plot.blockEntity("1 0 -3", AEBlocks.PATTERN_PROVIDER, provider -> provider.getLogic().getPatternInv()
                .addItems(CraftingPatternHelper.encodeShapelessCraftingRecipe(
                        (net.minecraft.server.level.ServerLevel) provider.getLevel(),
                        Items.HONEY_BOTTLE.getDefaultInstance())));
        plot.block("2 0 -3", AEBlocks.MOLECULAR_ASSEMBLER);
        Fluid honey = honey();
        ItemStack honeyPattern = PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(Items.GLASS_BOTTLE), 1),
                        new GenericStack(AEFluidKey.of(honey), 250)),
                List.of(new GenericStack(AEItemKey.of(Items.HONEY_BOTTLE), 1)));
        if (providerId == null) {
            plot.blockEntity("-1 0 -3", AEBlocks.PATTERN_PROVIDER,
                    provider -> provider.getLogic().getPatternInv().addItems(honeyPattern.copy()));
        } else if (providerId.startsWith("extendedae:")) {
            Block block = Rigs.modBlock(providerId);
            plot.block(HONEY_PROVIDER, block);
            plot.customizeBlockEntity(HONEY_PROVIDER, beType(block),
                    be -> ((PatternProviderLogicHost) be).getLogic().getPatternInv().addItems(honeyPattern.copy()));
        } else {
            Block block = Rigs.modBlock(providerId);
            plot.block(HONEY_PROVIDER, block);
            plot.customizeBlockEntity(HONEY_PROVIDER, beType(block), be -> putPattern(be, honeyPattern));
        }
        plot.block(BOTTLER, Rigs.modBlock("productivebees:bottler"));
        // Cable so the honey-bottle provider is on the network (no import bus on the bottler).
        plot.cable("-1 0 -4");
        var drive = plot.drive(new BlockPos(0, 0, -6));
        drive.addCreativeCell().add(honey);
        drive.addItemCell64k().add(Items.GLASS_BOTTLE, 8);
        plot.creativeEnergyCell("1 0 -5");
    }

    /** Puts a pattern into the first free slot of a provider's pattern container (AE2's interface, any addon). */
    private static void putPattern(BlockEntity be, ItemStack pattern) {
        if (!(be instanceof PatternContainer container)) {
            throw new IllegalStateException(be + " has no pattern container");
        }
        InternalInventory inv = container.getTerminalPatternInventory();
        for (int slot = 0; slot < inv.size(); slot++) {
            if (inv.getStackInSlot(slot).isEmpty()) {
                inv.setItemDirect(slot, pattern.copy());
                return;
            }
        }
        throw new IllegalStateException("no free pattern slot in " + be);
    }

    /** The block entity type that this block's state belongs to (asked of the registry, not assumed by id). */
    @SuppressWarnings("unchecked")
    private static BlockEntityType<BlockEntity> beType(Block block) {
        var state = block.defaultBlockState();
        for (var type : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
            if (type.isValid(state)) {
                return (BlockEntityType<BlockEntity>) type;
            }
        }
        throw new IllegalStateException(block + " has no block entity type");
    }

    /**
     * The pull host of the provider at the test position, found through its grid node the way the server does it
     * (open menu → grid node → crafting-provider service), so it works for AE2, ExtendedAE and AdvancedAE alike.
     */
    private static ProviderOutputPullHost host(PlotTestHelper helper) {
        var be = helper.getLevel().getBlockEntity(helper.absolutePos(HONEY_PROVIDER));
        if (be instanceof IActionHost actionHost && actionHost.getActionableNode() != null
                && actionHost.getActionableNode().getService(ICraftingProvider.class)
                        instanceof ProviderOutputPullHost host) {
            return host;
        }
        throw helper.assertionException("no pull-capable pattern provider at " + HONEY_PROVIDER + ": " + be,
                HONEY_PROVIDER);
    }

    private static String describe(appeng.api.stacks.KeyCounter counter) {
        var out = new StringBuilder("[");
        for (var entry : counter) {
            out.append(out.length() > 1 ? ", " : "").append(entry.getKey()).append(" x").append(entry.getLongValue());
        }
        return out.append(']').toString();
    }

    private static Fluid honey() {
        return BuiltInRegistries.FLUID.getOptional(Identifier.parse("productivebees:honey"))
                .orElseThrow(() -> new IllegalStateException("productivebees:honey not in this pack"));
    }
}
