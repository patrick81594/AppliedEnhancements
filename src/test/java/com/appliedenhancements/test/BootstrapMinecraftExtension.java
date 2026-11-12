package com.appliedenhancements.test;

import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Starts the ephemeral server (once per JUnit session) before a test class's own {@code @BeforeAll},
 * even if the test does not inject a {@code MinecraftServer}.
 */
public class BootstrapMinecraftExtension implements BeforeAllCallback {
    @Override
    public void beforeAll(ExtensionContext context) {
        EphemeralTestServerProvider.grabServer();
    }
}
