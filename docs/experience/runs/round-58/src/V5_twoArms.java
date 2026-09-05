import com.bench.MarketTick; import com.benchv.*;
/** V1_bare's exact measured loop, PLUS a second competing hot loop in the same binary.
 *  Tests whether PGO profile sharing is what costs the fast path. */
public class V5_twoArms {
    public static void main(String[] a){
        String arm=System.getProperty("arm","fx");
        long w=Long.getLong("warm",5_000_000L), it=Long.getLong("iters",200_000_000L);
        long ns; double bu;
        if(arm.equals("fx")){
            BaseProcessor p=new BaseProcessor(); MarketTick e=new MarketTick();
            for(long i=0;i<w;i++) p.handleEvent(set(e,i));
            long t0=System.nanoTime(); for(long i=0;i<it;i++) p.handleEvent(set(e,i)); ns=System.nanoTime()-t0;
            bu=p.buffer.value;
        } else {
            HandBase h=new HandBase(); MarketTick e=new MarketTick();
            for(long i=0;i<w;i++) h.onTick(set(e,i));
            long t0=System.nanoTime(); for(long i=0;i<it;i++) h.onTick(set(e,i)); ns=System.nanoTime()-t0;
            bu=h.buffer;
        }
        System.out.printf("RESULT %s %.4f buf=%.4f%n",arm,(double)ns/it,bu);
    }
    static MarketTick set(MarketTick e,long i){ return e.set(100.0+(i&15),100.5+(i&15),i); }
}
