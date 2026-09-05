import com.bench.MarketTick; import com.benchv.*;
/** ONE processor only. The arms differ ONLY in how it is held:
 *   local   - created in the measured method, never stored  (the Addendum 6 shape)
 *   field   - stored in a static field, as a deployment holds it
 *   opaque  - stored, and handed out through a method so the compiler cannot assume uniqueness */
public class SingletonBench {
    static BaseProcessor HELD;
    static MarketTick    EV = new MarketTick();
    static BaseProcessor get(){ return HELD; }
    public static void main(String[] a){
        String arm=System.getProperty("arm");
        long warm=Long.getLong("warm",5_000_000L), it=Long.getLong("iters",200_000_000L);
        long t0,ns; double bu;
        if(arm.equals("local")){
            BaseProcessor p=new BaseProcessor(); MarketTick e=new MarketTick();
            for(long i=0;i<warm;i++) p.handleEvent(set(e,i));
            t0=System.nanoTime(); for(long i=0;i<it;i++) p.handleEvent(set(e,i)); ns=System.nanoTime()-t0;
            bu=p.buffer.value;
        } else if(arm.equals("field")){
            HELD=new BaseProcessor();
            for(long i=0;i<warm;i++) HELD.handleEvent(set(EV,i));
            t0=System.nanoTime(); for(long i=0;i<it;i++) HELD.handleEvent(set(EV,i)); ns=System.nanoTime()-t0;
            bu=HELD.buffer.value;
        } else {
            HELD=new BaseProcessor(); BaseProcessor p=get();
            for(long i=0;i<warm;i++) p.handleEvent(set(EV,i));
            t0=System.nanoTime(); for(long i=0;i<it;i++) p.handleEvent(set(EV,i)); ns=System.nanoTime()-t0;
            bu=p.buffer.value;
        }
        System.out.printf("RESULT %s %.4f buf=%.4f%n",arm,(double)ns/it,bu);
    }
    static MarketTick set(MarketTick e,long i){ return e.set(100.0+(i&15),100.5+(i&15),i); }
}
