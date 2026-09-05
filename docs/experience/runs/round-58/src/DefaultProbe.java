/** Do the six empty auditor calls cost anything? Loop carries REAL observable work so it
 *  cannot be eliminated; arms differ only in whether the empty calls are present, and
 *  whether they are inherited DEFAULTS or explicit OVERRIDES. */
public class DefaultProbe {
    interface Hook {
        void mark();
        default void onEvent(Object o) {}
        default void complete() {}
    }
    static class Inherits implements Hook { public long n; public void mark(){n++;} }
    static class Overrides implements Hook {
        public long n; public void mark(){n++;}
        @Override public void onEvent(Object o) {}
        @Override public void complete() {}
    }
    static final Inherits  i1=new Inherits(),  i2=new Inherits(),  i3=new Inherits();
    static final Overrides o1=new Overrides(), o2=new Overrides(), o3=new Overrides();
    static double acc;
    public static void main(String[] a){
        String arm=System.getProperty("arm");
        long warm=Long.getLong("warm",5_000_000L), it=Long.getLong("iters",200_000_000L);
        Object ev=new Object(); long t0,ns;
        switch(arm){
            case "none":      for(long k=0;k<warm;k++) none(ev,k);
                              t0=System.nanoTime(); for(long k=0;k<it;k++) none(ev,k); ns=System.nanoTime()-t0; break;
            case "inherits":  for(long k=0;k<warm;k++) inh(ev,k);
                              t0=System.nanoTime(); for(long k=0;k<it;k++) inh(ev,k); ns=System.nanoTime()-t0; break;
            default:          for(long k=0;k<warm;k++) ovr(ev,k);
                              t0=System.nanoTime(); for(long k=0;k<it;k++) ovr(ev,k); ns=System.nanoTime()-t0;
        }
        System.out.printf("RESULT %s %.4f acc=%.3f%n",arm,(double)ns/it,acc);
    }
    static void work(long k){ acc = acc*0.5 + (k & 15); }        // real, observable, unremovable
    static void none(Object e,long k){ work(k); }
    static void inh (Object e,long k){ i1.onEvent(e); i2.onEvent(e); i3.onEvent(e); work(k); i1.complete(); i2.complete(); i3.complete(); }
    static void ovr (Object e,long k){ o1.onEvent(e); o2.onEvent(e); o3.onEvent(e); work(k); o1.complete(); o2.complete(); o3.complete(); }
}
