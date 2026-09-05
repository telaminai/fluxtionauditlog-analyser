import com.bench.MarketTick;
import com.benchv.BaseProcessor;
import com.benchv.HandBase;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;

/** Both Java arms exposed over the C ABI. The hot loops are PLAIN STATIC METHODS so a profile
 *  collected from an executable built from these same classes names the same methods — the
 *  CEntryPoint wrappers are trivial and carry no work. */
public class FluxtionLib {
    private static final BaseProcessor P = new BaseProcessor();
    private static final HandBase      H = new HandBase();
    private static final MarketTick    E = new MarketTick();

    public static void fluxtionBatch(long iters) {
        BaseProcessor p = P; MarketTick e = E;
        for (long i = 0; i < iters; i++) p.handleEvent(e.set(100.0 + (i & 15), 100.5 + (i & 15), i));
    }
    public static void handBatch(long iters) {
        HandBase h = H; MarketTick e = E;
        for (long i = 0; i < iters; i++) { e.set(100.0 + (i & 15), 100.5 + (i & 15), i); h.onTick(e); }
    }
    @CEntryPoint(name = "fx_run_batch")   public static void a(IsolateThread t, long n){ fluxtionBatch(n); }
    @CEntryPoint(name = "fx_hand_batch")  public static void b(IsolateThread t, long n){ handBatch(n); }
    @CEntryPoint(name = "fx_on_tick")     public static void c(IsolateThread t,double bid,double ask,long s){ P.handleEvent(E.set(bid,ask,s)); }
    @CEntryPoint(name = "fx_buffer")      public static double d(IsolateThread t){ return P.buffer.value; }
    @CEntryPoint(name = "fx_hand_buffer") public static double e(IsolateThread t){ return H.buffer; }

    /** Executable entry, same hot methods — used only to collect the PGO profile. */
    public static void main(String[] args) {
        fluxtionBatch(5_000_000); handBatch(5_000_000);
        fluxtionBatch(20_000_000); handBatch(20_000_000);
        System.out.println(P.buffer.value + " " + H.buffer);
    }
}
