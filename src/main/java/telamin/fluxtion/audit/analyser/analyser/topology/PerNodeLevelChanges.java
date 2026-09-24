package telamin.fluxtion.audit.analyser.analyser.topology;

import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * MA-8 — the per-node audit levels a log states about itself.
 *
 * <p><b>The problem this exists for.</b> A node set to {@code WARN} still RUNS, but its {@code info}
 * lines are suppressed, so it carries no node entries and coverage lists it as uncovered with no
 * explanation. Measured: a file read {@code complete}, 6 of 6, no findings, with zero entries for a node
 * that ran three times — while the control record naming {@code sourceId=riskCheck, level=WARN} sat in
 * the log. That is "this node never ran" being wrong with the answer already in the file.
 *
 * <p><b>Annotate, never excuse (MA-8.2).</b> An annotated node stays in {@code uncovered} and in the
 * ratio. Excusing it would hide a node that genuinely never ran whenever the qualifying record is
 * wrong — and until every writer escapes (MA-7), a control-LOOKING record can be content a payload
 * produced.
 *
 * <p><b>Read regardless of the current filter (MA-8.3).</b> A level change is configuration state, not
 * an event you happen to be looking at. A time or type filter that excludes the control record must not
 * drop the annotation, so this always scans the whole store.
 *
 * <p><b>Keyed on the event TYPE, not on a toString format (MA-8.5).</b> The runtime's rendering of
 * {@code EventLogConfig} is not a contract. The record's {@code event} being
 * {@code EventLogControlEvent} is what selects it; {@code sourceId}, {@code groupId} and {@code level}
 * are then read out of the text, and a fixture pins the format to a runtime version so a change is
 * caught rather than silently dropping every annotation.
 *
 * <p><b>{@code groupId} is in scope</b>, treated exactly as {@code sourceId}: it targets nodes and
 * produces the same silence. Where the log alone does not let a group be mapped to its nodes, the
 * annotation is made at the group level and says so.
 */
public final class PerNodeLevelChanges {

    /** The control event the runtime dispatches when an audit level is set. */
    static final String CONTROL_EVENT = "EventLogControlEvent";

    private final Map<String, List<Change>> bySource;
    /** Keyed BY GROUP: a window must be closed by the next change to the SAME group, not to any. */
    private final Map<String, List<Change>> byGroup;

    private PerNodeLevelChanges(Map<String, List<Change>> bySource, Map<String, List<Change>> byGroup) {
        this.bySource = bySource;
        this.byGroup = byGroup;
    }

    /**
     * One level change a log states about itself.
     *
     * @param target  the {@code sourceId} or {@code groupId} named
     * @param group   true when {@code target} is a group rather than a node
     * @param level   the level set
     * @param row     the record this was read from, so a person can go and look
     * @param logTime when, for the interval in {@link #annotationFor}
     */
    public record Change(String target, boolean group, String level, int row, long logTime) {
    }

