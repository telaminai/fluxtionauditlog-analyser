import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;
import telamin.fluxtion.audit.analyser.analyser.topology.PerNodeLevelChanges;

import java.util.Set;

/**
 * Eighth re-review probe: real annotations for the cases the brief asks the reviewer to hunt. Constructed logs only.
 * Record numbers in labels are 1-based, as the notes print them; view indexes are 0-based rows. A stream-end marker
 * is not a record.
 */
public class R8Review {
    static final String ABSENT = "<absent>";
    static String g(String grouping) { return ABSENT.equals(grouping) ? "" : "  groupingId: " + grouping + "\n"; }
    static String row(int t, String grp) {
        return "eventLogRecord:\n  logTime: " + t + "\n" + g(grp) + "  event: Tick\n  nodeLogs:\n    - priceListener: { seen: true}\n---\n";
    }
    static String ctl(int t, String grp, String level, String src, String gid) {
        return "eventLogRecord:\n  logTime: " + t + "\n" + g(grp) + "  event: EventLogControlEvent\n  eventToString: EventLogConfig{level="
                + level + ", logRecordProcessor=null, sourceId=" + src + ", groupId=" + gid + "}\n  nodeLogs:\n---\n";
    }
    static String unread(int t, String grp) {
        return "eventLogRecord:\n  logTime: " + t + "\n" + g(grp) + "  event: EventLogControlEvent\n  nodeLogs:\n---\n";
    }
    static final String MARKER = "eventLogRecord:\n  streamEnd: normal\n  streamEndRecords: 1\n---\n";

    /** A plugin-style store: the built-in store, except that record(row) is null for the rows named (O7-1). */
    static final class NullingStore implements LogStore {
        final HeapLogStore d; final Set<Integer> nulls;
        NullingStore(String log, Set<Integer> nulls) { this.d = new HeapLogStore(log); this.nulls = nulls; }
        public int size() { return d.size(); }
        public LogIndex index() { return d.index(); }
        public LogRecord record(int row) { return nulls.contains(row) ? null : d.record(row); }
        public String rawText(int row) { return d.rawText(row); }
        public Long minLogTime() { return d.minLogTime(); }
        public Long maxLogTime() { return d.maxLogTime(); }
        public java.util.List<Integer> runBoundaries() { return d.runBoundaries(); }
    }

    static void show(String label, String node, LogStore store, int... view) {
        String note = PerNodeLevelChanges.of(store).annotationFor(node, view);
        System.out.println("== " + label + "\n   " + note + "\n");
    }
    static void show(String label, String log, int... view) { show(label, "riskMonitor", new HeapLogStore(log), view); }

    public static void main(String[] a) {
        // --- Q1: records the bound, or the rest of the sentence, still takes in ---
        show("M spanning; closer INFO at rec 4 after the marker; view recs 2, 3, 5 (rec 5 is after the closer)",
                ctl(1, "null", "WARN", "riskMonitor", "null") + row(2, "null") + MARKER + row(3, "null")
                        + ctl(4, "null", "INFO", "riskMonitor", "null") + row(5, "null"), 1, 2, 4);
        show("N spanning; two markers in the window; view recs 2, 3, 4 (rec 4 is after the SECOND marker)",
                ctl(1, "null", "WARN", "riskMonitor", "null") + row(2, "null") + MARKER + row(3, "null") + MARKER
                        + row(4, "null"), 1, 2, 3);
        show("O spanning; grouping alpha; a beta record in view after the marker; view recs 2, 3, 4",
                ctl(1, "alpha", "WARN", "riskMonitor", "alpha") + row(2, "alpha") + MARKER + row(3, "alpha")
                        + row(4, "beta"), 1, 2, 3);
        show("P wholly after; two markers; view rec 3 only (after the SECOND marker)",
                ctl(1, "null", "WARN", "riskMonitor", "null") + MARKER + row(2, "null") + MARKER + row(3, "null"), 2);
        show("Q wholly after; two markers; view recs 2 and 3 (one each side of the second marker)",
                ctl(1, "null", "WARN", "riskMonitor", "null") + MARKER + row(2, "null") + MARKER + row(3, "null"), 1, 2);
        show("R closer before the first marker; records in view after the marker; view recs 2, 5",
                ctl(1, "null", "WARN", "riskMonitor", "null") + row(2, "null") + ctl(3, "null", "INFO", "riskMonitor", "null")
                        + MARKER + row(4, "null") + row(5, "null"), 1, 4);
        show("S several groupings in view; WARN in alpha; view alpha rec 2 and beta rec 3",
                ctl(1, "alpha", "WARN", "riskMonitor", "alpha") + row(2, "alpha") + row(3, "beta") + row(4, "alpha"), 1, 2);
        show("T untimed change (no logTime line); view rec 2",
                "eventLogRecord:\n  groupingId: null\n  event: EventLogControlEvent\n  eventToString: EventLogConfig{level=WARN, "
                        + "logRecordProcessor=null, sourceId=riskMonitor, groupId=null}\n  nodeLogs:\n---\n" + row(2, "null"), 1);
        show("U the change's own record is the last in view; later records not in view; view recs 1 (the change)",
                row(1, "null") + ctl(2, "null", "WARN", "riskMonitor", "null") + row(3, "null"), 0, 1);

        // --- Q8: R7-5's fix covers the APPLYING premise; the NAMING premise ("it named no node") is also open ---
        show("V declared null; WARN naming no node (or \"null\"); readable INFO riskMonitor closer at rec 3; view rec 2",
                ctl(1, "null", "WARN", "null", "null") + row(2, "null") + ctl(3, "null", "INFO", "riskMonitor", "null")
                        + row(4, "null"), 1);
        show("W declared null; WARN naming no node (or \"null\"); unreadable closer at rec 3; view rec 2",
                ctl(1, "null", "WARN", "null", "null") + row(2, "null") + unread(3, "null") + row(4, "null"), 1);
        show("X absent; WARN naming no node (or \"null\"); readable INFO riskMonitor closer at rec 3; view rec 2",
                ctl(1, ABSENT, "WARN", "null", "null") + row(2, ABSENT) + ctl(3, ABSENT, "INFO", "riskMonitor", "null")
                        + row(4, ABSENT), 1);

        // --- Q4: O7-1, a null record() from a plugin store ---
        String withControl = ctl(1, "null", "WARN", "riskMonitor", "null") + row(2, "null")
                + ctl(3, "null", "INFO", "riskMonitor", "null") + row(4, "null");
        show("Y1 null record() at rec 3, raw text names the control event (timed, logTime 3); view rec 2", "riskMonitor",
                new NullingStore(withControl, Set.of(2)), 1);
        String nestedOnly = ctl(1, "null", "WARN", "riskMonitor", "null") + row(2, "null")
                + "eventLogRecord:\n  logTime: 3\n  groupingId: null\n  nodeLogs:\n    - priceListener:\n        event: EventLogControlEvent\n---\n"
                + row(4, "null");
        show("Y2 null record() at rec 3, NO event: line; a node value line reads 'event: EventLogControlEvent'; view rec 2",
                "riskMonitor", new NullingStore(nestedOnly, Set.of(2)), 1);
        String tick = ctl(1, "null", "WARN", "riskMonitor", "null") + row(2, "null") + row(3, "null") + row(4, "null");
        show("Y3 null record() at rec 3, an ordinary Tick; view rec 2", "riskMonitor", new NullingStore(tick, Set.of(2)), 1);
    }
}
