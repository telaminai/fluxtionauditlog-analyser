import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;
import telamin.fluxtion.audit.analyser.analyser.topology.PerNodeLevelChanges;
import java.util.Set;

/** Only the named ninth-round counterexamples. Constructed logs, not a client replay. */
public class R10Targeted extends R8Review {
    static int checks;
    static void require(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
        System.out.println("PASS " + label);
    }
    static String note(LogStore store, int... view) {
        return PerNodeLevelChanges.of(store).annotationFor("riskMonitor", view);
    }
    public static void main(String[] args) {
        String warn = ctl(1, "null", "WARN", "riskMonitor", "null");
        String empty = MARKER.replace("streamEndRecords: 1", "streamEndRecords: 0");
        for (int n : new int[]{2, 3}) {
            var store = new HeapLogStore(warn + MARKER + empty.repeat(n - 1) + row(2, "null"));
            require(store.runBoundaries().equals(java.util.Collections.nCopies(n, 1)), "boundaries " + store.runBoundaries());
            require(note(store, 1) == null, "R9-1 no annotation after " + n + " adjacent markers");
        }
        require(note(new HeapLogStore(warn + row(2, "null") + MARKER + empty + row(3, "null")), 2) == null,
                "R9-1 no annotation after empty intervening run");
        require(note(new HeapLogStore(warn + MARKER + row(2, "null")), 1) != null, "one marker still annotates");

        String closer = ctl(3, "null", "INFO", "riskMonitor", "null");
        String standard = note(new NullingStore(warn + row(2, "null") + closer + row(4, "null"), Set.of(2)), 1, 3);
        String eventTime = note(new NullingStore(warn + row(2, "null")
                + closer.replace("  logTime: 3", "  eventTime: -1\n  logTime: 3") + row(4, "null"), Set.of(2)), 1, 3);
        require(standard.equals(eventTime), "R9-2 eventTime annotation equals standard header");
        require(eventTime.contains("before record 3"), "R9-2 conclusion ends before record 3");
        System.out.println("HEADER NOTE " + eventTime);
        String spoof = "eventLogRecord:\n  logTime: 3\n  groupingId: null\n  nodeLogs:\n    - priceListener:\n        event: EventLogControlEvent\n---\n";
        String spoofNote = note(new NullingStore(warn + row(2, "null") + spoof + row(4, "null"), Set.of(2)), 1, 3);
        require(spoofNote.contains("Nothing later"), "R9-2 payload spoof does not close window");

        String separated = warn + MARKER + row(2, "null") + MARKER + row(3, "null") + MARKER
                + row(4, "null") + ctl(5, "null", "INFO", "riskMonitor", "null");
        for (int[] view : new int[][]{{0, 1}, {1, 2, 3}}) {
            String result = note(new HeapLogStore(separated), view);
            require(result.contains("Every record in view that this annotation concerns is in a LATER run"),
                    "R9-3 qualified lead for " + java.util.Arrays.toString(view));
            require(result.contains("then after that marker and before the stream-end marker preceding record 3"),
                    "R9-3 unchanged bound for " + java.util.Arrays.toString(view));
            System.out.println("LATER NOTE " + result);
        }
        System.out.println("TOTAL " + checks + " checks passed");
    }
}
