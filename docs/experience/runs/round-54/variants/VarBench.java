import com.bench2.*;
import com.telamin.fluxtion.runtime.audit.*;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent.LogLevel;
import com.telamin.fluxtion.runtime.DataFlow;
import java.lang.management.*;
import java.lang.reflect.*;

public class VarBench {
    public static void main(String[] args) throws Exception {
        String cls = System.getProperty("cls");
        String lvl = System.getProperty("level", "INFO");
        long warm = 2_000_000L, iters = Long.getLong("iters", 10_000_000L);
        DataFlow f = (DataFlow) Class.forName(cls).getDeclaredConstructor().newInstance();
        long[] recs = {0};
        f.setAuditLogProcessor(r -> recs[0]++);
        f.init();
        f.onEvent(new EventLogControlEvent(LogLevel.valueOf(lvl)));
        MarketTick e = new MarketTick();
        for (long i = 0; i < warm; i++) f.onEvent(e.set(100.0 + (i & 15), 100.5 + (i & 15), i));
        recs[0] = 0;
        long a0 = alloc(); long t0 = System.nanoTime();
        for (long i = 0; i < iters; i++) f.onEvent(e.set(100.0 + (i & 15), 100.5 + (i & 15), i));
        long ns = System.nanoTime() - t0; long a1 = alloc();
        System.out.printf("  %-28s level=%-5s  %,11.0f ev/s  %6.1f ns/ev  %7.1f B/ev  records=%,d%n",
                System.getProperty("label", cls), lvl, iters / (ns / 1e9), (double) ns / iters,
                (double)(a1 - a0) / iters, recs[0]);
    }
    static long alloc() { return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean()).getCurrentThreadAllocatedBytes(); }
}
