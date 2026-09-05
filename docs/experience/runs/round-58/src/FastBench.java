import com.bench.*; import com.bench.gen.BenchProcessorFlat; import com.bench.gen.BenchProcessorFast;
import com.telamin.fluxtion.runtime.DataFlow;
/** Same binary, same auditors, same dispatch. Arms differ ONLY in the entry wrapper:
 *    stock    - callbackDispatcher.dispatchQueuedCallbacks() every event
 *    guarded  - drained only when a field flag says something is queued
 *    direct   - wrapper bypassed entirely (upper bound on what removing it can buy) */
public class FastBench {
    static long streamTime=1_700_000_000_000L;
    public static void main(String[] a){
        String arm=System.getProperty("arm");
        long warm=Long.getLong("warm",5_000_000L), it=Long.getLong("iters",200_000_000L);
        MarketTick e=new MarketTick(); long t0,ns; long br,up; double bu;
        if(arm.equals("guarded")){
            BenchProcessorFast p=new BenchProcessorFast(); p.init(); ((DataFlow)p).setClockStrategy(()->streamTime);
            for(long i=0;i<warm;i++){ streamTime++; p.onEvent(set(e,i)); }
            t0=System.nanoTime(); for(long i=0;i<it;i++){ streamTime++; p.onEvent(set(e,i)); } ns=System.nanoTime()-t0;
            br=p.limit.breaches; up=p.buffer.updates; bu=p.buffer.value;
        } else if(arm.equals("direct")){
            BenchProcessorFlat p=new BenchProcessorFlat(); p.init(); ((DataFlow)p).setClockStrategy(()->streamTime);
            for(long i=0;i<warm;i++){ streamTime++; p.handleEvent(set(e,i)); }
            t0=System.nanoTime(); for(long i=0;i<it;i++){ streamTime++; p.handleEvent(set(e,i)); } ns=System.nanoTime()-t0;
            br=p.limit.breaches; up=p.buffer.updates; bu=p.buffer.value;
        } else {
            BenchProcessorFlat p=new BenchProcessorFlat(); p.init(); ((DataFlow)p).setClockStrategy(()->streamTime);
            for(long i=0;i<warm;i++){ streamTime++; p.onEvent(set(e,i)); }
            t0=System.nanoTime(); for(long i=0;i<it;i++){ streamTime++; p.onEvent(set(e,i)); } ns=System.nanoTime()-t0;
            br=p.limit.breaches; up=p.buffer.updates; bu=p.buffer.value;
        }
        System.out.printf("RESULT %s %.4f %d %d %.4f%n",arm,(double)ns/it,br,up,bu);
    }
    static MarketTick set(MarketTick e,long i){ return e.set(100.0+(i&15),100.5+(i&15),i); }
}
