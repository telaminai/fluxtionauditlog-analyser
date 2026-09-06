import com.bench.MarketTick; import com.benchi.Mixed;
public class MixBench {
    public static void main(String[] a){
        int k=Integer.getInteger("k",0);
        long w=Long.getLong("warm",5_000_000L), it=Long.getLong("iters",200_000_000L);
        MarketTick e=new MarketTick(); long ns; double bu; long br,up;
        switch(k){
            case 0:{ Mixed.P0 p=new Mixed.P0(); for(long i=0;i<w;i++) p.handleEvent(set(e,i));
                long t=System.nanoTime(); for(long i=0;i<it;i++) p.handleEvent(set(e,i)); ns=System.nanoTime()-t;
                bu=p.buf(); br=p.breaches(); up=p.updates(); break; }
            case 3:{ Mixed.P3 p=new Mixed.P3(); for(long i=0;i<w;i++) p.handleEvent(set(e,i));
                long t=System.nanoTime(); for(long i=0;i<it;i++) p.handleEvent(set(e,i)); ns=System.nanoTime()-t;
                bu=p.buf(); br=p.breaches(); up=p.updates(); break; }
            case 6:{ Mixed.P6 p=new Mixed.P6(); for(long i=0;i<w;i++) p.handleEvent(set(e,i));
                long t=System.nanoTime(); for(long i=0;i<it;i++) p.handleEvent(set(e,i)); ns=System.nanoTime()-t;
                bu=p.buf(); br=p.breaches(); up=p.updates(); break; }
            default:{ Mixed.P10 p=new Mixed.P10(); for(long i=0;i<w;i++) p.handleEvent(set(e,i));
                long t=System.nanoTime(); for(long i=0;i<it;i++) p.handleEvent(set(e,i)); ns=System.nanoTime()-t;
                bu=p.buf(); br=p.breaches(); up=p.updates(); }
        }
        System.out.printf("RESULT k=%d %.4f %d %d %.4f%n",k,(double)ns/it,br,up,bu);
    }
    static MarketTick set(MarketTick e,long i){ return e.set(100.0+(i&15),100.5+(i&15),i); }
}
