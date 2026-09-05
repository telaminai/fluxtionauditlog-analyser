import com.bench.MarketTick; import com.benchv.BaseProcessor;
public class V7_privField {
    static final class Engine {
        private final BaseProcessor p = new BaseProcessor();
        private final MarketTick    e = new MarketTick();
        long run(long n){ long t=System.nanoTime();
            for(long i=0;i<n;i++) p.handleEvent(e.set(100.0+(i&15),100.5+(i&15),i));
            return System.nanoTime()-t; }
        double buf(){ return p.buffer.value; }
    }
    public static void main(String[] a){
        long w=Long.getLong("warm",5_000_000L), it=Long.getLong("iters",200_000_000L);
        Engine eng=new Engine();                 // local, never stored anywhere
        eng.run(w); long ns=eng.run(it);
        System.out.printf("RESULT %.4f buf=%.4f%n",(double)ns/it,eng.buf());
    }
}
