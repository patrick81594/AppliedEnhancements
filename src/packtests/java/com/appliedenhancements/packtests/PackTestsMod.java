package com.appliedenhancements.packtests;

import net.neoforged.fml.common.Mod;

/**
 * TEST-ONLY mod. Holds in-world test rigs ({@code @TestPlotClass} classes in this package) that AE2 finds by
 * annotation scan and registers as Minecraft game tests when the server runs with
 * {@code -Dneoforge.enableGameTest=true -Dappeng.tests=true}. Never install it in a real instance.
 */
@Mod(PackTestsMod.MOD_ID)
public final class PackTestsMod {
    public static final String MOD_ID = "appliedenhancements_packtests";

    /** Prefix of every rig id; AE2 registers them as {@code ae2:appliedenhancements/<name>}. */
    public static final String RIG_PREFIX = "appliedenhancements/";
}
