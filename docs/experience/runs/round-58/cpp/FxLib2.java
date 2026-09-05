import com.bench.MarketTick; import com.benchv.BaseProcessor; import com.benchv.HandBase;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
/** The shared library was stuck at 4.19 ns because its processor lived in a static field —
 *  the 2.57 shape, plus shared-library overhead. A BATCH entry point can construct the
 *  processor inside the call, making it non-escaping: the 1.58 shape. Both are offered here. */
public class FxLib2 {
    static double outBuf; static long outBreaches, outUpdates;
    private static final BaseProcessor STATIC_P = new BaseProcessor();
    private static final MarketTick    STATIC_E = new MarketTick();

    /** processor created inside the call — never escapes */
    public static void batchLocal(long n) {
        BaseProcessor p = new BaseProcessor(); MarketTick e = new MarketTick();
        for (long i = 0; i < n; i++) p.handleEvent(e.set(100.0+(i&15), 100.5+(i&15), i));
        outBuf = p.buffer.value; outBreaches = p.limit.breaches; outUpdates = p.buffer.updates;
    }
    /** processor held in a static field — survives across calls */
    public static void batchStatic(long n) {
        BaseProcessor p = STATIC_P; MarketTick e = STATIC_E;
        for (long i = 0; i < n; i++) p.handleEvent(e.set(100.0+(i&15), 100.5+(i&15), i));
        outBuf = p.buffer.value; outBreaches = p.limit.breaches; outUpdates = p.buffer.updates;
    }
    /** hand-rolled Java, same holding as batchLocal, for an in-library reference */
    public static void batchHand(long n) {
        HandBase h = new HandBase(); MarketTick e = new MarketTick();
        for (long i = 0; i < n; i++) h.onTick(e.set(100.0+(i&15), 100.5+(i&15), i));
        outBuf = h.buffer; outBreaches = h.breaches; outUpdates = h.updates;
    }
    @CEntryPoint(name="fx_local")  public static void a(IsolateThread t, long n){ batchLocal(n); }
    @CEntryPoint(name="fx_static") public static void b(IsolateThread t, long n){ batchStatic(n); }
    @CEntryPoint(name="fx_hand")   public static void c(IsolateThread t, long n){ batchHand(n); }
    @CEntryPoint(name="fx_buf")    public static double d(IsolateThread t){ return outBuf; }

    /** executable entry — same static methods, so the profile names the same hot code */
    public static void main(String[] x){
        batchLocal(5_000_000); batchStatic(5_000_000); batchHand(5_000_000);
        batchLocal(30_000_000); batchStatic(30_000_000); batchHand(30_000_000);
        System.out.println(outBuf);
    }
}
