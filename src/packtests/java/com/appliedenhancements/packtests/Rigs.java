package com.appliedenhancements.packtests;

import appeng.api.stacks.AEKey;
import appeng.core.definitions.AEBlocks;
import appeng.server.testworld.PlotBuilder;
import appeng.server.testworld.PlotTestHelper;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared building blocks and reporting for the pack-test rigs. */
public final class Rigs {
    private static final Logger LOG = LoggerFactory.getLogger("appliedenhancements-packtests");

    private Rigs() {
    }

    /**
     * A 3x3x3 AE2 crafting CPU at x/z -1..1, y 0..2 (same layout as AE2's own crafting rigs). The grid origin
     * (0,0,0) is one of its blocks, so {@code helper.getGrid(BlockPos.ZERO)} reaches the network.
     */
    public static void ae2CraftingCpu(PlotBuilder plot) {
        plot.block("[-1,1] [0,2] [-1,1]", AEBlocks.CRAFTING_STORAGE_64K);
        plot.block("[-1,1] 0 [-1,1]", AEBlocks.CRAFTING_ACCELERATOR);
        plot.block("0 1 -1", AEBlocks.CRAFTING_MONITOR);
    }

    /** A block from an optional addon, looked up by id (addons are not compile dependencies). */
    public static net.minecraft.world.level.block.Block modBlock(String id) {
        var key = net.minecraft.resources.Identifier.parse(id);
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(key)
                .orElseThrow(() -> new IllegalStateException("block not in this pack: " + id));
    }

    /**
     * Smallest ExtendedAE Assembler Matrix (3 wide in x, 3 high, 4 deep in z) with its lowest x/y/z corner at
     * {@code (x0, y0, z0)}: edges = frame, faces = wall, the two interior blocks = pattern core (z0+1) and crafter
     * core (z0+2). Returns the position of the pattern core.
     */
    public static BlockPos assemblerMatrix(PlotBuilder plot, int x0, int y0, int z0) {
        var frame = modBlock("extendedae:assembler_matrix_frame");
        var wall = modBlock("extendedae:assembler_matrix_wall");
        BlockPos patternCore = new BlockPos(x0 + 1, y0 + 1, z0 + 1);
        for (int x = x0; x <= x0 + 2; x++) {
            for (int y = y0; y <= y0 + 2; y++) {
                for (int z = z0; z <= z0 + 3; z++) {
                    int boundaries = (x == x0 || x == x0 + 2 ? 1 : 0) + (y == y0 || y == y0 + 2 ? 1 : 0)
                            + (z == z0 || z == z0 + 3 ? 1 : 0);
                    var pos = new BlockPos(x, y, z);
                    if (boundaries >= 2) {
                        plot.block(pos, frame);
                    } else if (boundaries == 1) {
                        plot.block(pos, wall);
                    } else if (pos.equals(patternCore)) {
                        plot.block(pos, modBlock("extendedae:assembler_matrix_pattern"));
                    } else {
                        plot.block(pos, modBlock("extendedae:assembler_matrix_crafter"));
                    }
                }
            }
        }
        return patternCore;
    }

    /** Puts an encoded pattern into any AE2 PatternContainer block (provider, matrix pattern core, …). */
    public static void insertPattern(PlotTestHelper helper, BlockPos pos, net.minecraft.world.item.ItemStack pattern) {
        var be = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(be instanceof appeng.helpers.patternprovider.PatternContainer container)) {
            throw helper.assertionException("no pattern container at " + pos + ": " + be, pos);
        }
        var rest = container.getTerminalPatternInventory().addItems(pattern);
        helper.check(rest.isEmpty(), "pattern did not fit into " + pos);
        try { // ExtendedAE: make sure the matrix re-reads its patterns (no-op elsewhere)
            var update = be.getClass().getMethod("updatePatterns");
            update.invoke(be);
        } catch (ReflectiveOperationException ignored) {
            // not an ExtendedAE pattern core
        }
    }

    /** Items and fluids inside a block (via its capabilities), e.g. "[minecraft:glass_bottle x3, …]". */
    public static String contents(PlotTestHelper helper, BlockPos pos) {
        // Read every slot/tank through NeoForge's transfer capabilities (works for any mod's block entity).
        var level = helper.getLevel();
        var abs = helper.absolutePos(pos);
        var out = new StringBuilder("[");
        try {
            var items = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, abs, null);
            if (items != null) {
                for (int i = 0; i < items.size(); i++) {
                    long amount = items.getAmountAsLong(i);
                    if (amount > 0) {
                        out.append(out.length() > 1 ? ", " : "").append("slot").append(i).append('=')
                                .append(items.getResource(i)).append(" x").append(amount);
                    }
                }
            }
            var fluids = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK, abs, null);
            if (fluids != null) {
                for (int i = 0; i < fluids.size(); i++) {
                    long amount = fluids.getAmountAsLong(i);
                    if (amount > 0) {
                        out.append(out.length() > 1 ? ", " : "").append("tank").append(i).append('=')
                                .append(fluids.getResource(i)).append(' ').append(amount).append("mB");
                    }
                }
            }
            if (items == null && fluids == null) {
                out.append("no item/fluid capability");
            }
        } catch (RuntimeException e) {
            out.append('<').append(e.getClass().getSimpleName()).append(": ").append(e.getMessage()).append('>');
        }
        return out.append(']').toString();
    }

    /** Amount of {@code key} the network at the rig origin can see. */
    public static long stored(PlotTestHelper helper, AEKey key) {
        return helper.getGrid(BlockPos.ZERO).getStorageService().getInventory().getAvailableStacks().get(key);
    }

    /** One machine-readable result line per rig, collected by run-pack-tests. */
    public static void report(String rig, String values) {
        LOG.info("[AE-PACKTEST] rig={} {}", rig, values);
    }
}
