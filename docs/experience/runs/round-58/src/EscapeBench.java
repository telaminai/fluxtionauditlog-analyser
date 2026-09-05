import com.bench.MarketTick; import com.benchv.*;
/** Does the 1.55 ns figure survive the processor being STATIC rather than a non-escaping local?
 *  Identical work; only the reachability of the objects differs. */
public class EscapeBench {
    static BaseProcessor SP = new BaseProcessor();
    static HandBase      SH = new HandBase();
    static MarketTick    SE = new MarketTick();
    public static void main(String[] a){
        String arm=System.getProperty("arm");
        long warm=Long.getLong("warm",5_000_000L), it=Long.getLong("iters",200_000_000L);
        long t0,ns; double bu;
        switch(arm){
            case "localFluxtion": {
                BaseProcessor p=new BaseProcessor(); MarketTick e=new MarketTick();
                for(long i=0;i<warm;i++) p.handleEvent(set(e,i));
                t0=System.nanoTime(); for(long i=0;i<it;i++) p.handleEvent(set(e,i)); ns=System.nanoTime()-t0;
                bu=p.buffer.value; break; }
            case "staticFluxtion": {
                for(long i=0;i<warm;i++) SP.handleEvent(set(SE,i));
                t0=System.nanoTime(); for(long i=0;i<it;i++) SP.handleEvent(set(SE,i)); ns=System.nanoTime()-t0;
                bu=SP.buffer.value; break; }
            case "localHand": {
                HandBase h=new HandBase(); MarketTick e=new MarketTick();
                for(long i=0;i<warm;i++) h.onTick(set(e,i));
                t0=System.nanoTime(); for(long i=0;i<it;i++) h.onTick(set(e,i)); ns=System.nanoTime()-t0;
                bu=h.buffer; break; }
            default: {
                for(long i=0;i<warm;i++) SH.onTick(set(SE,i));
                t0=System.nanoTime(); for(long i=0;i<it;i++) SH.onTick(set(SE,i)); ns=System.nanoTime()-t0;
                bu=SH.buffer; }
        }
        System.out.printf("RESULT %s %.4f buf=%.4f%n",arm,(double)ns/it,bu);
    }
    static MarketTick set(MarketTick e,long i){ return e.set(100.0+(i&15),100.5+(i&15),i); }
}
