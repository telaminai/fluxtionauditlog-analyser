/** Does declaring the clock-strategy field as a CONCRETE type instead of the interface make the
 *  per-event wall-clock read monomorphic and faster under AOT?
 *  Several ClockStrategy implementations are reachable, and all state is static/escaping —
 *  the realistic shape, per addendum 8. */
public class ClockProbe {
    interface Strategy { long time(); }
    static final class Fixed  implements Strategy { long t=1_700_000_000_000L; public long time(){ return ++t; } }
    static final class Wall   implements Strategy { public long time(){ return System.currentTimeMillis(); } }
    static final class Zero   implements Strategy { public long time(){ return 0L; } }

    /** as the runtime declares it today: interface-typed field */
    static final class ClockIface { Strategy s; long processTime, eventTime;
        void eventReceived(long ev){ processTime = s.time(); eventTime = ev; } }
    /** as it would be with the declaration mapped to the concrete type */
    static final class ClockConcrete { Fixed s; long processTime, eventTime;
        void eventReceived(long ev){ processTime = s.time(); eventTime = ev; } }

    static ClockIface    CI = new ClockIface();
    static ClockConcrete CC = new ClockConcrete();
    static Strategy[] ALL;                      // keeps every implementation reachable
    static double acc;
    public static void main(String[] a){
        String arm=System.getProperty("arm");
        long warm=Long.getLong("warm",5_000_000L), it=Long.getLong("iters",200_000_000L);
        CI.s = new Fixed(); CC.s = new Fixed();
        ALL = new Strategy[]{ CI.s, new Wall(), new Zero() };
        long t0,ns;
        if(arm.equals("iface")){
            for(long k=0;k<warm;k++){ CI.eventReceived(k); acc += CI.processTime & 1; }
            t0=System.nanoTime(); for(long k=0;k<it;k++){ CI.eventReceived(k); acc += CI.processTime & 1; } ns=System.nanoTime()-t0;
        } else {
            for(long k=0;k<warm;k++){ CC.eventReceived(k); acc += CC.processTime & 1; }
            t0=System.nanoTime(); for(long k=0;k<it;k++){ CC.eventReceived(k); acc += CC.processTime & 1; } ns=System.nanoTime()-t0;
        }
        System.out.printf("RESULT %s %.4f acc=%.1f reach=%d%n",arm,(double)ns/it,acc,ALL.length);
    }
}
