package com.appliedenhancements.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Keeps the feature modules extractable: nothing outside the module packages (the AELIS code, the shared core) may
 * reference {@code com.appliedenhancements.chance / diag / outputpull / matrixfix}. The only allowed entry points
 * are the mod class (registers the chance data component) and {@code NetworkHandler} (payload registration).
 * Modules may use the shared core (Config, FeedbackToastPayload, ...), never the other way round.
 */
class ModuleIsolationGuardTest {
    private static final Pattern MODULE_REFERENCE = Pattern.compile(
            "com\\.appliedenhancements\\.(?:chance|diag|outputpull|matrixfix)\\b");
    private static final Set<String> MODULES = Set.of("chance", "diag", "outputpull", "matrixfix");
    private static final Set<String> ENTRY_POINTS = Set.of("AppliedEnhancements.java", "network/NetworkHandler.java");

    @Test
    void sharedAndAelisCodeDoesNotReferenceModules() throws IOException {
        Path root = Path.of(System.getProperty("user.dir")).resolve("src/main/java/com/appliedenhancements");
        Set<String> offenders = new TreeSet<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                if (MODULES.contains(relative.split("/")[0]) || ENTRY_POINTS.contains(relative)) {
                    continue;
                }
                for (String line : Files.readAllLines(file)) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                        continue;
                    }
                    if (MODULE_REFERENCE.matcher(line).find()) {
                        offenders.add(relative);
                    }
                }
            }
        }
        assertEquals(Set.of(), offenders, () -> "Code outside the feature modules references a module: " + offenders);
    }
}
