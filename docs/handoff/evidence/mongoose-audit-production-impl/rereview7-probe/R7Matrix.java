import com.telamin.fluxtion.runtime.audit.EventLogControlEvent;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent.LogLevel;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.topology.PerNodeLevelChanges;
import java.util.*;

/** Replays ControlAddressAndScopeTest's matrix verbatim and records WHICH cells produce each phrase. */
public class R7Matrix {
    static final String ABSENT = "<absent>";
    static String gl(String g) { return ABSENT.equals(g) ? "" : "  groupingId: " + g + "\n"; }
    static String row(int t, String g) { return "eventLogRecord:\n  logTime: " + t + "\n" + gl(g) + "  event: Tick\n  nodeLogs:\n    - priceListener: { seen: true}\n---\n"; }
    static String control(int t, String g, EventLogControlEvent c) { return "eventLogRecord:\n  logTime: " + t + "\n" + gl(g) + "  event: EventLogControlEvent\n  eventToString: " + c + "\n  nodeLogs:\n---\n"; }
    static String unreadable(int t, String g) { return "eventLogRecord:\n  logTime: " + t + "\n" + gl(g) + "  event: EventLogControlEvent\n  eventToString: EventLogConfig{level=INFO, logRecordProcessor=Proc{a, sourceId=x}, sourceId=riskMonitor, groupId=null}\n  nodeLogs:\n---\n"; }
    static final String MARKER_1 = "eventLogRecord:\n  streamEnd: normal\n  streamEndRecords: 1\n---\n";
    public static void main(String[] a) {
        String[] phrases = {
            "If the change at record 1 (logTime 1) applied here, riskMonitor's",
            "applied here, riskMonitor's lines below WARN are not in this log; otherwise",
            "the log renders both identically. ",
            ", a control record this reader could not read; ", "could not be read by this reader; ",
            "The next change to riskMonitor's audit level in the same grouping is at ",
            "among the records that likewise state no grouping is at ",
            "either way it would change this node, and only if", "either way it changes this node",
            "is not established. Before record ", "which sets it to INFO. Before the stream-end marker", "only the first would change it"};
        Map<String, Set<String>> cells = new LinkedHashMap<>();
        for (String p : phrases) cells.put(p, new TreeSet<>());
        record G(String rg, String gid) { }
        List<G> groupings = List.of(new G("null", null), new G("null", "alpha"), new G(ABSENT, null), new G("alpha", "alpha"), new G(ABSENT, "alpha"));
        int logs = 0, bothBeforeMarker = 0;
        for (String node : new String[]{"riskMonitor", "null"}) for (boolean ns : new boolean[]{false, true}) {
            if ("null".equals(node) && !ns) continue;
            for (G g : groupings) for (String b : new String[]{"none", "spanning", "whollyAfter"})
            for (String cl : new String[]{"none", "perNode", "null", "unreadable"})
            for (String cg : cl.equals("none") || cl.equals("unreadable") ? new String[]{"-"} : new String[]{"same", "beta", "none"}) {
                String gid = switch (cg) { case "same" -> g.gid(); case "beta" -> "beta"; default -> null; };
                String rg = g.rg();
                var open = new EventLogControlEvent(ns ? null : node, g.gid(), LogLevel.WARN);
                String close = switch (cl) {
                    case "perNode" -> control(90, rg, new EventLogControlEvent(node, gid, LogLevel.INFO));
                    case "null" -> control(90, rg, new EventLogControlEvent(null, gid, LogLevel.INFO));
                    case "unreadable" -> unreadable(90, rg);
                    default -> ""; };
                String log; int[] view;
                switch (b) {
                    case "spanning" -> { log = control(1, rg, open) + row(2, rg) + MARKER_1 + row(3, rg) + close + row(95, rg); view = new int[]{1, 2}; }
                    case "whollyAfter" -> { log = control(1, rg, open) + MARKER_1 + row(2, rg) + close + row(95, rg); view = new int[]{1}; }
                    default -> { log = control(1, rg, open) + row(2, rg) + close + row(95, rg); view = new int[]{1}; }
                }
                logs++;
                String note = PerNodeLevelChanges.of(new HeapLogStore(log)).annotationFor(node, view);
                if (note == null) continue;
                String at = (ns ? "nullSrc" : "perNode") + "/" + node + "/" + rg + "|" + g.gid() + "/" + b + "/" + cl + "/" + cg;
                for (String p : phrases) if (note.contains(p)) cells.get(p).add(at);
            }
        }
        System.out.println("logs=" + logs);
        for (var e : cells.entrySet()) {
            Set<String> boundaries = new TreeSet<>(), closings = new TreeSet<>();
            for (String c : e.getValue()) { String[] p = c.split("/"); boundaries.add(p[3]); closings.add(p[4]); }
            System.out.println(e.getValue().size() + " cells  boundaries=" + boundaries + " closings=" + closings + "  «" + e.getKey() + "»");
        }
    }
}
