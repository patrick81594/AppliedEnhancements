package com.appliedenhancements.test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds every Mixin config of the mod, the same way FML does: from the {@code [[mixins]] config = "..."} entries of
 * {@code META-INF/neoforge.mods.toml}. Every feature module has its own Mixin config, so the Mixin tests must loop
 * over all of them instead of hard-coding one file name.
 */
public final class MixinConfigs {
    private static final Pattern CONFIG_ENTRY = Pattern.compile("(?m)^\\s*config\\s*=\\s*\"([^\"]+\\.mixins\\.json)\"");
    private static final String MODS_TOML = "META-INF/neoforge.mods.toml";

    private MixinConfigs() {
    }

    /** Config file names listed in a mods.toml text, in file order. */
    public static List<String> fromModsToml(String modsTomlText) {
        List<String> names = new ArrayList<>();
        Matcher matcher = CONFIG_ENTRY.matcher(modsTomlText);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /** Config file names listed in the mods.toml on the test classpath. */
    public static List<String> fromClasspath(ClassLoader loader) throws IOException {
        // Under FML the classpath holds the mods.toml of every mod (AE2, ...): keep only our own configs.
        List<String> names = new ArrayList<>();
        var urls = loader.getResources(MODS_TOML);
        while (urls.hasMoreElements()) {
            try (InputStream in = urls.nextElement().openStream()) {
                for (String name : fromModsToml(new String(in.readAllBytes(), StandardCharsets.UTF_8))) {
                    if (name.startsWith("appliedenhancements") && !names.contains(name)) {
                        names.add(name);
                    }
                }
            }
        }
        if (names.isEmpty()) {
            throw new IllegalStateException("no appliedenhancements*.mixins.json listed in any " + MODS_TOML);
        }
        return names;
    }
}
