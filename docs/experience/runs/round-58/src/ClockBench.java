import com.bench.*; import com.bench.gen.*;
import com.telamin.fluxtion.runtime.DataFlow;
/** In-situ cost of clock.eventReceived. Identical processors except for that one call.
 *  Stream clock injected in both, so no wall-clock syscall is involved. */
public class ClockBench {
    static long streamTime = 1_700_000_000_000L;
    public static void main(String[] a){
        String arm=System.getProperty("arm");
        long w=Long.getLong("warm",5_000_000L), it=Long.getLong("iters",200_000_000L);
        MarketTick e=new MarketTick(); long ns; double bu; long br,up;
        if(arm.equals("withClock")){
            BenchProcessorLean p=new BenchProcessorLean(); p.init();
            ((DataFlow)p).setClockStrategy(() -> streamTime);
            for(long i=0;i<w;i++){ streamTime++; p.onEvent(set(e,i)); }
            long t=System.nanoTime(); for(long i=0;i<it;i++){ streamTime++; p.onEvent(set(e,i)); } ns=System.nanoTime()-t;
            bu=p.buffer.value; br=p.limit.breaches; up=p.buffer.updates;
        } else {
            BenchProcessorBare p=new BenchProcessorBare(); p.init();
            ((DataFlow)p).setClockStrategy(() -> streamTime);
            for(long i=0;i<w;i++){ streamTime++; p.onEvent(set(e,i)); }
            long t=System.nanoTime(); for(long i=0;i<it;i++){ streamTime++; p.onEvent(set(e,i)); } ns=System.nanoTime()-t;
            bu=p.buffer.value; br=p.limit.breaches; up=p.buffer.updates;
        }
        System.out.printf("RESULT %s %.4f %d %d %.4f%n",arm,(double)ns/it,br,up,bu);
    }
    static MarketTick set(MarketTick e,long i){ return e.set(100.0+(i&15),100.5+(i&15),i); }
}
