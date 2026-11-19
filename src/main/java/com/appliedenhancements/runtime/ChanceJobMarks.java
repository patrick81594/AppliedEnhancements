package com.appliedenhancements.runtime;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The primary output keys of marked (chance-output) patterns that a CPU has pushed in its current job.
 *
 * AE2 drops a pattern from the job's task list as soon as its last craft is pushed, so the bonus rule
 * cannot look at the tasks alone: the extra outputs usually arrive after that. This set remembers the
 * marked patterns for the whole job; each CPU saves it with the job and clears it when the job ends.
 */
public final class ChanceJobMarks {
    private final Set<AEKey> keys = new LinkedHashSet<>();

    /** Records the pattern's primary output when the pattern is marked. */
    public void recordIfMarked(IPatternDetails pattern) {
        if (pattern.getDefinition() instanceof AEItemKey item && ChanceOutputMarker.isMarked(item.toStack())) {
            add(pattern.getPrimaryOutput().what());
        }
    }

    public void add(AEKey key) {
        if (key != null) {
            keys.add(key);
        }
    }

    public boolean contains(AEKey key) {
        return key != null && keys.contains(key);
    }

    public boolean isEmpty() {
        return keys.isEmpty();
    }

    public List<AEKey> toList() {
        return new ArrayList<>(keys);
    }

    public void load(Collection<AEKey> saved) {
        keys.clear();
        saved.forEach(this::add);
    }

    public void clear() {
        keys.clear();
    }
}
