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
 */
public final class PerNodeLevelChanges {

    /** The control event the runtime dispatches when an audit level is set. */
    static final String CONTROL_EVENT = "EventLogControlEvent";

    private final List<Change> changes;
    private final List<Integer> runBoundaries;

    private PerNodeLevelChanges(List<Change> changes, List<Integer> runBoundaries) {
        this.changes = changes;
        this.runBoundaries = runBoundaries;
    }

    /**
     * One level change a log states about itself, and whether it applied to this processor.
     *
     * @param sourceId          the node it names, or null for EVERY node
     * @param groupId           the processor grouping it was addressed to, or null
     * @param level             the level set
     * @param row               the control record's row
     * @param logTime           the control record's time, or null when untimed
     * @param processorGrouping the {@code groupingId} the control record itself carries — the processor's
     */
    public record Change(String sourceId, String groupId, String level, int row, Long logTime,
                         String processorGrouping) {
        /** The runtime's gate, exactly: an ungrouped processor takes every change, a grouped one only its own. */
        public boolean applies() {
            return processorGrouping == null || processorGrouping.equals(groupId);
        }

        boolean affects(String nodeId) {
            return applies() && (sourceId == null || sourceId.equals(nodeId));
        }
    }

    public static PerNodeLevelChanges of(LogStore store) {
        List<Change> out = new ArrayList<>();
        if (store == null) return new PerNodeLevelChanges(out, List.of());
        for (int row = 0; row < store.size(); row++) {          // deliberately unfiltered — MA-8.3
            var record = store.record(row);
            if (record == null) continue;
            // Exact match, not contains: `FakeEventLogControlEventX` is not a control event. The
            // fully-qualified name is accepted by comparing the simple name after the last dot.
            if (!isControlEvent(record.event())) continue;
            String text = record.eventToString();
            if (text == null || !recognised(text)) continue;
            String level = field(text, "level");
            if (level == null) continue;
            out.add(new Change(field(text, "sourceId"), field(text, "groupId"), level, row, record.logTime(),
                    record.groupingId()));
        }
        return new PerNodeLevelChanges(List.copyOf(out), store.runBoundaries());
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

    /**
     * Read {@code name=value} out of the runtime's {@code EventLogConfig{…}} rendering — a WHOLE field.
     *
     * <p>The key must open the body or follow {@code ", "}, the separators the runtime writes. The first
     * version searched for the substring {@code name=}, so {@code not_sourceId=riskMonitor} annotated
     * riskMonitor (independent review, O3). A key written twice is ambiguous and reads as absent, which
     * loses the annotation — the stated behaviour for a rendering this class does not recognise.
     *
     * <p>{@code null} and the literal {@code "null"} both read as absent: that is how the runtime renders
     * an unset field.
     */
    static String field(String text, String name) {
        int body = text.indexOf('{');
        if (body < 0) return null;
        String at = null;
        for (String opener : new String[]{"{" + name + "=", ", " + name + "="}) {
            int i = text.indexOf(opener, body);
            while (i >= 0) {
                if (at != null) return null;                          // twice: ambiguous
                int from = i + opener.length();
                int end = from;
                while (end < text.length() && text.charAt(end) != ',' && text.charAt(end) != '}') end++;
                at = text.substring(from, end).trim();
                i = text.indexOf(opener, i + 1);
            }
        }
        return at == null || at.isEmpty() || at.equals("null") ? null : at;
    }

    /** The four fields the runtime always writes, in the rendering this class is pinned to. */
    private static final String[] RENDERED_FIELDS = {"level", "logRecordProcessor", "sourceId", "groupId"};

    /**
     * Is this the pinned rendering — every field the runtime writes present exactly once, as a whole field?
     *
     * <p>Required BEFORE any field is read, because absence means something here: a missing
     * {@code sourceId} would otherwise read as {@code null}, and {@code null} means EVERY node. Measured
     * while fixing O3: {@code not_sourceId=riskMonitor} stopped annotating riskMonitor and started
     * annotating all five nodes, a rendering this class did not recognise being read as a global change.
     * The runtime writes all four fields every time, {@code null} included, so a record without one of
     * them is not a rendering this class knows, and it is skipped.
     */
    static boolean recognised(String text) {
        for (String name : RENDERED_FIELDS) if (occurrences(text, name) != 1) return false;
        return true;
    }

    private static int occurrences(String text, String name) {
        int body = text.indexOf('{');
        if (body < 0) return 0;
        int n = 0;
        for (String opener : new String[]{"{" + name + "=", ", " + name + "="}) {
            for (int i = text.indexOf(opener, body); i >= 0; i = text.indexOf(opener, i + 1)) n++;
        }
        return n;
    }

    /** True when the log states any level change that applied to this processor. */
    public boolean any() {
        return changes.stream().anyMatch(Change::applies);
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
        List<Change> mine = changes.stream().filter(c -> c.affects(nodeId)).toList();
        for (int i = 0; i < mine.size(); i++) {
            Change c = mine.get(i);
            if (!isQuiet(c.level())) continue;
            Change next = i + 1 < mine.size() ? mine.get(i + 1) : null;
            int until = next == null ? Integer.MAX_VALUE : next.row();
            int lastInside = lastInViewBetween(inView, c.row(), until);
            if (lastInside < 0) continue;                           // governed none of the records in view
            return sentence(nodeId, c, next, boundaryBetween(c.row(), lastInside));
        }
        return null;
    }

    /** The last row in view strictly after {@code from} and strictly before {@code until}, or -1. */
    private static int lastInViewBetween(int[] inView, int from, int until) {
        int found = -1;
        for (int r : inView) {
            if (r <= from) continue;
            if (r >= until) break;
            found = r;
        }
        return found;
    }

    /** A run boundary in {@code (from, to]}: a record at or after it belongs to a later run. */
    private Integer boundaryBetween(int from, int to) {
        for (int b : runBoundaries) if (b > from && b <= to) return b;
        return null;
    }

    private static String sentence(String nodeId, Change c, Change next, Integer boundary) {
        String who = c.sourceId() == null ? "every node's audit level" : nodeId + "'s audit level";
        String addressed = c.groupId() == null ? ""
                : " (addressed to processor grouping '" + c.groupId() + "', "
                + (c.processorGrouping() == null ? "which applies because this processor states no grouping"
                : "which is this processor's") + ")";
        String span = next == null
                ? "and nothing later in this log changes it"
                : "until " + at(next) + " sets it to " + next.level()
                + (next.sourceId() == null ? " for every node" : "");
        StringBuilder s = new StringBuilder("this log sets ").append(who).append(" to ").append(c.level())
                .append(" at ").append(at(c)).append(addressed).append(", ").append(span)
                .append(", so ").append(nodeId).append("'s lines below that level are not in this log — it is ")
                .append("still counted as uncovered, because a level change is not proof the node ran");
        if (boundary != null) {
            s.append(". A stream-end marker before record ").append(boundary + 1)
                    .append(" ends one run and begins another inside that interval, and the log does not say ")
                    .append("whether the level survived into the later run");
        }
        return s.toString();
    }

    /** "record 3 (logTime 1000)", or "(untimed)" — never a stand-in number. */
    private static String at(Change c) {
        return "record " + (c.row() + 1) + (c.logTime() == null ? " (untimed)" : " (logTime " + c.logTime() + ")");
    }

    /** Levels at or above WARN suppress the {@code info} lines coverage is looking for. */
    private static boolean isQuiet(String level) {
        if (level == null) return false;
        return switch (level.toUpperCase(Locale.ROOT)) {
            case "WARN", "ERROR", "FATAL", "NONE", "OFF" -> true;
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
