package telamin.fluxtion.audit.analyser.analyser.topology;

import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * MA-8 — the audit levels a log states about itself, and which records each one governed.
 *
 * <p><b>The problem this exists for.</b> A node set to {@code WARN} still RUNS, but its {@code info}
 * lines are suppressed, so it carries no node entries and coverage lists it as uncovered with no
 * explanation. Measured: a file read {@code complete}, 6 of 6, no findings, with zero entries for a node
 * that ran three times — while the control record naming {@code sourceId=riskCheck, level=WARN} sat in
 * the log. That is "this node never ran" being wrong with the answer already in the file.
 *
 * <p><b>Annotate, never excuse (MA-8.2).</b> An annotated node stays in {@code uncovered} and in the
 * ratio. Excusing it would hide a node that genuinely never ran whenever the qualifying record is
 * wrong — and a control-LOOKING record can be content a payload produced.
 *
 * <p><b>Read regardless of the current filter (MA-8.3).</b> A level change is configuration state, not
 * an event you happen to be looking at, so every record is scanned for changes. The filter decides only
 * which records the annotation must be ABOUT.
 *
 * <h2>The runtime's rule, read rather than inferred</h2>
 *
 * <p>fluxtion-runtime 1.0.16 {@code EventLogManager.calculationLogConfig} does this with a level change:
 *
 * <pre>
 * if (level != null &amp;&amp; (logRecord.groupingId == null || logRecord.groupingId.equals(change.groupId))) {
 *     node2Logger.computeIfPresent(change.sourceId, setLevel);            // that node
 *     if (change.sourceId == null) every node's logger.setLevel(level);   // EVERY node
 * }
 * </pre>
 *
 * <p>So {@code groupId} is <b>not a group of nodes</b>. It gates the whole change against the PROCESSOR's
 * grouping, which every runtime record writes as its {@code groupingId:} field; and a change naming no
 * {@code sourceId} sets every node. The first version of this class read {@code groupId} as node
 * membership and annotated each node "this may or may not cover" — an inference that shipped, found while
 * answering the independent review's F4 by reading the runtime instead of this class's own comments.
 * Assumption, stated: a record that carries no {@code groupingId} field is read as ungrouped, which is the
 * runtime's default and the only reading available to a producer with no grouping concept.
 *
 * <h2>Order, not time</h2>
 *
 * <p>A change is dispatched through the same processor as every other event, so its RECORD's position is
 * where it took effect. Intervals are therefore {@code (change row, next applicable change row)} in record
 * order, and an annotation applies when a record in view lies inside one. The earlier version used
 * {@code logTime}, and the independent review found every consequence (F4, F5): a global restore never
 * closed a per-node interval; a record at exactly the restore's instant was still inside the WARN window;
 * an empty filter selection widened to all time and received every annotation; and an untimed control
 * after the records in view was assigned to the start of the log. None of those can happen by position.
 *
 * <h2>Within ONE processor's records — and what the log cannot establish</h2>
 *
 * <p>Record order is a clock only inside one processor's stream. The re-review (RR-3) showed a change from
 * a processor grouped {@code alpha} explaining a {@code beta} record, and a {@code beta} restore closing
 * {@code alpha}'s window. So every record is given the context its OWN {@code groupingId:} line declares,
 * and a change explains — and is closed by — only records of the same declared context. Two limits are
 * stated rather than hidden: records that SHARE a grouping (two ungrouped processors, say) are read as one
 * stream, which nothing in a record establishes; and a record with NO {@code groupingId:} line does not
 * declare "ungrouped", so whether a change applied there is qualified, not assumed.
 */
public final class PerNodeLevelChanges {

    /** The control event the runtime dispatches when an audit level is set. */
    static final String CONTROL_EVENT = "EventLogControlEvent";

    private final List<Change> changes;
    private final Grouping[] rowContext;
    private final List<Integer> runBoundaries;

    private PerNodeLevelChanges(List<Change> changes, Grouping[] rowContext, List<Integer> runBoundaries) {
        this.changes = changes;
        this.rowContext = rowContext;
        this.runBoundaries = runBoundaries;
    }

