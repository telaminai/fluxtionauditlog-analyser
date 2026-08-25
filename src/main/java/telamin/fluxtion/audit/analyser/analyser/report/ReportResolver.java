package telamin.fluxtion.audit.analyser.analyser.report;

import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.graph.Expr;
import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves a {@link ReportSpec}'s references against the live log (spec-investigation-reports
 * M33.1). Pure and headless: the inputs are the index, the findings map and the current names; the
 * output is DATA — what resolved, what did not and why, and the D-I3a announce/offer lines — so the
 * decisions are pinned by test while rendering (M33.2) and Swing stay downstream.
 *
 * <p>Order matters and is part of the contract (acceptance 9): the fingerprint verdict comes FIRST,
 * before any section — a report opened against a different log names the mismatch before rendering
 * anything, and does not refuse (announce, never forbid).
 */
public final class ReportResolver {

    private ReportResolver() {
    }

    /** The verbs a TABLE section may derive its rows from (D-I7). */
    private static final Set<String> TABLE_VERBS = Set.of("read", "series", "aggregate", "coverage");

    /**
     * One section's resolution.
     *
     * @param index    position in the report
     * @param kind     the section kind
     * @param resolved whether the reference resolves against this log
     * @param finding  FINDING sections only: the flag's content, BYTE-IDENTICAL (D-I1) — the resolver
     *                 hands over the {@link Finding} object itself, never a restatement
     * @param reason   when unresolved: why, naming the reference
     * @param warning  resolved-with-warning (e.g. a malformed rowWhen — the table still renders,
     *                 acceptance 7)
     */
    public record SectionResolution(int index, ReportSpec.Kind kind, boolean resolved,
                                    Finding finding, String reason, String warning) {
    }

    /**
     * The whole verdict, as data.
     *
     * @param fingerprintMismatch the D-I3a announce line, or null when the log matches — ALWAYS
     *                            rendered first when present
     * @param filterDifference    the D-I3a offer line, or null when the view matches; applying the
     *                            stored context is the CALLER's act, on the user's acceptance only
     * @param sections            per-section outcomes, report order
     * @param summary             the unresolved-anchor line ("3 of 5 anchors did not resolve against
     *                            this log"), or null when everything resolved
     */
    public record Resolution(String fingerprintMismatch, String filterDifference,
                             List<SectionResolution> sections, String summary) {

        public boolean clean() {
            return fingerprintMismatch == null && summary == null;
        }
    }

    public static Resolution resolve(ReportSpec spec, LogIndex idx, Map<Integer, Finding> findings,
                                     Set<String> graphNames, Set<String> focusNames,
                                     FilterState currentFilter) {
        return resolve(spec, idx, null, findings, graphNames, focusNames, currentFilter);
    }

    /**
     * The banner heading for a fingerprint announce line — ONE definition for the panel and the PDF,
     * because a soft message under the strong heading contradicts itself (found by eyeball, Q1's
     * first live render): a renamed copy must not be headlined "this is not the log".
     */
    public static String fingerprintHeading(String mismatchLine) {
        if (mismatchLine == null) return "THIS IS NOT THE LOG THE REPORT WAS WRITTEN AGAINST";
        if (mismatchLine.contains("a different system")) return "SAME CONTENT — A DIFFERENT SYSTEM";
        if (mismatchLine.contains("matches on content")) return "SAME CONTENT — A DIFFERENT FILE";
        return "THIS IS NOT THE LOG THE REPORT WAS WRITTEN AGAINST";
    }

    /**
     * @param loadedLogName the name of the log ACTUALLY open, which is the caller's to know. The
     *                      verdict still compares content identity only (count, range) — that
     *                      coarseness is deliberate (see {@link LogFingerprint#mismatch}) — but the
     *                      announce line must name the file on screen. Passing the stored name here
     *                      makes the one message whose job is "you are looking at a different log"
     *                      print the name of the log you are NOT looking at.
     */
    public static Resolution resolve(ReportSpec spec, LogIndex idx, String loadedLogName,
                                     Map<Integer, Finding> findings,
                                     Set<String> graphNames, Set<String> focusNames,
                                     FilterState currentFilter) {
        return resolve(spec, idx, loadedLogName, null, findings, graphNames, focusNames, currentFilter);
    }

    /**
     * @param loadedProvenance where the OPEN log came from (§E), or null when nobody declared it.
     *                         Needed here because two servers running the same build produce
     *                         identical content under identical file names — the only thing that can
     *                         separate them is what someone declared.
     */
    public static Resolution resolve(ReportSpec spec, LogIndex idx, String loadedLogName,
                                     String loadedProvenance, Map<Integer, Finding> findings,
                                     Set<String> graphNames, Set<String> focusNames,
                                     FilterState currentFilter) {
        String fp = spec.fingerprint() == null ? null
                : spec.fingerprint().mismatch(idx == null ? null
                        : LogFingerprint.of(idx, loadedLogName, loadedProvenance)).orElse(null);
        String filterLine = currentFilter == null ? null
                : spec.filter().difference(currentFilter).orElse(null);

        List<SectionResolution> out = new ArrayList<>();
        int anchors = 0, unresolved = 0;
        for (int i = 0; i < spec.sections().size(); i++) {
            ReportSpec.SectionSpec s = spec.sections().get(i);
            SectionResolution r = resolveSection(i, s, idx, findings, graphNames, focusNames);
            out.add(r);
            if (s.kind() != ReportSpec.Kind.NARRATIVE) {
                anchors++;
                if (!r.resolved()) unresolved++;
            }
        }
        String summary = unresolved == 0 ? null
                : unresolved + " of " + anchors + " anchor(s) did not resolve against this log";
        return new Resolution(fp, filterLine, List.copyOf(out), summary);
    }

