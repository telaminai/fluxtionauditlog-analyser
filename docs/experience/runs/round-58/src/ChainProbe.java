/** onEventInternal dispatches with an if/else-if instanceof CHAIN, so cost is linear in the
 *  number of event types AND depends on position. Measures the last-position cost at 1/4/8/16
 *  event types, against a switch on a type id. */
public class ChainProbe {
    interface Ev { int id(); }
    static final class E0 implements Ev { public int id(){return 0;} }
    static final class E1 implements Ev { public int id(){return 1;} }
    static final class E2 implements Ev { public int id(){return 2;} }
    static final class E3 implements Ev { public int id(){return 3;} }
    static final class E4 implements Ev { public int id(){return 4;} }
    static final class E5 implements Ev { public int id(){return 5;} }
    static final class E6 implements Ev { public int id(){return 6;} }
    static final class E7 implements Ev { public int id(){return 7;} }
    static final class E8 implements Ev { public int id(){return 8;} }
    static final class E9 implements Ev { public int id(){return 9;} }
    static final class EA implements Ev { public int id(){return 10;} }
    static final class EB implements Ev { public int id(){return 11;} }
    static final class EC implements Ev { public int id(){return 12;} }
    static final class ED implements Ev { public int id(){return 13;} }
    static final class EE implements Ev { public int id(){return 14;} }
    static final class EF implements Ev { public int id(){return 15;} }
    static Object[] EVS;
    static void fill(int n){
        Object[] all = { new E0(),new E1(),new E2(),new E3(),new E4(),new E5(),new E6(),new E7(),
                         new E8(),new E9(),new EA(),new EB(),new EC(),new ED(),new EE(),new EF() };
        EVS = new Object[64];
        for (int i=0;i<64;i++) EVS[i] = all[16-n + (i % n)];   // last n types, cycled
    }
    static double acc;
    static void work(long k){ acc = acc*0.5 + (k & 15); }
    // the shape the generator emits: linear instanceof chain, target is LAST
    static void chain(Object e, long k, int n){
        if (n>1  && e instanceof E0) { work(k); return; }
        if (n>1  && e instanceof E1) { work(k); return; }
        if (n>4  && e instanceof E2) { work(k); return; }
        if (n>4  && e instanceof E3) { work(k); return; }
        if (n>8  && e instanceof E4) { work(k); return; }
        if (n>8  && e instanceof E5) { work(k); return; }
        if (n>8  && e instanceof E6) { work(k); return; }
        if (n>8  && e instanceof E7) { work(k); return; }
        if (e instanceof E8) { work(k); return; }
        if (e instanceof E9) { work(k); return; }
        if (e instanceof EA) { work(k); return; }
        if (e instanceof EB) { work(k); return; }
        if (e instanceof EC) { work(k); return; }
        if (e instanceof ED) { work(k); return; }
        if (e instanceof EE) { work(k); return; }
        if (e instanceof EF) { work(k); return; }
    }
    static void byId(Ev e, long k){ switch(e.id()){ default: work(k); } }
    public static void main(String[] a){
        String arm=System.getProperty("arm"); int n=Integer.getInteger("types",16);
        long warm=Long.getLong("warm",5_000_000L), it=Long.getLong("iters",200_000_000L);
        fill(n); long t0,ns;
        if(arm.equals("chain")){
            for(long k=0;k<warm;k++) chain(EVS[(int)(k&63)],k,n);
            t0=System.nanoTime(); for(long k=0;k<it;k++) chain(EVS[(int)(k&63)],k,n); ns=System.nanoTime()-t0;
        } else {
            for(long k=0;k<warm;k++) byId((Ev)EVS[(int)(k&63)],k);
            t0=System.nanoTime(); for(long k=0;k<it;k++) byId((Ev)EVS[(int)(k&63)],k); ns=System.nanoTime()-t0;
        }
        System.out.printf("RESULT %s types=%d %.4f acc=%.2f%n",arm,n,(double)ns/it,acc);
    }
}