    /**
     * The processor grouping a record DECLARES about itself.
     *
     * @param declared false when the record has no {@code groupingId:} line at all — which is not a
     *                 statement that it is ungrouped (re-review RR-3)
     * @param value    the grouping, or null when declared as {@code null}
     */
    public record Grouping(boolean declared, String value) {
        static final Grouping ABSENT = new Grouping(false, null);
    }

    /** Whether a change applied to the processor that logged it. */
    public enum Applies { YES, NO, NOT_ESTABLISHED }

    /**
     * One level change a log states about itself.
     *
     * @param sourceId the node it names — EXACTLY as rendered, commas, braces and spaces included — or null
     *                 when it names none and so sets every node
     * @param groupId  the processor grouping it was addressed to, exactly as rendered, or null
     * @param level    the level set
     * @param row      the control record's row
     * @param logTime  the control record's time, or null when untimed
     * @param context  the grouping the control record itself declares — its processor's
     */
    public record Change(String sourceId, String groupId, String level, int row, Long logTime, Grouping context) {
        /** The runtime's gate: an ungrouped processor takes every change, a grouped one only its own. */
        public Applies applies() {
            if (!context.declared()) return Applies.NOT_ESTABLISHED;
            if (context.value() == null) return Applies.YES;
            return context.value().equals(groupId) ? Applies.YES : Applies.NO;
        }

        boolean affects(String nodeId) {
            // Exact: the runtime looks a sourceId up in a map, so " riskMonitor " and "riskMonitor, DEMO"
            // address no node called riskMonitor, and "" addresses a node called "" (re-review RR-2).
            return applies() != Applies.NO && (sourceId == null || sourceId.equals(nodeId));
        }
    }

    public static PerNodeLevelChanges of(LogStore store) {
        List<Change> out = new ArrayList<>();
        if (store == null) return new PerNodeLevelChanges(out, new Grouping[0], List.of());
        Grouping[] context = new Grouping[store.size()];
        for (int row = 0; row < store.size(); row++) {          // deliberately unfiltered — MA-8.3
            context[row] = groupingOf(store.rawText(row));
            var record = store.record(row);
            if (record == null) continue;
            // Exact match, not contains: `FakeEventLogControlEventX` is not a control event. The
            // fully-qualified name is accepted by comparing the simple name after the last dot.
            if (!isControlEvent(record.event())) continue;
            String text = record.eventToString();
            Rendering r = text == null ? null : parse(text);
            if (r == null) continue;
            out.add(new Change(r.sourceId(), r.groupId(), r.level(), row, record.logTime(), context[row]));
        }
        return new PerNodeLevelChanges(List.copyOf(out), context, store.runBoundaries());
    }

    /**
     * The grouping a record declares: its {@code groupingId:} line, read only BEFORE its {@code event:} line.
     *
     * <p>The runtime writes {@code groupingId} before {@code event} and everything a payload can reach —
     * {@code eventToString}, node values — comes after it, so a payload line cannot declare a grouping.
     * A producer that writes the fields in another order simply reads as undeclared, the safe direction.
     */
    static Grouping groupingOf(String rawText) {
        if (rawText == null) return Grouping.ABSENT;
        for (String line : rawText.split("\n", -1)) {
            String t = line.strip();
            if (t.startsWith("event:")) break;
            if (t.startsWith("groupingId:")) {
                String v = t.substring("groupingId:".length()).strip();
                return new Grouping(true, v.isEmpty() || v.equals("null") ? null : v);
            }
        }
        return Grouping.ABSENT;
    }

    /**
     * Is this the runtime's control event, by NAME rather than by resemblance?
     *
     * <p>{@code contains} accepted {@code FakeEventLogControlEventX}; an exact match alone missed a
     * fully-qualified name. So: the simple name, after the last dot, must equal it exactly.
     */
    static boolean isControlEvent(String event) {
        // One predicate, shared with ProducerDiagnostics.onlyControlEvents (phase 1 round 4, F4).
        return telamin.fluxtion.audit.analyser.analyser.parse.ProducerDiagnostics.isControlEvent(event);
    }

    /** The runtime's {@code EventLogConfig} rendering, field by field, values exactly as written. */
    record Rendering(String level, String logRecordProcessor, String sourceId, String groupId) {
    }

