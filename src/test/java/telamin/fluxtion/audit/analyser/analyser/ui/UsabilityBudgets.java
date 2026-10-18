package telamin.fluxtion.audit.analyser.analyser.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The usability scorecard's ENFORCED budgets: today's measured values (the baseline at {@code a875fcba},
 * docs/usability/scorecard.md), so any regression fails {@link UsabilityBudgetFrameTest}. A change that makes a
 * number better lowers its budget here in the same commit; one that needs a worse number changes it here with its
 * reason, where review sees it. The TARGETS are the specs' numbers, listed, not yet enforced.
 */
final class UsabilityBudgets {

    private UsabilityBudgets() {
    }

    /** Acts per task (clicks + keys + drags + scrolls), at most. */
    static final Map<String, Integer> ACTS = Map.of(
            "T1", 5, "T2", 5, "T3", 1, "T4", 2, "T5", 8, "T6", 4, "T7", 1, "T8", 1);

    /** Always-visible chrome words in each key state's region, at most. */
    static final Map<String, Integer> WORDS = Map.of("S1", 225, "S2", 31, "S3", 25);

    /** Clipped strings in each key state's region, at most: none. */
    static final Map<String, Integer> CLIPPED = Map.of("S1", 0, "S2", 0, "S3", 0);

    /** The specs' act targets (not enforced yet). */
    static final Map<String, String> ACT_TARGETS = Map.of(
            "T2", "≤ 6 (M77 T19, M77-2)",
            "T4", "≤ 1, 0 hesitations (records-filter fix, proposed)");

    /** The specs' word targets (not enforced yet). */
    static final Map<String, String> WORD_TARGETS = Map.of(
            "S2", "≤ 15 words, ≤ 4 controls (M78 D1, TQ1)");

    /** Every budget a result breaks, in words; empty when all hold. A task that does not succeed breaks its budget. */
    static List<String> violations(List<UsabilityTasks.StateMeasure> states, List<Usability.Run> runs) {
        List<String> out = new ArrayList<>();
        for (UsabilityTasks.StateMeasure s : states) {
            Integer words = WORDS.get(s.id());
            Integer clipped = CLIPPED.get(s.id());
            if (words == null || clipped == null) { out.add(s.id() + " has no budget"); continue; }
            if (s.words() > words) out.add(s.id() + " " + s.name() + ": " + s.words() + " chrome words > budget " + words);
            if (s.clipped().size() > clipped) {
                out.add(s.id() + " " + s.name() + ": " + s.clipped().size() + " clipped > budget " + clipped + ": " + s.clipped());
            }
        }
        for (Usability.Run r : runs) {
            Integer acts = ACTS.get(r.id);
            if (acts == null) { out.add(r.id + " has no budget"); continue; }
            if (!r.success) out.add(r.id + " " + r.name + ": did not succeed (" + r.failure + ")");
            if (r.totalActs() > acts) out.add(r.id + " " + r.name + ": " + r.totalActs() + " acts > budget " + acts);
        }
        return out;
    }
}
