package com.acme.probe;
import com.telamin.fluxtion.Fluxtion;
import com.telamin.fluxtion.runtime.DataFlow;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent.LogLevel;
import com.vendor.Events;
import com.vendor.marketdata.*;
import com.vendor.pricing.*;
import java.util.*;

/**
 * Does node STATE survive being rebuilt into a NEW compiled graph, while the DISPATCH changes?
 * Built with compileDispatcher, where the reference the caller holds is the dispatch target.
 */
public class StateAcrossRebuild {
    static List<String> log = new ArrayList<>();
    static DataFlow build(Object... nodes) {
        DataFlow f = (DataFlow) Fluxtion.compileDispatcher(c -> {
            for (Object n : nodes) c.addNode(n);
            c.addEventAudit(LogLevel.INFO);
        });
        f.setAuditLogProcessor(r -> log.add(r.asCharSequence().toString()));
        f.setAuditLogLevel(LogLevel.INFO);
        f.init();
        return f;
    }
    static List<String> stagesSince(int mark) {
        List<String> out = new ArrayList<>();
        for (String r : log.subList(mark, log.size()))
            for (String line : r.split("\n"))
                if (line.contains("stage:"))
                    out.add(line.replaceAll(".*stage: ([\\w.]+).*", "$1").trim());
        return out;
    }

    public static void main(String[] a) {
        // ---- the node instances the caller owns, constructed ONCE and never rebuilt ----
        MdTick   tick     = new MdTick();
        MdConfig config   = new MdConfig();
        Mid      mid      = new Mid(tick);
        Depth    depth    = new Depth(tick);
        TickCount counter = new TickCount(tick);

        // ---- GRAPH A: marketdata + the stateful counter. No pricing. ----
        DataFlow A = build(mid, depth, counter, config);
        int m = log.size();
        A.onEvent(new Events.Tick("DEMO", 100, 102));
        A.onEvent(new Events.Tick("DEMO", 101, 103));
        A.onEvent(new Events.Tick("DEMO", 102, 104));
        List<String> dispatchA = stagesSince(m);
        int countAfterA = counter.count;
        double midAfterA = mid.value;

        // ---- GRAPH B: the SAME instances, plus pricing. Topology changed, nodes did not. ----
        Adjusted adjusted = new Adjusted(mid, depth);
        PxRate   rate     = new PxRate();
        Spread   spread   = new Spread(rate, adjusted);
        DataFlow B = build(mid, depth, counter, config, adjusted, rate, spread);
        int countAfterRebuild = counter.count;          // <-- did state survive the rebuild?
        m = log.size();
        B.onEvent(new Events.Tick("DEMO", 200, 210));
        List<String> dispatchB = stagesSince(m);

        System.out.println("graph A dispatch      : " + dispatchA);
        System.out.println("graph B dispatch      : " + dispatchB);
        System.out.println("count after 3 events  : " + countAfterA);
        System.out.println("count after REBUILD   : " + countAfterRebuild + "   (state survived: "
                           + (countAfterRebuild == countAfterA) + ")");
        System.out.println("count after B's event : " + counter.count
                           + "   (continued, not reset: " + (counter.count == countAfterA + 1) + ")");
        System.out.println("mid value carried over: " + midAfterA + " -> " + mid.value);
        System.out.println("dispatch CHANGED      : " + !new HashSet<>(dispatchA).equals(new HashSet<>(dispatchB)));
        System.out.println("pricing now dispatches: " + dispatchB.stream().anyMatch(s -> s.startsWith("pricing")));
        boolean pass = countAfterRebuild == countAfterA
                    && counter.count == countAfterA + 1
                    && !new HashSet<>(dispatchA).equals(new HashSet<>(dispatchB))
                    && dispatchB.stream().anyMatch(s -> s.startsWith("pricing"));
        System.out.println("\nRESULT: " + (pass ? "PASS - same instances, new dispatcher, state intact"
                                               : "FAIL"));
    }
}
