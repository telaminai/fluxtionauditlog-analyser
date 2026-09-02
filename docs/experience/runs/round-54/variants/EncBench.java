import com.bench2.*;
import com.telamin.fluxtion.runtime.DataFlow;
import com.telamin.fluxtion.runtime.audit.*;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent.LogLevel;
import com.telamin.fluxtion.runtime.time.Clock;
import java.lang.management.*;

/** The documented RUNTIME route: supply your own LogRecord encoder with the flag already off. */
public class EncBench {
    public static void main(String[] args) throws Exception {
        String lvl  = System.getProperty("level", "ERROR");
        boolean useEncoder = Boolean.getBoolean("encoder");
        long iters  = Long.getLong("iters", 10_000_000L);

        DataFlow f = (DataFlow) Class.forName("com.bench2.genA.PA").getDeclaredConstructor().newInstance();
        long[] recs = {0};
        boolean clearing = Boolean.getBoolean("clear");
        f.setAuditLogProcessor(r -> { recs[0]++; if (clearing) r.clear(); });
        f.init();
        if (useEncoder) {
            Clock clock = Clock.DEFAULT_CLOCK;
            LogRecord rec = new LogRecord(clock);
            rec.printEventToString(false);
            f.setAuditLogRecordEncoder(rec);
        }
        f.onEvent(new EventLogControlEvent(LogLevel.valueOf(lvl)));
        MarketTick e = new MarketTick();
        for (long i = 0; i < 2_000_000L; i++) f.onEvent(e.set(100.0 + (i & 15), 100.5 + (i & 15), i));
        recs[0] = 0;
        long a0 = alloc(); long t0 = System.nanoTime();
        for (long i = 0; i < iters; i++) f.onEvent(e.set(100.0 + (i & 15), 100.5 + (i & 15), i));
        long ns = System.nanoTime() - t0; long a1 = alloc();
        System.out.printf("  encoder=%-5b clear=%-5b level=%-5s  %,11.0f ev/s  %6.1f ns/ev  %7.1f B/ev  records=%,d%n",
                useEncoder, clearing, lvl, iters / (ns / 1e9), (double) ns / iters, (double)(a1 - a0) / iters, recs[0]);
    }
    static long alloc() { return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean()).getCurrentThreadAllocatedBytes(); }
}
