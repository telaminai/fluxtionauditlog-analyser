import com.bench.*; import com.bench.gen.BenchProcessorBare; import com.plain.*;
public class BareBench {
    public static void main(String[] a) {
        long warm=Long.getLong("warm",5_000_000L), iters=Long.getLong("iters",200_000_000L);
        MarketTick e=new MarketTick(); BenchProcessorBare p=new BenchProcessorBare(); p.init();
        for(long i=0;i<warm;i++) p.onEvent(set(e,i));
        long t0=System.nanoTime();
        for(long i=0;i<iters;i++) p.onEvent(set(e,i));
        long ns=System.nanoTime()-t0;
        System.out.printf("RESULT bare %.4f %.6f %d %d %.4f%n",(double)ns/iters,0.0,
            p.limit.breaches,p.buffer.updates,p.buffer.value);
    }
    static MarketTick set(MarketTick e,long i){ return e.set(100.0+(i&15),100.5+(i&15),i); }
}