    public static PerNodeLevelChanges of(LogStore store) {
        Map<String, List<Change>> bySource = new LinkedHashMap<>();
        Map<String, List<Change>> groups = new LinkedHashMap<>();
        if (store == null) return new PerNodeLevelChanges(bySource, groups);

        for (int row = 0; row < store.size(); row++) {          // deliberately unfiltered — MA-8.3
            var record = store.record(row);
            // Match the fully-qualified name too. `onlyControlEvents` uses contains(), and a log that
            // records the qualified class name would otherwise be missed here while being seen there.
            if (record == null) continue;
            // Exact match, not contains: `FakeEventLogControlEventX` is not a control event. The
            // fully-qualified name is accepted by comparing the simple name after the last dot.
            if (!isControlEvent(record.event())) continue;
            String text = record.eventToString();
            if (text == null) continue;

            String level = field(text, "level");
            if (level == null) continue;
            String sourceId = field(text, "sourceId");
            String groupId = field(text, "groupId");

            // An untimed control record is legal; treat it as having happened at the start of the
            // log rather than dereferencing null, so its window still begins where the record does.
            Long at = record.logTime();
            long when = at == null ? Long.MIN_VALUE : at;
            if (sourceId != null) {
                bySource.computeIfAbsent(sourceId, k -> new ArrayList<>())
                        .add(new Change(sourceId, false, level, row, when));
            } else if (groupId != null) {
                groups.computeIfAbsent(groupId, k -> new ArrayList<>())
                        .add(new Change(groupId, true, level, row, when));
            }
            // Neither named: a GLOBAL level change. AuditLevel already reports those.
        }
        return new PerNodeLevelChanges(bySource, groups);
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
     * Read {@code name=value} out of the control event's text.
     *
     * <p>Tolerant by design: the runtime's rendering is not a contract, so a format change must lose
     * the annotation rather than break the load. {@code null} and the literal {@code "null"} both read
     * as absent, because that is how the runtime renders an unset field.
     */
    static String field(String text, String name) {
        int at = text.indexOf(name + "=");
        if (at < 0) return null;
        int from = at + name.length() + 1;
        int end = from;
        while (end < text.length() && text.charAt(end) != ',' && text.charAt(end) != '}') end++;
        String value = text.substring(from, end).trim();
        return value.isEmpty() || value.equals("null") ? null : value;
    }

    /** True when the log states any per-node or per-group level change. */
    public boolean any() {
        return !bySource.isEmpty() || !byGroup.isEmpty();
    }

    /**
     * The annotation for one uncovered node, or {@code null} when the log says nothing about it.
     *
     * <p><b>Intervals (MA-8.4).</b> A node set to a quieter level and later restored is annotated only
     * between the two changes. Silence outside that window is plain uncovered, and saying otherwise
     * would excuse a node that really never ran.
     */
    public String annotationFor(String nodeId, long scopeStart, long scopeEnd) {
        String own = intervalAnnotation(bySource.get(nodeId), nodeId, scopeStart, scopeEnd, false);
        if (own != null) return own;
        // LOW: group windows are closed by the NEXT change to the SAME group, not by any group's.
        for (Map.Entry<String, List<Change>> e : byGroup.entrySet()) {
            String grouped = intervalAnnotation(e.getValue(), nodeId, scopeStart, scopeEnd, true);
            if (grouped != null) return grouped;
        }
        return null;
    }

    /**
     * MA-8.4 — a level applies over an INTERVAL, {@code [change, next change)}, clipped to the scope.
     *
     * <p>Using only the last change for the whole log gets both directions wrong, and review found
     * both: a node set to WARN at record 1 and restored to INFO at record 7 was annotated NOWHERE,
     * because the last change is the restore; and a node set to WARN late, viewed through a filter
     * ending before it, had its earlier silence "explained" by a change that had not happened yet.
     */
    private String intervalAnnotation(List<Change> changes, String nodeId,
                                      long scopeStart, long scopeEnd, boolean group) {
        if (changes == null || changes.isEmpty()) return null;
        for (int i = 0; i < changes.size(); i++) {
            Change c = changes.get(i);
            if (!isQuiet(c.level())) continue;
            long from = c.logTime();
            if (from > scopeEnd) continue;                       // it had not happened yet
            Long to = i + 1 < changes.size() ? changes.get(i + 1).logTime() : null;
            // Clip at BOTH ends. Only clipping the end left a window that CLOSED before the scope
            // began still explaining silence inside it — a filter entirely after the restore was
            // annotated "WARN between 1001 and 1007", which is a window it never overlapped.
            //
            // An UNTIMED closing change is not "closed before the scope": Long.MIN_VALUE is a stand-in
            // for "no time given", not an early one. Treating it as a real instant dropped the window
            // entirely, so a genuinely quietened node lost its explanation.
            boolean untimedClose = to != null && to == Long.MIN_VALUE;
            if (to != null && !untimedClose && to < scopeStart) continue;
            String window = untimedClose
                    ? (from == Long.MIN_VALUE
                    ? "at some point in this log (both changes are untimed, so the window is unknown)"
                    : "from " + from + " until an untimed change")
                    : from == Long.MIN_VALUE
                    ? (to == null ? "from an untimed change to the end of this log"
                    : "from an untimed change until " + to)
                    : to == null
                    ? "from " + from + " to the end of this log"
                    : "between " + from + " and " + to;
            if (group) {
                return "this log sets the audit level of group '" + c.target() + "' to " + c.level()
                        + " " + window + ". The log alone does not say which nodes are in that group, "
                        + "so this may or may not cover " + nodeId;
            }
            return "this log sets " + nodeId + "'s audit level to " + c.level() + " " + window
                    + ", so its lines below that level are not in this log — it is still counted as "
                    + "uncovered, because a level change is not proof the node ran";
        }
        return null;
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
     * Every change the log states. NOT yet wired to the {@code context} echo or the report — that is
     * D-MA0c / MA-8's report path, still open. Stated so the comment does not promise what the code does
     * not do (phase 1 round 4, F4).
     */
    public List<Change> all() {
        List<Change> out = new ArrayList<>();
        bySource.values().forEach(out::addAll);
        byGroup.values().forEach(out::addAll);
        return List.copyOf(out);
    }
}