    private static final String OPEN = "EventLogConfig{level=";
    private static final String[] SEPARATORS = {", logRecordProcessor=", ", sourceId=", ", groupId="};
    private static final List<String> LEVELS = List.of("NONE", "ERROR", "WARN", "INFO", "DEBUG", "TRACE");

    /**
     * Parse the pinned rendering by its FIXED separators, or return null.
     *
     * <p>fluxtion-runtime 1.0.16 writes {@code EventLogConfig{level=L, logRecordProcessor=P, sourceId=S,
     * groupId=G}} and escapes nothing. So a value may itself contain a comma, a brace or spaces. The
     * previous parser stopped each value at the first comma or brace and trimmed it, which the re-review
     * measured (RR-2) against the real runtime: {@code riskMonitor, DEMO}, {@code riskMonitor}DEMO} and
     * {@code " riskMonitor "} left riskMonitor at INFO and were read as setting it to WARN; {@code
     * groupId="alpha, DEMO"} was ignored by the runtime and read as {@code alpha}; an EMPTY sourceId was
     * read as every node.
     *
     * <p>Now each separator must occur exactly once, in order, and every value is taken whole between
     * them: the text a value can hold is whatever does not complete a separator. A value that DOES contain
     * one — a listener whose {@code toString} writes {@code ", sourceId="} — makes the rendering ambiguous,
     * and it is refused. The level must be one of the runtime's six names.
     *
     * <p><b>The one thing no parser of this text can do:</b> the runtime renders a Java {@code null} and the
     * four-character string {@code "null"} identically. {@code null} is read as absent — the overwhelmingly
     * common meaning, and the only one the global case can have — and the sentence says so.
     */
    static Rendering parse(String text) {
        if (!text.startsWith(OPEN) || !text.endsWith("}")) return null;
        int[] at = new int[SEPARATORS.length];
        int from = OPEN.length();
        for (int i = 0; i < SEPARATORS.length; i++) {
            int first = text.indexOf(SEPARATORS[i]);
            if (first < 0 || text.indexOf(SEPARATORS[i], first + 1) >= 0 || first < from) return null;
            at[i] = first;
            from = first + SEPARATORS[i].length();
        }
        String level = text.substring(OPEN.length(), at[0]);
        if (!LEVELS.contains(level)) return null;
        String processor = text.substring(at[0] + SEPARATORS[0].length(), at[1]);
        String source = text.substring(at[1] + SEPARATORS[1].length(), at[2]);
        String group = text.substring(at[2] + SEPARATORS[2].length(), text.length() - 1);
        return new Rendering(level, nullLiteral(processor), nullLiteral(source), nullLiteral(group));
    }

    private static String nullLiteral(String v) {
        return v.equals("null") ? null : v;
    }

    /** Is this the pinned rendering? */
    static boolean recognised(String text) {
        return parse(text) != null;
    }

    /**
     * One field of the pinned rendering, exactly as written; null when it reads {@code null} or when the
     * text is not the pinned rendering at all. Kept for the tests that pin the format.
     */
    static String field(String text, String name) {
        Rendering r = parse(text);
        if (r == null) return null;
        return switch (name) {
            case "level" -> r.level();
            case "logRecordProcessor" -> r.logRecordProcessor();
            case "sourceId" -> r.sourceId();
            case "groupId" -> r.groupId();
            default -> null;
        };
    }

    /** True when the log states any level change that applied, or may have applied, to its processor. */
    public boolean any() {
        return changes.stream().anyMatch(c -> c.applies() != Applies.NO);
    }

