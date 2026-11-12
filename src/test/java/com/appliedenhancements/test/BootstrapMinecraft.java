package com.appliedenhancements.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Starts NeoForge's ephemeral test server for the annotated test class, so Minecraft 26 item data
 * components and tags are bound. Needs a started FML environment, i.e. the `test` task (see
 * build.gradle `fmlTestClasses`). Same approach as AE2 26.1.13's {@code appeng.util.BootstrapMinecraft}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ExtendWith({EphemeralTestServerProvider.class, BootstrapMinecraftExtension.class})
public @interface BootstrapMinecraft {
}
