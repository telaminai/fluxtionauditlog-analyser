package telamin.fluxtion.audit.analyser.analyser.ui;

import java.nio.file.*;
import java.util.*;
import javax.swing.SwingUtilities;
import telamin.fluxtion.audit.analyser.analyser.parse.*;
import telamin.fluxtion.audit.analyser.analyser.filter.*;
import telamin.fluxtion.audit.analyser.analyser.graph.*;

/** Constructed independent probes against the unmodified packaged review subject. */
public class RereviewProbe {
    static String record(int time, String nodes) {
        return "eventLogRecord:\n  event: Tick\n  logTime: " + time + "\n  nodeLogs:\n" + nodes + "---\n";
    }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new TopologyPanel();
            panel.load(Path.of("docs/handoff/evidence/unguided-session-2026-09-21/fixtures/MarketProcessor.src-round3.graphml"));
            var store = new HeapLogStore(record(1000, "    - rootNode: {v: 1}\n"));
            var filter = new FilterState(); var tabs = new GraphTabs(); tabs.bind(store, filter);
            var table = new LogTablePanel(); table.setModel(new LogTableModel(store));
            var ex = new ActionExecutor(() -> store, () -> filter, tabs, table, (r,n,f,k) -> {});
            ex.bind(panel, null);
            panel.selectNode("rootNode"); panel.setFocus(true);
            var before = panel.cursorState();
            var reply = ex.render("topology", Map.of("showAll", true, "saveFocusAs", "review-focus"));
            System.out.println("ATOMIC_BEFORE=" + before);
            System.out.println("ATOMIC_REPLY=" + reply.toMap());
            System.out.println("ATOMIC_AFTER=" + panel.cursorState());

            var alternate = new HeapLogStore(record(1000, "    - nodeA: {x: 1}\n")
                    + record(2000, "    - nodeB: {y: 2}\n") + record(3000, "    - nodeA: {x: 4}\n"));
            Map<String,Object> strict = Map.of("expr", "nodeA.x + nodeB.y", "resolve", "STRICT");
            System.out.println("STRICT_ACTION=" + SeriesScan.scan(alternate, strict));
            var strictPicture = ReportSeriesPicture.of(alternate, filter, strict, 700, 300);
            System.out.println("STRICT_REPORT=" + strictPicture.caption() + "; problem=" + strictPicture.problem());
            var simple = new HeapLogStore(record(1000, "    - rootNode: {v: 1}\n")
                    + record(2000, "    - rootNode: {v: 3}\n") + record(3000, "    - rootNode: {v: 2}\n"));
            Map<String,Object> narrow = Map.of("expr", "rootNode.v", "filter", Map.of("from", 2000, "to", 2000));
            System.out.println("FILTER_ACTION=" + SeriesScan.scan(simple, narrow));
            var narrowPicture = ReportSeriesPicture.of(simple, filter, narrow, 700, 300);
            System.out.println("FILTER_REPORT=" + narrowPicture.caption() + "; problem=" + narrowPicture.problem());
        });
        int accepted=0, refused=0, mismatch=0;
        for (String name : List.of("normal", "a:b", "note", "series", "a\\b", "a\nb", "a\tb", " a ", "a\"b", "", " ")) {
            String address = SpotlightTarget.graphAddress(name);
            if (address == null) { refused++; continue; }
            var parsed = SpotlightTarget.parse(address);
            if (!parsed.ok() || !name.equals(parsed.target().graph())) {
                mismatch++;
                System.out.println("ADDRESS_MISMATCH=" + name.replace("\n", "\\n").replace("\t", "\\t") + " -> " + parsed);
            } else accepted++;
        }
        System.out.println("ADDRESS accepted=" + accepted + " refused=" + refused + " mismatched=" + mismatch);
        System.exit(0);
    }
}
