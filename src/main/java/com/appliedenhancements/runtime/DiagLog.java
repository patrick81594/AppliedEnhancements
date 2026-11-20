package com.appliedenhancements.runtime;

import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * TEMPORARY diagnostics for the Assembler Matrix investigation (2026-11-18). Logs at INFO with the
 * prefix "[AE-DIAG]", at most once per second per key. Remove together with AssemblerMatrixDiagMixin
 * once the cause is fixed.
 */
@ApiStatus.Internal
public final class DiagLog {
    private static final Logger LOGGER = LoggerFactory.getLogger("appliedenhancements-diag");
    private static final long INTERVAL_MS = 1000;
    private static final ConcurrentHashMap<String, Long> LAST = new ConcurrentHashMap<>();

    private DiagLog() {
    }

    public static void log(String key, String message, Object... args) {
        long now = System.currentTimeMillis();
        Long previous = LAST.get(key);
        if (previous != null && now - previous < INTERVAL_MS) {
            return;
        }
        LAST.put(key, now);
        if (LAST.size() > 512) {
            LAST.clear();
        }
        LOGGER.info("[AE-DIAG] " + message, args);
    }

    /** True at most once per {@code intervalMs} for {@code key}. */
    public static boolean every(String key, long intervalMs) {
        long now = System.currentTimeMillis();
        Long previous = LAST.get("every." + key);
        if (previous != null && now - previous < intervalMs) {
            return false;
        }
        LAST.put("every." + key, now);
        return true;
    }

    /**
     * Logs the state of every crafter thread of an ExtendedAE assembler matrix cluster: pattern, plan, awake,
     * progress, grid contents and pending output. Reflection only (ExtendedAE is not a compile dependency).
     */
    public static void dumpMatrixThreads(Object cluster) {
        try {
            int crafterIndex = 0;
            int busyThreads = 0;
            for (var crafter : collectionsOf(cluster, "TileAssemblerMatrixCrafter")) {
                Object threads = probe(crafter, "threads");
                if (!(threads instanceof Object[] array)) {
                    continue;
                }
                for (int t = 0; t < array.length; t++) {
                    Object thread = array[t];
                    if (thread == null) {
                        continue;
                    }
                    Object pattern = probe(thread, "myPattern");
                    Object grid = probe(thread, "gridInv");
                    String gridText = inventoryText(grid);
                    boolean idle = (pattern instanceof net.minecraft.world.item.ItemStack stack && stack.isEmpty())
                            && "[]".equals(gridText);
                    if (idle) {
                        continue;
                    }
                    busyThreads++;
                    LOGGER.info("[AE-DIAG] matrix thread crafter#{} thread#{} pos={} pattern={} plan={} awake={} "
                                    + "progress={} reboot={} forcePlan={} pushDirection={} output={} grid={}",
                            crafterIndex, t, probe(crafter, "worldPosition"), patternText(pattern),
                            planText(probe(thread, "myPlan")), probe(thread, "isAwake"), probe(thread, "progress"),
                            probe(thread, "reboot"), probe(thread, "forcePlan"), probe(thread, "pushDirection"),
                            probe(thread, "output"), gridText);
                }
                crafterIndex++;
            }
            LOGGER.info("[AE-DIAG] matrix dump: {} crafter(s), {} occupied thread(s), availableCrafters={}",
                    crafterIndex, busyThreads, sizeOf(probe(cluster, "availableCrafters")));
        } catch (RuntimeException e) {
            LOGGER.info("[AE-DIAG] matrix dump failed: {}", e.toString());
        }
    }

    private static java.util.List<Object> collectionsOf(Object owner, String elementClassPart) {
        var out = new java.util.ArrayList<Object>();
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            for (var field : type.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(owner);
                    if (value instanceof java.util.Collection<?> collection && !collection.isEmpty()
                            && collection.iterator().next() != null
                            && collection.iterator().next().getClass().getName().contains(elementClassPart)) {
                        if (field.getName().toLowerCase().contains("available")) {
                            continue; // only the full crafter list
                        }
                        out.addAll(collection);
                        return out;
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // next field
                }
            }
        }
        return out;
    }

    private static String inventoryText(Object inventory) {
        if (!(inventory instanceof appeng.api.inventories.InternalInventory inv)) {
            return String.valueOf(inventory);
        }
        var out = new StringBuilder("[");
        for (int i = 0; i < inv.size(); i++) {
            var stack = inv.getStackInSlot(i);
            if (!stack.isEmpty()) {
                out.append(out.length() > 1 ? ", " : "").append(i).append('=').append(stack.getCount()).append('x')
                        .append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()));
            }
        }
        return out.append(']').toString();
    }

    private static String patternText(Object pattern) {
        if (pattern instanceof net.minecraft.world.item.ItemStack stack && !stack.isEmpty()) {
            try {
                var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
                var level = server == null ? null : server.overworld();
                var details = appeng.api.crafting.PatternDetailsHelper.decodePattern(stack, level);
                return details == null ? "UNDECODABLE " + stack : String.valueOf(details.getPrimaryOutput());
            } catch (RuntimeException e) {
                return "decode failed (" + e.getClass().getSimpleName() + ") " + stack;
            }
        }
        return "empty";
    }

    private static String planText(Object plan) {
        if (plan instanceof appeng.api.crafting.IPatternDetails details) {
            return details.getClass().getSimpleName() + "->" + details.getPrimaryOutput();
        }
        return String.valueOf(plan);
    }

    private static String sizeOf(Object collection) {
        return collection instanceof java.util.Collection<?> c ? String.valueOf(c.size()) : String.valueOf(collection);
    }

    /** "key xN, key xM" for a KeyCounter (diagnostics). */
    public static String counterText(appeng.api.stacks.KeyCounter counter) {
        var out = new StringBuilder("[");
        for (var entry : counter) {
            out.append(out.length() > 1 ? ", " : "").append(entry.getKey()).append(" x").append(entry.getLongValue());
        }
        return out.append(']').toString();
    }

    public static boolean isMatrix(Object provider) {
        return provider != null && provider.getClass().getName().contains("AssemblerMatrix");
    }

    /** Reads a no-arg method or field by name, for logging only; never throws. */
    public static Object probe(Object target, String name) {
        if (target == null) {
            return null;
        }
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var method = type.getDeclaredMethod(name);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // try a field next
            }
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // try the superclass
            }
        }
        return "?";
    }
}
