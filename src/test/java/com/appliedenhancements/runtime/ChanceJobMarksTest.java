package com.appliedenhancements.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.appliedenhancements.test.TestAEKey;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChanceJobMarksTest {
    @Test
    void remembersAddedKeysAndIgnoresNull() {
        var marks = new ChanceJobMarks();
        var flint = new TestAEKey("flint");
        marks.add(flint);
        marks.add(null);
        assertTrue(marks.contains(flint));
        assertFalse(marks.contains(new TestAEKey("gravel")));
        assertFalse(marks.contains(null));
        assertEquals(List.of(flint), marks.toList());
    }

    @Test
    void loadReplacesAndClearEmpties() {
        var marks = new ChanceJobMarks();
        marks.add(new TestAEKey("old"));
        var flint = new TestAEKey("flint");
        marks.load(List.of(flint));
        assertEquals(List.of(flint), marks.toList());
        marks.clear();
        assertTrue(marks.isEmpty());
    }
}
