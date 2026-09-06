import com.bench.MarketTick; import com.benchi.Chain;
/** buildA measured, but B and C are also constructed and used — three implementors reachable. */
public class IfaceN {
    static Object keep;
    public static void main(String[] a){
        long w=Long.getLong("warm",5_000_000L), it=Long.getLong("iters",200_000_000L);
        Chain.IfaceProcessor b=Chain.buildB(), c=Chain.buildC();
        b.handleEvent(set(new MarketTick(),1)); c.handleEvent(set(new MarketTick(),1)); keep=new Object[]{b,c};
        Chain.IfaceProcessor p=Chain.buildA(); MarketTick e=new MarketTick();
        for(long i=0;i<w;i++) p.handleEvent(set(e,i));
        long t=System.nanoTime(); for(long i=0;i<it;i++) p.handleEvent(set(e,i)); long ns=System.nanoTime()-t;
        System.out.printf("RESULT ifaceN %.4f %d %d %.4f%n",(double)ns/it,p.breaches(),p.updates(),p.buf());
    }
    static MarketTick set(MarketTick e,long i){ return e.set(100.0+(i&15),100.5+(i&15),i); }
}
