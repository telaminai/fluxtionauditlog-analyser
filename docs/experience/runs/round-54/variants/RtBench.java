import com.bench2.*;
import com.telamin.fluxtion.runtime.DataFlow;
import com.telamin.fluxtion.runtime.audit.*;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent.LogLevel;
import java.lang.management.*;

/** Same processor, built with the DEFAULT addEventAudit(INFO). Flip the knobs at RUNTIME. */
public class RtBench {
    public static void main(String[] args) throws Exception {
        String cls   = System.getProperty("cls");
        String lvl   = System.getProperty("level", "ERROR");
        String mode  = System.getProperty("mode", "default");   // default | noEventStr | noTrace | both
        long iters   = Long.getLong("iters", 10_000_000L);

        DataFlow f = (DataFlow) Class.forName(cls).getDeclaredConstructor().newInstance();
        long[] recs = {0};
        f.setAuditLogProcessor(r -> recs[0]++);
        // preInit: reach the auditor BEFORE init() and before the control event
        if (mode.startsWith("pre")) {
            EventLogManager em = f.getAuditorById(EventLogManager.NODE_NAME);
            em.printEventToString(false);
        }
        f.init();
        if (mode.equals("preInitOnly")) { /* init may re-assign; measured below */ }
        f.onEvent(new EventLogControlEvent(LogLevel.valueOf(lvl)));
        if (mode.equals("postCtrl")) {
            EventLogManager em = f.getAuditorById(EventLogManager.NODE_NAME);
            em.printEventToString(false);
        }
        EventLogManager mgr = f.getAuditorById(EventLogManager.NODE_NAME);
        boolean p0 = mgr.printEventToString, t0f = mgr.trace;
        switch (mode) {
            case "default": break;
            case "noEventStr": mgr.printEventToString(false); break;
            case "noTrace":    mgr.tracingOff(); break;
            case "both":       mgr.printEventToString(false); mgr.tracingOff(); break;
            case "preInitOnly": case "postCtrl": break;
            default: throw new IllegalArgumentException(mode);
        }

        MarketTick e = new MarketTick();
        for (long i = 0; i < 2_000_000L; i++) f.onEvent(e.set(100.0 + (i & 15), 100.5 + (i & 15), i));
        recs[0] = 0;
        long a0 = alloc(); long t0 = System.nanoTime();
        for (long i = 0; i < iters; i++) f.onEvent(e.set(100.0 + (i & 15), 100.5 + (i & 15), i));
        long ns = System.nanoTime() - t0; long a1 = alloc();
        System.out.printf("  %-11s level=%-5s  as-built[printEventToString=%b trace=%b] -> now[%b %b]  %,11.0f ev/s  %6.1f ns/ev  %7.1f B/ev  records=%,d%n",
                mode, lvl, p0, t0f, mgr.printEventToString, mgr.trace,
                iters / (ns / 1e9), (double) ns / iters, (double)(a1 - a0) / iters, recs[0]);
    }
    static long alloc() { return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean()).getCurrentThreadAllocatedBytes(); }
}
