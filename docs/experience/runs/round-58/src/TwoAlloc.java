import com.bench.MarketTick; import com.benchv.BaseProcessor;
/** ONE processor is measured. The only difference from PureLocal is that a SECOND
 *  BaseProcessor is also allocated and kept reachable — as a real deployment might
 *  have more than one processor instance of the same generated class. */
public class TwoAlloc {
    static BaseProcessor OTHER;
    public static void main(String[] a){
        long warm=Long.getLong("warm",5_000_000L), it=Long.getLong("iters",200_000_000L);
        if (!"none".equals(System.getProperty("second"))) {
            OTHER = new BaseProcessor();                    // second instance, reachable
            OTHER.handleEvent(new MarketTick().set(1,2,3));
        }
        BaseProcessor p=new BaseProcessor(); MarketTick e=new MarketTick();
        for(long i=0;i<warm;i++) p.handleEvent(set(e,i));
        long t0=System.nanoTime(); for(long i=0;i<it;i++) p.handleEvent(set(e,i)); long ns=System.nanoTime()-t0;
        System.out.printf("RESULT %.4f buf=%.4f other=%s%n",(double)ns/it,p.buffer.value,
            OTHER==null?"absent":"present");
    }
    static MarketTick set(MarketTick e,long i){ return e.set(100.0+(i&15),100.5+(i&15),i); }
}
