import com.telamin.fluxtion.Fluxtion;
import com.bench.*;
public class GenNoDirty {
    public static void main(String[] a) {
        TickIn tick = new TickIn();
        Mid mid = new Mid(tick); Spread sp = new Spread(tick);
        Ewma ewma = new Ewma(mid); Vol vol = new Vol(mid, ewma);
        Notional no = new Notional(mid, sp); Exposure ex = new Exposure(no, vol);
        Limit lim = new Limit(ex); Charge ch = new Charge(lim, ex); Buffer buf = new Buffer(ch);
        Fluxtion.compile(c -> {
            c.addNode(mid,"mid"); c.addNode(sp,"spread"); c.addNode(ewma,"ewma"); c.addNode(vol,"vol");
            c.addNode(no,"notional"); c.addNode(ex,"exposure"); c.addNode(lim,"limit");
            c.addNode(ch,"charge"); c.addNode(buf,"buffer");
            c.setSupportDirtyFiltering(false);
        }, cfg -> {
            cfg.setPackageName("com.bench.nodirty"); cfg.setClassName("NoDirtyProcessor");
            cfg.setOutputDirectory(a[0]); cfg.setWriteSourceToFile(true); cfg.setCompileSource(false);
            cfg.setResourcesOutputDirectory(a[1]);
        });
    }
}
