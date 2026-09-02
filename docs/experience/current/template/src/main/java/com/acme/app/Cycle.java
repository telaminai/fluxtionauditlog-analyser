package com.acme.app;

import java.util.ArrayList;
import java.util.List;

/**
 * What ran this cycle, and what committed — the two phases, recorded separately.
 *
 * <p>Nodes call {@link #evaluated} from their {@code @OnTrigger}/{@code @OnEventHandler} method and
 * {@link #committed} from their {@code @AfterTrigger} method. `Main` drains both once per event.
 */
public class Cycle {

    private static final List<String> evaluated = new ArrayList<>();
    private static final List<String> committed = new ArrayList<>();

    public static void evaluated(String node) { evaluated.add(node); }
    public static void committed(String node) { committed.add(node); }

    /** Evaluation order, then commit order, as one line: `a|b|c|commit:c|commit:b|commit:a`. */
    public static String drain() {
        List<String> all = new ArrayList<>(evaluated);
        for (String c : committed) all.add("commit:" + c);
        evaluated.clear();
        committed.clear();
        return String.join("|", all);
    }

    public static void reset() { evaluated.clear(); committed.clear(); }
}
