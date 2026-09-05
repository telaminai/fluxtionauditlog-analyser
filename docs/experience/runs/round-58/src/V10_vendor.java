import com.bench.MarketTick; import com.benchv.BaseProcessor;
/** Nodes come from a pre-compiled jar. No source, no flat-state codegen possible.
 *  Can native-image + PGO still scalar-replace across the jar boundary? */
public class V10_vendor {
    public static void main(String[] a){
        long w=Long.getLong("warm",5_000_000L), it=Long.getLong("iters",200_000_000L);
        BaseProcessor p=new BaseProcessor(); MarketTick e=new MarketTick();
        for(long i=0;i<w;i++) p.handleEvent(set(e,i));
        long t0=System.nanoTime(); for(long i=0;i<it;i++) p.handleEvent(set(e,i)); long ns=System.nanoTime()-t0;
        System.out.printf("RESULT %.4f buf=%.4f%n",(double)ns/it,p.buffer.value);
    }
    static MarketTick set(MarketTick e,long i){ return e.set(100.0+(i&15),100.5+(i&15),i); }
}
