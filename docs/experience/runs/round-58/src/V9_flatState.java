import com.bench.MarketTick; import com.benchv.*;
public class V9_flatState {
    public static void main(String[] a){
        String arm=System.getProperty("arm","flat");
        long w=Long.getLong("warm",5_000_000L), it=Long.getLong("iters",200_000_000L);
        MarketTick e=new MarketTick(); long ns; double bu;
        if(arm.equals("flat")){
            FlatStateProcessor p=new FlatStateProcessor();
            for(long i=0;i<w;i++) p.handleEvent(set(e,i));
            long t=System.nanoTime(); for(long i=0;i<it;i++) p.handleEvent(set(e,i)); ns=System.nanoTime()-t; bu=p.buffer;
        } else if(arm.equals("nodes")){
            BaseProcessor p=new BaseProcessor();
            for(long i=0;i<w;i++) p.handleEvent(set(e,i));
            long t=System.nanoTime(); for(long i=0;i<it;i++) p.handleEvent(set(e,i)); ns=System.nanoTime()-t; bu=p.buffer.value;
        } else {
            HandBase h=new HandBase();
            for(long i=0;i<w;i++) h.onTick(set(e,i));
            long t=System.nanoTime(); for(long i=0;i<it;i++) h.onTick(set(e,i)); ns=System.nanoTime()-t; bu=h.buffer;
        }
        System.out.printf("RESULT %s %.4f buf=%.4f%n",arm,(double)ns/it,bu);
    }
    static MarketTick set(MarketTick e,long i){ return e.set(100.0+(i&15),100.5+(i&15),i); }
}