    private static SectionResolution resolveSection(int i, ReportSpec.SectionSpec s, LogIndex idx,
                                                    Map<Integer, Finding> findings,
                                                    Set<String> graphNames, Set<String> focusNames) {
        return switch (s.kind()) {
            case NARRATIVE ->                    // prose resolves by definition — and renders AS prose
                    new SectionResolution(i, s.kind(), true, null, null, null);
            case FINDING -> {
                if (!inRange(idx, s.recordIndex())) {
                    yield unresolved(i, s, "record " + s.recordIndex() + " is out of range");
                }
                Finding f = findings == null ? null : findings.get(s.recordIndex());
                if (f == null || f.isEmpty()) {
                    // an unflagged record cannot carry a finding section: the section renders what
                    // flag wrote (D-I1), and nothing was written
                    yield unresolved(i, s, "record " + s.recordIndex()
                            + " has no flag — a finding section renders what 'flag' wrote");
                }
                yield new SectionResolution(i, s.kind(), true, f, null, null);
            }
            case RECORD -> inRange(idx, s.recordIndex())
                    ? new SectionResolution(i, s.kind(), true, null, null, null)
                    : unresolved(i, s, "record " + s.recordIndex() + " is out of range");
            case CHART -> graphNames != null && graphNames.contains(s.ref())
                    ? new SectionResolution(i, s.kind(), true, null, null, null)
                    : unresolved(i, s, "graph '" + s.ref() + "' is not defined");
            case TOPOLOGY -> focusNames != null && focusNames.contains(s.ref())
                    ? new SectionResolution(i, s.kind(), true, null, null, null)
                    : unresolved(i, s, "focus '" + s.ref() + "' is not defined");
            case SERIES -> {
                String expr = s.call().get("expr");
                String key = s.call().get("key");
                if (expr == null && key == null) {
                    yield unresolved(i, s, "a series section's call names neither 'key' nor 'expr'");
                }
                if (expr != null) {
                    try {
                        Expr.parse(expr);
                    } catch (RuntimeException e) {
                        yield unresolved(i, s, "series expr '" + expr + "' does not parse: "
                                + e.getMessage());
                    }
                }
                yield new SectionResolution(i, s.kind(), true, null, null, null);
            }
            case TABLE -> {
                String verb = s.call().get("verb");
                if (verb == null || !TABLE_VERBS.contains(verb)) {
                    yield unresolved(i, s, "a table's rows are DERIVED (D-I7): its call must name one "
                            + "of " + TABLE_VERBS + ", got '" + verb + "'");
                }
                // a malformed highlight rule is a WARNING, not a failure: the table still renders,
                // un-highlighted, and the echo names the rule (acceptance 7)
                String warning = null;
                if (s.rowWhen() != null) {
                    try {
                        warning = rowWhenProblem(Expr.parse(s.rowWhen()), s.rowWhen());
                    } catch (RuntimeException e) {
                        warning = "rowWhen '" + s.rowWhen() + "' does not parse (" + e.getMessage()
                                + ") — the table renders without highlighting";
                    }
                }
                yield new SectionResolution(i, s.kind(), true, null, null, warning);
            }
        };
    }

    /**
     * D-I8's rule made enforceable: a row rule is evaluated against its OWN record and nothing else,
     * so a rolling window would see exactly one sample. Left alone that is worse than a parse error —
     * {@code mean(x,"5m") > 100} silently degrades to {@code x > 100} and highlights, while the label
     * printed under the table still says the rule was a five-minute mean. The report would then state
     * a rule the analyser never applied, which is the one thing a forensic artefact may not do. So the
     * rule is REFUSED and named, and the table renders un-highlighted (acceptance 7's shape).
     *
     * @return the warning, or null when the rule is point-wise and can honestly be applied per row
     */
    static String rowWhenProblem(Expr rule, String source) {
        Set<String> windows = rule.windowFunctions();
        if (windows.isEmpty()) return null;
        return "rowWhen '" + source + "' uses " + windows + ", which need history — a row rule is "
                + "evaluated against its own record alone, so the window would hold one sample and "
                + "report a value it never computed; the table renders without highlighting. Compute "
                + "the window with the 'series' verb and highlight on a plain comparison.";
    }

    private static boolean inRange(LogIndex idx, int recordIndex) {
        return idx != null && recordIndex >= 0 && recordIndex < idx.size();
    }

    private static SectionResolution unresolved(int i, ReportSpec.SectionSpec s, String reason) {
        return new SectionResolution(i, s.kind(), false, null, reason, null);
    }
}
