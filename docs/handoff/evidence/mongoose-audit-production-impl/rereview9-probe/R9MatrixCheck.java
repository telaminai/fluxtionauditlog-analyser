import com.telamin.fluxtion.runtime.audit.EventLogControlEvent;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent.LogLevel;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.topology.PerNodeLevelChanges;
import java.util.*;
import java.util.regex.Pattern;

/** Round 8: replays the 2880-log matrix and records which cells produce each branch pattern. */
public class R9MatrixCheck {
    static final String ABSENT = "<absent>";
    static String gl(String g) { return ABSENT.equals(g) ? "" : "  groupingId: " + g + "\n"; }
    static String row(int t, String g) { return "eventLogRecord:\n  logTime: " + t + "\n" + gl(g) + "  event: Tick\n  nodeLogs:\n    - priceListener: { seen: true}\n---\n"; }
    static String nodeLine(int t, String g, String n) { return "eventLogRecord:\n  logTime: " + t + "\n" + gl(g) + "  event: Tick\n  nodeLogs:\n    - " + n + ": { checked: true}\n---\n"; }
    static String control(int t, String g, EventLogControlEvent c) { return "eventLogRecord:\n  logTime: " + t + "\n" + gl(g) + "  event: EventLogControlEvent\n  eventToString: " + c + "\n  nodeLogs:\n---\n"; }
    static String unreadable(int t, String g) { return "eventLogRecord:\n  logTime: " + t + "\n" + gl(g) + "  event: EventLogControlEvent\n  eventToString: EventLogConfig{level=INFO, logRecordProcessor=Proc{a, sourceId=x}, sourceId=riskMonitor, groupId=null}\n  nodeLogs:\n---\n"; }
    static final String MARKER_1 = "eventLogRecord:\n  streamEnd: normal\n  streamEndRecords: 1\n---\n";
    public static void main(String[] a) {
        Map<String, Pattern> p = new LinkedHashMap<>();
        p.put("bound: no marker, plain \", so\"", Pattern.compile(", so after record \\d+(?: and before record \\d+)?, in [^.]*are not in this log\\. It is still"));
        p.put("bound: no marker, own sentence", Pattern.compile("is not established\\. After record \\d+ and before record \\d+, in "));
        p.put("bound: no marker, with a premise (R7-1)", Pattern.compile("If [^.]*, then after record \\d+(?: and before record \\d+)?, in [^.]*; otherwise"));
        p.put("bound: before a marker, no closer", Pattern.compile(", so after record \\d+ and before the stream-end marker preceding record \\d+, in [^.]*are not in this log\\. It is still"));
        p.put("bound: view wholly before a marker, closer past it", Pattern.compile("\\. After record \\d+ and before the stream-end marker preceding record \\d+, in [^.]*are not in this log\\. It is still"));
        p.put("bound: view wholly before a marker, with a premise", Pattern.compile("If [^.]*, then after record \\d+ and before the stream-end marker preceding record \\d+, in [^.;]*; otherwise"));
        p.put("bound: spanning, plain \", so\"", Pattern.compile(", so after record \\d+ and before the stream-end marker preceding record \\d+, in [^.]*\\. That marker"));
        p.put("bound: spanning, own sentence", Pattern.compile("\\. After record \\d+ and before the stream-end marker preceding record \\d+, in [^.]*\\. That marker"));
        p.put("bound: spanning, with a premise", Pattern.compile("\\. If [^.]*, then after record \\d+ and before the stream-end marker preceding record \\d+, in [^.]*\\. That marker"));
        p.put("bound: wholly after", Pattern.compile(", then after that marker(?: and before [^,]*)?, in "));
        p.put("later run: bounded by the closer or the second marker", Pattern.compile("for the records in view after that marker and before (?:record \\d+|the stream-end marker preceding record \\d+), in "));
        p.put("later run: nothing after the marker ends it", Pattern.compile("for the records in view after that marker, in "));
        for (String lit : new String[]{"whose change to INFO applied wherever this one did", "applied here, it holds until ",
                "applied here, it holds at least until ", "A later control record ", "is not established. After record "})
            p.put(lit, Pattern.compile(Pattern.quote(lit)));
        Map<String, Set<String>> cells = new LinkedHashMap<>();
        p.keySet().forEach(k -> cells.put(k, new TreeSet<>()));
        record G(String rg, String gid) { }
        List<G> gs = List.of(new G("null", null), new G("null", "alpha"), new G(ABSENT, null), new G("alpha", "alpha"), new G(ABSENT, "alpha"));
        int logs = 0, notes = 0, nulls = 0;
        Map<String,Set<String>> branchOwners=new TreeMap<>();
        Map<Integer,Integer> boundsPerNote=new TreeMap<>();
        int minHolds=0;
        for (String node : new String[]{"riskMonitor", "null"}) for (boolean ns : new boolean[]{false, true}) {
            if ("null".equals(node) && !ns) continue;
            for (G g : gs) for (String b : new String[]{"none", "spanning", "whollyAfter", "beforeMarker", "twoMarkers", "pastCloser", "twoMarkersAfter", "pastSecondOnly"})
            for (String cl : new String[]{"none", "perNode", "null", "unreadable"})
            for (String cg : cl.equals("none") || cl.equals("unreadable") ? new String[]{"-"} : new String[]{"same", "beta", "none"})
            for (String lead : new String[]{"none", "nodeLogsBefore", "otherGrouping"}) {
                String gid = switch (cg) { case "same" -> g.gid(); case "beta" -> "beta"; default -> null; };
                String rg = g.rg();
                var open = new EventLogControlEvent(ns ? null : node, g.gid(), LogLevel.WARN);
                String close = switch (cl) {
                    case "perNode" -> control(90, rg, new EventLogControlEvent(node, gid, LogLevel.INFO));
                    case "null" -> control(90, rg, new EventLogControlEvent(null, gid, LogLevel.INFO));
                    case "unreadable" -> unreadable(90, rg); default -> ""; };
                String opening = (lead.equals("nodeLogsBefore") ? nodeLine(0, rg, node) : "") + control(1, rg, open)
                        + (lead.equals("otherGrouping") ? nodeLine(1, "gamma", node) : "");
                int sh = lead.equals("none") ? 0 : 1;
                String log; int[] view;
                switch (b) {
                    case "spanning" -> { log = opening + row(2, rg) + MARKER_1 + row(3, rg) + close + row(95, rg); view = new int[]{1 + sh, 2 + sh}; }
                    case "whollyAfter" -> { log = opening + MARKER_1 + row(2, rg) + close + row(95, rg); view = new int[]{1 + sh}; }
                    case "beforeMarker" -> { log = opening + row(2, rg) + MARKER_1 + row(3, rg) + close + row(95, rg); view = new int[]{1 + sh}; }
                    case "twoMarkers" -> { log = opening + row(2, rg) + MARKER_1 + row(3, rg) + MARKER_1 + row(4, rg) + close + row(95, rg); view = new int[]{1 + sh, 2 + sh, 3 + sh}; }
                    case "pastCloser" -> { log = opening + row(2, rg) + MARKER_1 + row(3, rg) + close + row(95, rg); view = new int[]{1 + sh, 2 + sh, (close.isEmpty() ? 3 : 4) + sh}; }
                    case "twoMarkersAfter" -> { log = opening + MARKER_1 + row(2, rg) + MARKER_1 + row(3, rg) + close + row(95, rg); view = new int[]{1 + sh, 2 + sh}; }
                    case "pastSecondOnly" -> { log = opening + MARKER_1 + row(2, rg) + MARKER_1 + row(3, rg) + close + row(95, rg); view = new int[]{2 + sh}; }
                    default -> { log = opening + row(2, rg) + close + row(95, rg); view = new int[]{1 + sh}; }
                }
                logs++;
                String note = PerNodeLevelChanges.of(new HeapLogStore(log)).annotationFor(node, view);
                if (note == null) { if (b.equals("pastSecondOnly")) nulls++; continue; }
                notes++;
                boolean premise = ABSENT.equals(rg) || (ns && !"null".equals(node));
                String at = b + "/" + cl + "/" + (premise ? "premise" : "noPremise");
                int hit=0;
                String family=switch(b) {
                    case "none" -> "no-marker";
                    case "beforeMarker" -> "before-marker";
                    case "whollyAfter", "twoMarkersAfter" -> "wholly-after";
                    default -> "spanning";
                };
                for (var e : p.entrySet()) if (e.getValue().matcher(note).find()) {
                    cells.get(e.getKey()).add(at + "/" + node + "/" + rg + "/" + g.gid() + "/" + cg + "/" + lead);
                    if(e.getKey().startsWith("bound:")) {
                        hit++;
                        branchOwners.computeIfAbsent(e.getKey(),k->new TreeSet<>()).add(family+"/"+(premise?"premise":"noPremise"));
                    }
                }
                boundsPerNote.merge(hit,1,Integer::sum);
                if(note.toLowerCase(Locale.ROOT).contains("it holds"))minHolds++;
            }
        }
        System.out.println("logs=" + logs + " notes=" + notes + " pastSecondOnly-null=" + nulls);
        System.out.println("main conclusion pattern count per note="+boundsPerNote+", notes with holds="+minHolds);
        branchOwners.forEach((k,v)->System.out.println("OWNER "+k+" => "+v));
        if(!boundsPerNote.equals(Map.of(1,2520)))throw new AssertionError(boundsPerNote);
        for (var e : cells.entrySet()) {
            Set<String> bs = new TreeSet<>(), cls = new TreeSet<>(), prem = new TreeSet<>();
            for (String c : e.getValue()) { String[] x = c.split("/"); bs.add(x[0]); cls.add(x[1]); prem.add(x[2]); }
            System.out.println(e.getValue().size() + " cells  boundaries=" + bs + " closings=" + cls + " " + prem + "  «" + e.getKey() + "»");
        }
    }
}
