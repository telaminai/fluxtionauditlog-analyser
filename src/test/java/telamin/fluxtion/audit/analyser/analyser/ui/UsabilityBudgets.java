package telamin.fluxtion.audit.analyser.analyser.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The usability scorecard's ENFORCED budgets: the measured values of the 2026-10-18 integration build
 * ({@code test/usability-2026-10-18}, docs/usability/baseline-integration.md), so any regression fails
 * {@link UsabilityBudgetFrameTest}. A change that makes a number better lowers its budget here in the same commit; one
 * that needs a worse number changes it here with its reason, where review sees it. The TARGETS are the specs' numbers,
 * listed, not yet enforced.
 *
 * <p>Re-baselined on the integration (each change from the {@code a875fcba} budget, with its reason):
 * <ul>
 *   <li>T1 5 → 6: Set up AI's Continue and Finish replace the cards' one Apply (M77-2).</li>
 *   <li>T2 5 → 5: the same extra act, paid for by the scroll the collapsed Event types list no longer needs
 *       (fix/event-types-default-collapsed).</li>
 *   <li>T4 2 → 1: the checklist's floor shows the unticked type without a scroll (fix/records-filter-visibility).</li>
 *   <li>T4c joins the budget (0 acts, must pass): the hidden line and {@code context.hidden} (the records-filter fix).</li>
 *   <li>T5 8 → 5: the mode chip is the quick switch; a row applies at once (M77-4, M78 D2).</li>
 *   <li>T6 4 → 7: RAISED. The start page's Connect block is gone (M77-4, one door): the first-run block's <i>Your own
 *       AI client</i> needs <i>Continue</i> into Screen 2b (+1), and the bridge check is behind Screen 2b's
 *       <i>More options…</i> (+1), below that window's fold (+1 scroll). Flagged to the owner as a finding.</li>
 *   <li>A2 11 → 10: <i>Load this project?</i> is no longer asked for the project already open (the real-path compare,
 *       fix/authoring-walk-defects).</li>
 *   <li>S1 225 → 222: the Connect block's words left with it; the first-run block's came in.</li>
 *   <li>S2 31 → 6: the quiet panel (M78 D1) and the one chip (M78 D2); TQ1's ≤ 15 words and ≤ 4 controls are met.</li>
 *   <li>S3 25 → 26: RAISED by one. The Records title now says what is shown (<i>Records: all 10 shown</i>, +3) and the
 *       floor shows the fourth row (+2); the repeated <i>Event types</i> heading went (−4) (fix/records-filter-visibility).</li>
 * </ul>
 *
 * <p>Tightened by fix/wizard-scorecard-regressions (2026-10-18, docs/usability/baseline-regfix.md), the owner's finding
 * above answered:
 * <ul>
 *   <li>T6 7 → 4 acts, and a hesitation budget of 1 (was 4): Screen 2b carries <i>Check connection</i> itself, under
 *       the access switch and above the fold at 640×560 and 520×480; with access off its press raises the SAME OD-77.4
 *       confirmation, and that confirmation's <i>Turn on access</i> also runs the check. <i>Continue</i> is
 *       <i>Set up my client</i>. The one hesitation left is <i>Turn on access</i>, the owner's confirmation, unchanged.</li>
 *   <li>T1 and T2: a hesitation budget of 2 each (was 3): the last wizard press reads <i>Finish, then send my
 *       question</i> while a question waits (OD-77.2 already skips the Summary then). Their acts are unchanged (6 and 5):
 *       Screen 1's press opens the checks and 2a's applies, two acts with two decisions. Screen 1's press is now
 *       <i>Check Claude Code</i>, which honestly holds none of T1's words, so it stays a hesitation.</li>
 *   <li>S1 222 → 224: RAISED by two. The first-run block's button says what it does for the preselected choice
 *       (<i>Check Claude Code</i>, 3 words, where <i>Continue</i> was 1).</li>
 * </ul>
 */
final class UsabilityBudgets {

    private UsabilityBudgets() {
    }

    /** Acts per task (clicks + keys + drags + scrolls), at most. */
    static final Map<String, Integer> ACTS = Map.of(
            "T1", 6, "T2", 5, "T3", 1, "T4", 1, "T4c", 0, "T5", 5, "T6", 4, "T7", 1, "T8", 1,
            "A2", 10);

    /**
     * Hesitations per task, at most — for the tasks a fix has driven down, so restoring the old labels or the old route
     * is red (fix/wizard-scorecard-regressions). A task with no entry here has no hesitation budget yet.
     */
    static final Map<String, Integer> HESITATIONS = Map.of("T1", 2, "T2", 2, "T6", 1);

    /** Always-visible chrome words in each key state's region, at most. */
    static final Map<String, Integer> WORDS = Map.of("S1", 224, "S2", 6, "S3", 26);

    /** Clipped strings in each key state's region, at most: none. */
    static final Map<String, Integer> CLIPPED = Map.of("S1", 0, "S2", 0, "S3", 0);

    /** The specs' act targets (not enforced yet). */
    static final Map<String, String> ACT_TARGETS = Map.of(
            "T2", "≤ 6 (M77 T19, M77-2): met",
            "T6", "≤ 4, ≤ 1 hesitation (fix/wizard-scorecard-regressions): met",
            "T4", "≤ 1, 0 hesitations (records-filter fix, proposed)",
            "A2", "≤ 3 acts project→evidence, no typed path (M76, as A6)");

    /** The specs' word targets (not enforced yet). */
    static final Map<String, String> WORD_TARGETS = Map.of(
            "S2", "≤ 15 words, ≤ 4 controls (M78 D1, TQ1): met");

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
            Integer hesitations = HESITATIONS.get(r.id);
            if (hesitations != null && r.hesitations() > hesitations) {
                out.add(r.id + " " + r.name + ": " + r.hesitations() + " hesitations > budget " + hesitations);
            }
        }
        return out;
    }
}
