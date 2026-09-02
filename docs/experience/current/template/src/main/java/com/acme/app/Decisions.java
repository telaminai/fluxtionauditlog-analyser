package com.acme.app;

import java.util.ArrayList;
import java.util.List;

/**
 * The decisions made in the current cycle. A LIST, not a field.
 *
 * <p>One event can produce several decisions of the same kind — one RECEIPT can release three orders —
 * and a node that holds a single decision per cycle silently drops all but one. Three engines built
 * before this template failed exactly that case, in three different ways.
 *
 * <p>{@code Main} calls {@link #drain()} once per event and writes what it gets.
 */
public class Decisions {

    private static final List<String> current = new ArrayList<>();

    /** Record a decision made in this cycle. Call once per decision, not once per cycle. */
    public static void add(String decision, String key) {
        current.add(decision + "," + key);
    }

    /**
     * Clear everything, including anything left from a previous run.
     *
     * <p>Call this before each run. This holder is static, so a second processor in the same JVM — a
     * second test method, typically — starts with whatever the first one left behind. That produces a
     * test failure that looks like a rule bug and is not one.
     */
    public static void reset() {
        current.clear();
    }

    /** Take everything decided this cycle and clear, ready for the next. */
    public static List<String> drain() {
        List<String> out = new ArrayList<>(current);
        current.clear();
        return out;
    }
}
