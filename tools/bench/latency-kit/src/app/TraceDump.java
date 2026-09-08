package app;

import com.bench.E0;
import com.bench.E1;
import com.bench.E2;
import com.bench.traced.TracedProcessor;
import com.telamin.fluxtion.runtime.audit.EventLogManager;

/**
 * Round 63 §17 — <b>use the auditor to establish, as fact, what the processor did.</b>
 *
 * <p>The timing question ("2.17 ns for a 30-node graph seems too fast") was being answered by
 * arithmetic on record sizes. It does not need to be: with tracing on, the audit record names every
 * node the dispatcher invoked, in the order it invoked them. That is a direct observation of the work,
 * and it is the reason the auditor exists.
 */
public class TraceDump {
    public static void main(String[] a) throws Exception {
        TracedProcessor p = new TracedProcessor();
        EventLogManager mgr = p.getAuditorById(EventLogManager.NODE_NAME);
        final StringBuilder captured = new StringBuilder();
        mgr.setLogSink(r -> captured.append(r.asCharSequence()).append('\n'));
        p.init();

        Object[] events = {new E0().set(3.0), new E1().set(3.0), new E2().set(3.0)};
        for (Object e : events) {
            captured.setLength(0);
            p.onEvent(e);
            String rec = captured.toString();
            int nodes = rec.split("- ").length - 1;
            System.out.println("---- event " + e.getClass().getSimpleName()
                    + " invoked " + nodes + " nodes ----");
            System.out.println(rec.trim());
            System.out.println();
        }
    }
}
