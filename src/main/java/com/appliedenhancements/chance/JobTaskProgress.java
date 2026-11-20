package com.appliedenhancements.chance;

import appeng.api.crafting.IPatternDetails;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adds crafts to a job's task map. The map values (ExecutingCraftingJob.TaskProgress, in AE2 and in
 * AdvancedAE) are package-private classes with a package-private constructor, so they are created and
 * read by reflection.
 */
public final class JobTaskProgress {
    private record Access(Constructor<?> constructor, Field value) {
    }

    private static final Map<String, Access> CACHE = new ConcurrentHashMap<>();

    private JobTaskProgress() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void add(Map<IPatternDetails, ?> tasks, IPatternDetails pattern, long crafts,
            String progressClassName, ClassLoader loader) throws ReflectiveOperationException {
        var access = CACHE.get(progressClassName);
        if (access == null) {
            var type = Class.forName(progressClassName, false, loader);
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            var value = type.getDeclaredField("value");
            value.setAccessible(true);
            access = new Access(constructor, value);
            CACHE.put(progressClassName, access);
        }
        Object progress = tasks.get(pattern);
        if (progress == null) {
            progress = access.constructor().newInstance();
            ((Map) tasks).put(pattern, progress);
        }
        access.value().setLong(progress, access.value().getLong(progress) + crafts);
    }
}
