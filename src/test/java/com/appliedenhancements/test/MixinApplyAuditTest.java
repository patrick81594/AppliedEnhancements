package com.appliedenhancements.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Force-loads every class our Mixins target, so each Mixin is really applied, and fails if one does not apply.
 *
 * <p>Why this exists: Mixin targets are strings that javac cannot check, and a Mixin is only applied when its
 * target class loads. A plain server start loads only a handful of AE2 classes (measured: 7 of our 41 common
 * Mixins), so most broken Mixins would stay hidden until a player uses the feature.
 *
 * <p>How it works: the Mixin list comes from {@code appliedenhancements.mixins.json}; each Mixin's targets are
 * read from its class file with ASM (Mixin classes must not be loaded directly). Each target is then loaded
 * without initialising it, which runs the Mixin transformer but no static setup code.
 *
 * <p>Limits: a target that is absent and only targeted by {@code @Pseudo} Mixins (optional mod not installed)
 * is skipped, so Mixins for optional mods are only proven in a game that has those mods. Injections declared
 * with {@code require = 0} may fail silently by design. "Applies" does not mean "behaves correctly".
 *
 * <p>This class must NOT live in {@code com.appliedenhancements.mixin}: that is the declared Mixin package,
 * and Mixin refuses to load ordinary classes from it.
 */
@Tag("fml")
@BootstrapMinecraft
class MixinApplyAuditTest {
    private static final String CONFIG = "appliedenhancements.mixins.json";
    private static final String MIXIN_DESC = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String PSEUDO_DESC = "Lorg/spongepowered/asm/mixin/Pseudo;";

    /** One target class and the Mixins that declare it. */
    private static final class Target {
        final List<String> mixins = new ArrayList<>();
        boolean allPseudo = true;
    }

    @Test
    void everyMixinTargetLoadsWithItsMixinsApplied() throws IOException {
        ClassLoader loader = MixinApplyAuditTest.class.getClassLoader();
        JsonObject config = readConfig(loader);
        String mixinPackage = config.get("package").getAsString();
        Dist dist = FMLEnvironment.getDist();

        // Same selection Mixin makes: "mixins" always, plus the list for the side we run on.
        List<String> mixinNames = new ArrayList<>(names(config, "mixins"));
        mixinNames.addAll(names(config, dist == Dist.CLIENT ? "client" : "server"));
        assertFalse(mixinNames.isEmpty(), "no Mixins listed in " + CONFIG);

        Map<String, Target> targets = new TreeMap<>();
        for (String name : mixinNames) {
            readMixin(loader, mixinPackage + "." + name, name, targets);
        }

        List<String> applied = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (Map.Entry<String, Target> entry : targets.entrySet()) {
            String target = entry.getKey();
            Target info = entry.getValue();
            try {
                // initialize = false: define the class (this is where Mixins are applied) but do not run
                // static initialisers, which for client classes may need a window or graphics context.
                Class.forName(target, false, loader);
                applied.add(target);
            } catch (ClassNotFoundException absent) {
                if (info.allPseudo && absent.getCause() == null) {
                    skipped.add(target + " <- " + info.mixins);
                } else {
                    failed.add(describe(target, info, absent));
                }
            } catch (Throwable error) {
                failed.add(describe(target, info, error));
            }
        }

        System.out.println("[MixinApplyAudit] side=" + dist + " mixins=" + mixinNames.size()
                + " targets=" + targets.size() + " applied=" + applied.size()
                + " skipped(absent optional mod)=" + skipped.size() + " failed=" + failed.size());
        System.out.println("[MixinApplyAudit] loader=" + loader);
        skipped.forEach(line -> System.out.println("[MixinApplyAudit] skipped: " + line));
        failed.forEach(line -> System.out.println("[MixinApplyAudit] FAILED: " + line));

        assertFalse(applied.isEmpty(), "no Mixin target could be loaded; the audit itself is broken");
        assertTrue(failed.isEmpty(),
                failed.size() + " Mixin target(s) failed to load with their Mixins applied:\n"
                        + String.join("\n", failed));
    }

    private static JsonObject readConfig(ClassLoader loader) throws IOException {
        try (InputStream in = loader.getResourceAsStream(CONFIG)) {
            assertNotNull(in, CONFIG + " not found on the test classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static List<String> names(JsonObject config, String key) {
        List<String> result = new ArrayList<>();
        if (config.has(key)) {
            config.getAsJsonArray(key).forEach(element -> result.add(element.getAsString()));
        }
        return result;
    }

    /** Reads {@code @Mixin(value/targets)} and {@code @Pseudo} from the Mixin's class file without loading it. */
    private static void readMixin(ClassLoader loader, String className, String shortName,
            Map<String, Target> targets) throws IOException {
        String resource = className.replace('.', '/') + ".class";
        List<String> declared = new ArrayList<>();
        boolean[] pseudo = {false};
        try (InputStream in = loader.getResourceAsStream(resource)) {
            assertNotNull(in, "Mixin listed in " + CONFIG + " has no class file: " + resource);
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    if (PSEUDO_DESC.equals(descriptor)) {
                        pseudo[0] = true;
                        return null;
                    }
                    if (!MIXIN_DESC.equals(descriptor)) {
                        return null;
                    }
                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override
                        public AnnotationVisitor visitArray(String name) {
                            if (!"value".equals(name) && !"targets".equals(name)) {
                                return null;
                            }
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override
                                public void visit(String ignored, Object value) {
                                    // value = Class[] (ASM Type), targets = String[] ("a.b.C" or "a/b/C")
                                    declared.add(value instanceof Type type
                                            ? type.getClassName()
                                            : value.toString().replace('/', '.'));
                                }
                            };
                        }
                    };
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertFalse(declared.isEmpty(), "no @Mixin targets found in " + className);
        for (String target : declared) {
            Target info = targets.computeIfAbsent(target, key -> new Target());
            info.mixins.add(shortName);
            info.allPseudo &= pseudo[0];
        }
    }

    private static String describe(String target, Target info, Throwable error) {
        StringBuilder text = new StringBuilder(target).append(" <- ").append(info.mixins);
        int depth = 0;
        for (Throwable cause = error; cause != null && depth < 8; cause = cause.getCause(), depth++) {
            text.append("\n      ").append(depth == 0 ? "" : "caused by ").append(cause);
        }
        return text.toString();
    }
}
