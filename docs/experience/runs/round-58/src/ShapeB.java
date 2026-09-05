import com.bench.MarketTick; import com.benchv.BaseProcessor;
public class ShapeB {
    private static final BaseProcessor P = new BaseProcessor();
    private static final MarketTick    E = new MarketTick();
    static void batch(long n){ BaseProcessor p=P; MarketTick e=E;
        for(long i=0;i<n;i++) p.handleEvent(e.set(100.0+(i&15),100.5+(i&15),i)); }
    public static void main(String[] a){
        long w=Long.getLong("warm",5_000_000L), it=Long.getLong("iters",200_000_000L);
        batch(w); long t0=System.nanoTime(); batch(it); long ns=System.nanoTime()-t0;
        System.out.printf("RESULT %.4f buf=%.4f%n",(double)ns/it,P.buffer.value); } }
