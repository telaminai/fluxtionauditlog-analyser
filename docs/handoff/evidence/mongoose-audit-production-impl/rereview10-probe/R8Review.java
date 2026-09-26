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

}
