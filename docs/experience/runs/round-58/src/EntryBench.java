import com.bench.*; import com.bench.gen.BenchProcessorBare;
/** Isolates the generic ENTRY WRAPPER (processEvent -> queueReentrantEvent /
 *  dispatchQueuedCallbacks) from the dispatch method it wraps. Identical work either way. */
public class EntryBench {
    public static void main(String[] a){
        String arm=System.getProperty("arm");
        long warm=Long.getLong("warm",5_000_000L), it=Long.getLong("iters",200_000_000L);
        MarketTick e=new MarketTick(); BenchProcessorBare p=new BenchProcessorBare(); p.init();
        long t0,ns;
        if(arm.equals("viaEntry")){                       // onEvent -> processEvent -> ... -> handleEvent
            for(long i=0;i<warm;i++) p.onEvent(set(e,i));
            t0=System.nanoTime(); for(long i=0;i<it;i++) p.onEvent(set(e,i)); ns=System.nanoTime()-t0;
        } else {                                          // straight to the dispatch, wrapper bypassed
            for(long i=0;i<warm;i++) p.handleEvent(set(e,i));
            t0=System.nanoTime(); for(long i=0;i<it;i++) p.handleEvent(set(e,i)); ns=System.nanoTime()-t0;
        }
        System.out.printf("RESULT %s %.4f %d %d %.4f%n",arm,(double)ns/it,
            p.limit.breaches,p.buffer.updates,p.buffer.value);
    }
    static MarketTick set(MarketTick e,long i){ return e.set(100.0+(i&15),100.5+(i&15),i); }
}
