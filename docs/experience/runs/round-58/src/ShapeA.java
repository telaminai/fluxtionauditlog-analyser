import com.bench.MarketTick; import com.benchv.BaseProcessor;
public class ShapeA {
    static void batch(BaseProcessor p, MarketTick e, long n){
        for(long i=0;i<n;i++) p.handleEvent(e.set(100.0+(i&15),100.5+(i&15),i)); }
    public static void main(String[] a){
        long w=Long.getLong("warm",5_000_000L), it=Long.getLong("iters",200_000_000L);
        BaseProcessor p=new BaseProcessor(); MarketTick e=new MarketTick();
        batch(p,e,w); long t0=System.nanoTime(); batch(p,e,it); long ns=System.nanoTime()-t0;
        System.out.printf("RESULT %.4f buf=%.4f%n",(double)ns/it,p.buffer.value); } }
