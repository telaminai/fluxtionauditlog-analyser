import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.topology.PerNodeLevelChanges;

/** Seventh re-review probe: real annotations for the cases the review asks about. Constructed logs only. */
public class R7 {
    static final String ABSENT = "<absent>";
    static String g(String grouping) { return ABSENT.equals(grouping) ? "" : "  groupingId: " + grouping + "\n"; }
    static String row(int t, String grp) {
        return "eventLogRecord:\n  logTime: " + t + "\n" + g(grp) + "  event: Tick\n  nodeLogs:\n    - priceListener: { seen: true}\n---\n";
    }
    /** a record in which riskMonitor itself logs — a line below WARN, written before any quiet change */
    static String riskLine(int t, String grp) {
        return "eventLogRecord:\n  logTime: " + t + "\n" + g(grp) + "  event: Tick\n  nodeLogs:\n    - riskMonitor: { checked: true}\n---\n";
    }
    static String ctl(int t, String grp, String level, String src, String gid) {
        return "eventLogRecord:\n  logTime: " + t + "\n" + g(grp) + "  event: EventLogControlEvent\n  eventToString: EventLogConfig{level="
                + level + ", logRecordProcessor=null, sourceId=" + src + ", groupId=" + gid + "}\n  nodeLogs:\n---\n";
    }
    static String unread(int t, String grp, String event) {
        return "eventLogRecord:\n  logTime: " + t + "\n" + g(grp) + "  event: " + event + "\n  nodeLogs:\n---\n";
    }
    static final String MARKER = "eventLogRecord:\n  streamEnd: normal\n  streamEndRecords: 1\n---\n";
    static void show(String label, String log, int... view) {
        String note = PerNodeLevelChanges.of(new HeapLogStore(log)).annotationFor("riskMonitor", view);
        System.out.println("== " + label + "\n   " + note + "\n");
    }
    public static void main(String[] a) {
        // A: premise (absent grouping) + unreadable closer: is the conclusion bounded like the premise-free one?
        show("A absent grouping, unreadable closer, view rec 2",
                ctl(1, ABSENT, "WARN", "riskMonitor", "null") + row(2, ABSENT) + unread(3, ABSENT, "EventLogControlEvent") + row(4, ABSENT), 1);
        // B: premise + readable closer across a marker, every record in view BEFORE the marker
        show("B absent grouping, closer across a marker, view rec 2 (before the marker)",
                ctl(1, ABSENT, "WARN", "riskMonitor", "null") + row(2, ABSENT) + MARKER + row(3, ABSENT)
                        + ctl(4, ABSENT, "INFO", "riskMonitor", "null") + row(5, ABSENT), 1);
        // C: no closer, a stream-end marker AFTER the records in view — the window does not see it
        show("C declared null, no closer, a later run after the view, view rec 2",
                ctl(1, "null", "WARN", "riskMonitor", "null") + row(2, "null") + MARKER + row(3, "null"), 1);
        // D: "Before record N" — does it reach records BEFORE the change? riskMonitor logs at record 1, WARN at 2
        show("D riskMonitor logs at rec 1; WARN at rec 2; unreadable at rec 4; view rec 3",
                riskLine(1, "null") + ctl(2, "null", "WARN", "riskMonitor", "null") + row(3, "null")
                        + unread(4, "null", "EventLogControlEvent") + row(5, "null"), 2);
        // E: the same, with a marker bound: "Before the stream-end marker preceding record K"
        show("E riskMonitor logs at rec 1; WARN at rec 2; marker; INFO at rec 5; view rec 3",
                riskLine(1, "null") + ctl(2, "null", "WARN", "riskMonitor", "null") + row(3, "null") + MARKER + row(4, "null")
                        + ctl(5, "null", "INFO", "riskMonitor", "null") + row(6, "null"), 2);
        // F: "Before record N" and ANOTHER grouping's records between the change and N
        show("F declared alpha WARN at rec 1; beta record with riskMonitor at rec 2; unreadable alpha at rec 4; view rec 3",
                ctl(1, "alpha", "WARN", "riskMonitor", "alpha") + riskLine(2, "beta") + row(3, "alpha")
                        + unread(4, "alpha", "EventLogControlEvent") + row(5, "alpha"), 2);
        // G: fully-qualified control event, unreadable
        show("G fully-qualified unreadable control event at rec 3, view rec 2",
                ctl(1, "null", "WARN", "riskMonitor", "null") + row(2, "null")
                        + unread(3, "null", "com.telamin.fluxtion.runtime.audit.EventLogControlEvent") + row(4, "null"), 1);
        // H: unreadable in an absent-grouping context closing an absent change (premise path)
        show("H absent grouping, unreadable closer, view rec 2 (same as A: the premise path)",
                ctl(1, ABSENT, "WARN", "riskMonitor", "alpha") + row(2, ABSENT) + unread(3, ABSENT, "EventLogControlEvent") + row(4, ABSENT), 1);
        // I: the plain readable closer (not crossing, not unreadable): still ", so … not in this log", unbounded
        show("I declared null, readable closer at rec 3, riskMonitor logs at rec 4, view rec 2",
                ctl(1, "null", "WARN", "riskMonitor", "null") + row(2, "null") + ctl(3, "null", "INFO", "riskMonitor", "null")
                        + riskLine(4, "null"), 1);
    }
}
