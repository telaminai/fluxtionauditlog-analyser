import com.bench.MarketTick;
import com.benchv.BaseProcessor;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;

/** The base-case Fluxtion processor exposed as a C ABI shared library.
 *  Same ten void-trigger nodes, same dependency order, no guards/auditors/wrapper. */
public class FluxtionLib {
    private static final BaseProcessor P = new BaseProcessor();
    private static final MarketTick E = new MarketTick();

    /** One event across the C boundary — measures embedding cost as an integrator would pay it. */
    @CEntryPoint(name = "fx_on_tick")
    public static void onTick(IsolateThread t, double bid, double ask, long seq) {
        P.handleEvent(E.set(bid, ask, seq));
    }

    /** N events, loop inside Java — measures the processor with the boundary amortised away. */
    @CEntryPoint(name = "fx_run_batch")
    public static void runBatch(IsolateThread t, long iters) {
        BaseProcessor p = P; MarketTick e = E;
        for (long i = 0; i < iters; i++) p.handleEvent(e.set(100.0 + (i & 15), 100.5 + (i & 15), i));
    }
    @CEntryPoint(name = "fx_buffer")   public static double buffer(IsolateThread t){ return P.buffer.value; }
    @CEntryPoint(name = "fx_breaches") public static long breaches(IsolateThread t){ return P.limit.breaches; }
    @CEntryPoint(name = "fx_updates")  public static long updates(IsolateThread t){ return P.buffer.updates; }
}
