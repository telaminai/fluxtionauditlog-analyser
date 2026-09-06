import com.bench.MarketTick; import com.benchv.BaseProcessor;
public class Conc {
    public static void main(String[] a){
        long w=Long.getLong("warm",5_000_000L), it=Long.getLong("iters",200_000_000L);
        BaseProcessor p=new BaseProcessor(); MarketTick e=new MarketTick();
        for(long i=0;i<w;i++) p.handleEvent(set(e,i));
        long t=System.nanoTime(); for(long i=0;i<it;i++) p.handleEvent(set(e,i)); long ns=System.nanoTime()-t;
        System.out.printf("RESULT concrete %.4f %d %d %.4f%n",(double)ns/it,p.limit.breaches,p.buffer.updates,p.buffer.value);
    }
    static MarketTick set(MarketTick e,long i){ return e.set(100.0+(i&15),100.5+(i&15),i); }
}
