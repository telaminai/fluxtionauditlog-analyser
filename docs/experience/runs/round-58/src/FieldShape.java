import com.bench.MarketTick; import com.benchv.BaseProcessor;
/** Does holding the processor as a PRIVATE FINAL INSTANCE field (no accessor) keep the fast path? */
public class FieldShape {
    /** the realistic application object */
    static final class App {
        private final BaseProcessor p = new BaseProcessor();   // private, final, no getter
        private final MarketTick    e = new MarketTick();
        long run(long n){ long t0=System.nanoTime();
            for(long i=0;i<n;i++) p.handleEvent(e.set(100.0+(i&15),100.5+(i&15),i));
            return System.nanoTime()-t0; }
        double buf(){ return p.buffer.value; }
    }
    static final class AppNonFinal {
        private BaseProcessor p = new BaseProcessor();          // same but NOT final
        private MarketTick    e = new MarketTick();
        long run(long n){ long t0=System.nanoTime();
            for(long i=0;i<n;i++) p.handleEvent(e.set(100.0+(i&15),100.5+(i&15),i));
            return System.nanoTime()-t0; }
        double buf(){ return p.buffer.value; }
    }
    static App HELD;            // application object itself held statically — the deployed shape
    static AppNonFinal HELD_NF;
    private static final BaseProcessor SP = new BaseProcessor();   // ShapeB control
    private static final MarketTick    SE = new MarketTick();
    static long staticRun(long n){ BaseProcessor p=SP; MarketTick e=SE; long t0=System.nanoTime();
        for(long i=0;i<n;i++) p.handleEvent(e.set(100.0+(i&15),100.5+(i&15),i)); return System.nanoTime()-t0; }
    public static void main(String[] a){
        String arm=System.getProperty("arm");
        long w=Long.getLong("warm",5_000_000L), it=Long.getLong("iters",200_000_000L);
        long ns; double bu;
        switch(arm){
            case "appLocal":   { App x=new App();   x.run(w); ns=x.run(it); bu=x.buf(); break; }
            case "appStatic":  { HELD=new App();    HELD.run(w); ns=HELD.run(it); bu=HELD.buf(); break; }
            case "appNonFinal":{ HELD_NF=new AppNonFinal(); HELD_NF.run(w); ns=HELD_NF.run(it); bu=HELD_NF.buf(); break; }
            default:           { staticRun(w); ns=staticRun(it); bu=SP.buffer.value; }
        }
        System.out.printf("RESULT %s %.4f buf=%.4f%n",arm,(double)ns/it,bu);
    }
}
