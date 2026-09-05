import com.bench.MarketTick; import com.benchv.BaseProcessor;
/** Two HOT loops, each driving a DIFFERENT BaseProcessor instance.
 *  Tests whether the fast path depends on exactly one processor receiver being hot. */
public class V6_twoProcs {
    static BaseProcessor B = new BaseProcessor();
    static MarketTick    BE = new MarketTick();
    static long second(long n){ long t=System.nanoTime();
        for(long i=0;i<n;i++) B.handleEvent(BE.set(100.0+(i&15),100.5+(i&15),i)); return System.nanoTime()-t; }
    public static void main(String[] a){
        String arm=System.getProperty("arm","first");
        long w=Long.getLong("warm",5_000_000L), it=Long.getLong("iters",200_000_000L);
        long ns; double bu;
        if(arm.equals("first")){
            BaseProcessor p=new BaseProcessor(); MarketTick e=new MarketTick();
            for(long i=0;i<w;i++) p.handleEvent(set(e,i));
            long t0=System.nanoTime(); for(long i=0;i<it;i++) p.handleEvent(set(e,i)); ns=System.nanoTime()-t0;
            bu=p.buffer.value;
        } else { second(w); ns=second(it); bu=B.buffer.value; }
        System.out.printf("RESULT %s %.4f buf=%.4f%n",arm,(double)ns/it,bu);
    }
    static MarketTick set(MarketTick e,long i){ return e.set(100.0+(i&15),100.5+(i&15),i); }
}