    /**
     * The annotation for one uncovered node over the records in view, or {@code null} when no quiet level
     * the log states governed any of them.
     *
     * @param inView the rows in view, ascending. EMPTY means nothing is in view, and nothing is annotated:
     *               an empty selection is not an unbounded one (independent review, F5).
     */
    public String annotationFor(String nodeId, int[] inView) {
        if (inView == null || inView.length == 0) return null;
        for (Change c : changes) {
            if (!c.affects(nodeId) || !isQuiet(c.level())) continue;
            // The next change to this node IN THE SAME PROCESSOR CONTEXT ends the window (RR-3): another
            // processor's restore does not touch this processor's loggers.
            Change next = null;
            for (Change later : changes) {
                if (later.row() > c.row() && later.context().equals(c.context()) && later.affects(nodeId)) {
                    next = later;
                    break;
                }
            }
            int until = next == null ? Integer.MAX_VALUE : next.row();
            int first = -1, last = -1;
            for (int r : inView) {
                if (r <= c.row()) continue;
                if (r >= until) break;
                if (!c.context().equals(context(r))) continue;       // another processor's record
                if (first < 0) first = r;
                last = r;
            }
            if (first < 0) continue;                                  // governed none of the records in view
            return sentence(nodeId, c, next, first, boundaryBetween(c.row(), last));
        }
        return null;
    }

    private Grouping context(int row) {
        return row >= 0 && row < rowContext.length ? rowContext[row] : Grouping.ABSENT;
    }

    /** The FIRST run boundary in {@code (from, to]}: a record at or after it belongs to a later run. */
    private Integer boundaryBetween(int from, int to) {
        for (int b : runBoundaries) if (b > from && b <= to) return b;
        return null;
    }

    private static String sentence(String nodeId, Change c, Change next, int firstInView, Integer boundary) {
        String who = c.sourceId() == null
                ? "every node's audit level (the change names no node; the log renders that exactly as it would "
                + "a node literally named \"null\")"
                : nodeId + "'s audit level";
        String applied = switch (c.applies()) {
            case YES -> c.groupId() == null ? ""
                    : " (addressed to processor grouping '" + c.groupId() + "', "
                    + (c.context().value() == null ? "which applies because this processor declares no grouping"
                    : "which is this processor's") + ")";
            case NOT_ESTABLISHED -> ". The control record states no processor grouping, so whether this change"
                    + (c.groupId() == null ? "" : " — addressed to processor grouping '" + c.groupId() + "' —")
                    + " applied is not established; if it did";
            case NO -> throw new IllegalStateException("an inapplicable change never reaches a sentence");
        };
        String span = next == null
                ? "nothing later in this processor's records changes it"
                : "it holds until " + at(next) + " sets it to " + next.level()
                + (next.sourceId() == null ? " for every node" : "");
        String lines = nodeId + "'s lines below that level are not in this log";
        StringBuilder s = new StringBuilder("this log sets ").append(who).append(" to ").append(c.level())
                .append(" at ").append(at(c)).append(applied).append(", ").append(span);
        if (boundary == null) {
            s.append(", so ").append(lines);
        } else if (firstInView < boundary) {
            // RR-4: definite only within the run the change was made in; conditional after the marker.
            s.append(", so within that run ").append(lines).append(". A stream-end marker before record ")
                    .append(boundary + 1).append(" begins a later run, and the log does not say whether the level ")
                    .append("survived into it: for the records in view from record ").append(boundary + 1)
                    .append(" on, those lines are absent only if it did");
        } else {
            s.append(". Every record in view is in a LATER run — a stream-end marker comes before record ")
                    .append(boundary + 1).append(" — and the log does not say whether the level survived into it: ")
                    .append("if it did, ").append(lines).append("; if it did not, this change explains nothing here");
        }
        return s.append(". It is still counted as uncovered, because a level change is not proof the node ran")
                .toString();
    }

    /** "record 3 (logTime 1000)", or "(untimed)" — never a stand-in number. */
    private static String at(Change c) {
        return "record " + (c.row() + 1) + (c.logTime() == null ? " (untimed)" : " (logTime " + c.logTime() + ")");
    }

    /** Levels at or above WARN suppress the {@code info} lines coverage is looking for. */
    private static boolean isQuiet(String level) {
        if (level == null) return false;
        return switch (level.toUpperCase(Locale.ROOT)) {
            // The runtime's quiet levels. FATAL and OFF were listed here once; the runtime has neither.
            case "WARN", "ERROR", "NONE" -> true;
            default -> false;
        };
    }

    /**
     * Every change the log states, applied or not. NOT yet wired to the {@code context} echo or the
     * report — that is D-MA0c / MA-8's report path, still open.
     */
    public List<Change> all() {
        return changes;
    }
}
