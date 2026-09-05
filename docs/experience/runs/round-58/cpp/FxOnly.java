import com.bench.MarketTick; import com.benchv.BaseProcessor;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
/** ONE hot path only — the Fluxtion processor. No competing arm in the image. */
public class FxOnly {
    private static final BaseProcessor P = new BaseProcessor();
    private static final MarketTick    E = new MarketTick();
    public static void batch(long iters){
        BaseProcessor p=P; MarketTick e=E;
        for(long i=0;i<iters;i++) p.handleEvent(e.set(100.0+(i&15),100.5+(i&15),i));
    }
    @CEntryPoint(name="fx_batch")  public static void a(IsolateThread t,long n){ batch(n); }
    @CEntryPoint(name="fx_buffer") public static double b(IsolateThread t){ return P.buffer.value; }
    public static void main(String[] x){ batch(5_000_000); batch(20_000_000); System.out.println(P.buffer.value); }
}
