package com.appliedenhancements.mixin;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Guards against an {@code @Inject} handler that calls {@code CallbackInfo.cancel()} or
 * {@code CallbackInfoReturnable.setReturnValue(..)} without {@code cancellable = true}. Mixin accepts that at
 * load time but throws {@code CancellationException} the first time the call runs, which crashes the game
 * (found 2026-11-19: the pattern provider "Pull Outputs" panel's getExclusionZones hook crashed the client
 * when JEI asked for the zones). Reads the compiled Mixin classes with ASM (Mixin classes cannot be loaded
 * directly) and covers both the common and the client list, so client Mixins are checked too.
 */
class InjectCancellableGuardTest {
    private static final String CONFIG = "appliedenhancements.mixins.json";
    private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String CI = "org/spongepowered/asm/mixin/injection/callback/CallbackInfo";
    private static final String CIR = "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable";

    @Test
    void everyCancellingInjectIsCancellable() throws IOException {
        ClassLoader loader = getClass().getClassLoader();
        JsonObject config;
        try (InputStream in = loader.getResourceAsStream(CONFIG)) {
            assertNotNull(in, CONFIG + " not on the test classpath");
            config = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        String pkg = config.get("package").getAsString().replace('.', '/');
        List<String> mixins = new ArrayList<>();
        for (String list : new String[] {"mixins", "client", "server"}) {
            if (config.has(list)) {
                for (JsonElement e : config.getAsJsonArray(list)) {
                    mixins.add(e.getAsString());
                }
            }
        }
        assertTrue(mixins.size() > 20, "expected the full Mixin list, got " + mixins.size());

        List<String> offenders = new ArrayList<>();
        for (String mixin : mixins) {
            String resource = pkg + "/" + mixin.replace('.', '/') + ".class";
            try (InputStream in = loader.getResourceAsStream(resource)) {
                assertNotNull(in, "missing class " + resource);
                new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                            String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            boolean inject;
                            boolean cancellable;
                            boolean cancels;

                            @Override
                            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                                if (!INJECT.equals(desc)) {
                                    return null;
                                }
                                inject = true;
                                return new AnnotationVisitor(Opcodes.ASM9) {
                                    @Override
                                    public void visit(String key, Object value) {
                                        if ("cancellable".equals(key) && Boolean.TRUE.equals(value)) {
                                            cancellable = true;
                                        }
                                    }
                                };
                            }

                            @Override
                            public void visitMethodInsn(int opcode, String owner, String insn, String desc,
                                    boolean itf) {
                                if ((CI.equals(owner) || CIR.equals(owner))
                                        && ("cancel".equals(insn) || "setReturnValue".equals(insn))) {
                                    cancels = true;
                                }
                            }

                            @Override
                            public void visitEnd() {
                                if (inject && cancels && !cancellable) {
                                    offenders.add(mixin + "." + name);
                                }
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        assertTrue(offenders.isEmpty(),
                "@Inject handlers that cancel or set a return value need cancellable = true: " + offenders);
    }
}
