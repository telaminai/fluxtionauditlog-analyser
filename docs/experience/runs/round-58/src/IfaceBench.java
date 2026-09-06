import com.bench.MarketTick; import com.benchi.Chain; import com.benchv.BaseProcessor;
/** concrete  : BaseProcessor, concrete node fields (the 1.42 ns shape)
 *  iface1    : interface-typed fields, ONE implementor of each interface reachable
 *  ifaceN    : interface-typed fields, THREE implementors of each reachable (one used) */
public class IfaceBench {
    static Object keepAlive;
    public static void main(String[] a){
        String arm=System.getProperty("arm","concrete");
        long w=Long.getLong("warm",5_000_000L), it=Long.getLong("iters",200_000_000L);
        long ns; double bu; long br,up;
        if(arm.equals("concrete")){
            BaseProcessor p=new BaseProcessor(); MarketTick e=new MarketTick();
            for(long i=0;i<w;i++) p.handleEvent(set(e,i));
            long t=System.nanoTime(); for(long i=0;i<it;i++) p.handleEvent(set(e,i)); ns=System.nanoTime()-t;
            bu=p.buffer.value; br=p.limit.breaches; up=p.buffer.updates;
        } else {
            if(arm.equals("ifaceN")){                     // force 3 implementors to be reachable
                keepAlive = new Object[]{ Chain.buildB(), Chain.buildC() };
                ((Chain.IfaceProcessor)((Object[])keepAlive)[0]).handleEvent(set(new MarketTick(),1));
                ((Chain.IfaceProcessor)((Object[])keepAlive)[1]).handleEvent(set(new MarketTick(),1));
            }
            Chain.IfaceProcessor p=Chain.buildA(); MarketTick e=new MarketTick();
            for(long i=0;i<w;i++) p.handleEvent(set(e,i));
            long t=System.nanoTime(); for(long i=0;i<it;i++) p.handleEvent(set(e,i)); ns=System.nanoTime()-t;
            bu=p.buf(); br=p.breaches(); up=p.updates();
        }
        System.out.printf("RESULT %s %.4f %d %d %.4f%n",arm,(double)ns/it,br,up,bu);
    }
    static MarketTick set(MarketTick e,long i){ return e.set(100.0+(i&15),100.5+(i&15),i); }
}
