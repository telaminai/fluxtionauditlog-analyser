import com.bench.MarketTick; import com.benchv.*;
/** MATCHED HOLDING. Both implementations are stateful and must store state somewhere.
 *  Each is measured in the SAME two shapes, so escape analysis is available to both or neither. */
public class FairShape {
    static final class AppFx  { private final BaseProcessor p=new BaseProcessor(); private final MarketTick e=new MarketTick();
        long run(long n){ long t=System.nanoTime(); for(long i=0;i<n;i++) p.handleEvent(e.set(100.0+(i&15),100.5+(i&15),i)); return System.nanoTime()-t; }
        double buf(){ return p.buffer.value; } }
    static final class AppHand{ private final HandBase h=new HandBase(); private final MarketTick e=new MarketTick();
        long run(long n){ long t=System.nanoTime(); for(long i=0;i<n;i++) h.onTick(e.set(100.0+(i&15),100.5+(i&15),i)); return System.nanoTime()-t; }
        double buf(){ return h.buffer; } }
    static AppFx HF; static AppHand HH;
    public static void main(String[] a){
        String arm=System.getProperty("arm");
        long w=Long.getLong("warm",5_000_000L), it=Long.getLong("iters",200_000_000L);
        long ns; double bu;
        switch(arm){
            case "fxLocal":   { BaseProcessor p=new BaseProcessor(); MarketTick e=new MarketTick();
                for(long i=0;i<w;i++) p.handleEvent(set(e,i));
                long t=System.nanoTime(); for(long i=0;i<it;i++) p.handleEvent(set(e,i)); ns=System.nanoTime()-t; bu=p.buffer.value; break; }
            case "handLocal": { HandBase h=new HandBase(); MarketTick e=new MarketTick();
                for(long i=0;i<w;i++) h.onTick(set(e,i));
                long t=System.nanoTime(); for(long i=0;i<it;i++) h.onTick(set(e,i)); ns=System.nanoTime()-t; bu=h.buffer; break; }
            case "fxField":   { HF=new AppFx();  HF.run(w); ns=HF.run(it); bu=HF.buf(); break; }
            default:          { HH=new AppHand();HH.run(w); ns=HH.run(it); bu=HH.buf(); }
        }
        System.out.printf("RESULT %s %.4f buf=%.4f%n",arm,(double)ns/it,bu);
    }
    static MarketTick set(MarketTick e,long i){ return e.set(100.0+(i&15),100.5+(i&15),i); }
}
