package com.appliedenhancements.chance;

import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The chance module's log output. Every line carries the tag {@code [ACE/chance]} (the tag scheme of the future
 * Applied Crafting Extras mod is {@code [ACE/<module>]}), so log-grep tools can filter the module with one pattern.
 * The message texts after the tag are unchanged.
 */
public final class ChanceLog {
    public static final String TAG = "[ACE/chance] ";

    private static final Logger LOGGER = LoggerFactory.getLogger("appliedcraftingextras-chance");
    private static final long ERROR_INTERVAL_MS = 60_000;
    private static final ConcurrentHashMap<String, Long> LAST_ERROR = new ConcurrentHashMap<>();

    private ChanceLog() {
    }

    public static void info(String message, Object... arguments) {
        LOGGER.info(TAG + message, arguments);
    }

    /** Errors of automatic retry loops are rate limited (once a minute per message) so they cannot flood the log. */
    public static void error(String message, Object... arguments) {
        long now = System.currentTimeMillis();
        Long previous = LAST_ERROR.get(message);
        if (previous != null && now - previous < ERROR_INTERVAL_MS) {
            return;
        }
        if (LAST_ERROR.size() > 128) {
            LAST_ERROR.clear();
        }
        LAST_ERROR.put(message, now);
        LOGGER.error(TAG + message, arguments);
    }
}
