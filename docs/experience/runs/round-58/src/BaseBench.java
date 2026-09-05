import com.bench.MarketTick; import com.benchv.*;
/** TRUE BASE CASE: void triggers (failBuildIfMissingBooleanReturn=false) + dirty filtering off.
 *  Both arms unconditional, identical semantics, identical output. */
public class BaseBench {
    public static void main(String[] a){
        String arm=System.getProperty("arm");
        long warm=Long.getLong("warm",5_000_000L), it=Long.getLong("iters",200_000_000L);
        MarketTick e=new MarketTick(); long t0,ns,br,up; double bu;
        if(arm.equals("generatedBase")){
            BaseProcessor p=new BaseProcessor();
            for(long i=0;i<warm;i++) p.handleEvent(set(e,i));
            t0=System.nanoTime(); for(long i=0;i<it;i++) p.handleEvent(set(e,i)); ns=System.nanoTime()-t0;
            br=p.limit.breaches; up=p.buffer.updates; bu=p.buffer.value;
        } else {
            HandBase p=new HandBase();
            for(long i=0;i<warm;i++) p.onTick(set(e,i));
            t0=System.nanoTime(); for(long i=0;i<it;i++) p.onTick(set(e,i)); ns=System.nanoTime()-t0;
            br=p.breaches; up=p.updates; bu=p.buffer;
        }
        System.out.printf("RESULT %s %.4f %d %d %.4f%n",arm,(double)ns/it,br,up,bu);
    }
    static MarketTick set(MarketTick e,long i){ return e.set(100.0+(i&15),100.5+(i&15),i); }
}
