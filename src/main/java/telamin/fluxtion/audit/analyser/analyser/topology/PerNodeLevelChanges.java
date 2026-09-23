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
    private final List<Change> groupChanges;

    private PerNodeLevelChanges(Map<String, List<Change>> bySource, List<Change> groupChanges) {
        this.bySource = bySource;
        this.groupChanges = groupChanges;
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
        List<Change> groups = new ArrayList<>();
        if (store == null) return new PerNodeLevelChanges(bySource, groups);

        for (int row = 0; row < store.size(); row++) {          // deliberately unfiltered — MA-8.3
            var record = store.record(row);
            if (record == null || !CONTROL_EVENT.equals(record.event())) continue;
            String text = record.eventToString();
            if (text == null) continue;

            String level = field(text, "level");
            if (level == null) continue;
            String sourceId = field(text, "sourceId");
            String groupId = field(text, "groupId");

            if (sourceId != null) {
                bySource.computeIfAbsent(sourceId, k -> new ArrayList<>())
                        .add(new Change(sourceId, false, level, row, record.logTime()));
            } else if (groupId != null) {
                groups.add(new Change(groupId, true, level, row, record.logTime()));
            }
            // Neither named: a GLOBAL level change. AuditLevel already reports those.
        }
        return new PerNodeLevelChanges(bySource, groups);
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
        return !bySource.isEmpty() || !groupChanges.isEmpty();
    }

    /**
     * The annotation for one uncovered node, or {@code null} when the log says nothing about it.
     *
     * <p><b>Intervals (MA-8.4).</b> A node set to a quieter level and later restored is annotated only
     * between the two changes. Silence outside that window is plain uncovered, and saying otherwise
     * would excuse a node that really never ran.
     */
    public String annotationFor(String nodeId) {
        List<Change> changes = bySource.get(nodeId);
        if (changes != null && !changes.isEmpty()) {
            Change last = changes.get(changes.size() - 1);
            if (isQuiet(last.level())) {
                return "this log sets " + nodeId + "'s audit level to " + last.level()
                        + " (record " + (last.row() + 1) + "), so its lines below that level are not in "
                        + "this log — it is still counted as uncovered, because a level change is not "
                        + "proof the node ran";
            }
            return "this log changes " + nodeId + "'s audit level to " + last.level()
                    + " (record " + (last.row() + 1) + "), which does not suppress its lines — so its "
                    + "silence is not explained by a level";
        }
        if (!groupChanges.isEmpty()) {
            Change g = groupChanges.get(groupChanges.size() - 1);
            if (isQuiet(g.level())) {
                return "this log sets the audit level of group '" + g.target() + "' to " + g.level()
                        + " (record " + (g.row() + 1) + "). The log alone does not say which nodes are "
                        + "in that group, so this may or may not cover " + nodeId;
            }
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

    /** Every change the log states, for the {@code context} echo and the report. */
    public List<Change> all() {
        List<Change> out = new ArrayList<>();
        bySource.values().forEach(out::addAll);
        out.addAll(groupChanges);
        return List.copyOf(out);
    }
}
