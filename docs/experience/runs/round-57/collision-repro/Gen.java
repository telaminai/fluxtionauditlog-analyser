import com.telamin.fluxtion.Fluxtion;
public class Gen {
    public static void main(String[] a) {
        com.a.Spread sa = new com.a.Spread();
        com.b.Spread sb = new com.b.Spread();
        Fluxtion.compile(c -> { c.addNode(sa, "spreadA"); c.addNode(sb, "spreadB"); },
            cfg -> { cfg.setPackageName("gen"); cfg.setClassName("CollideProcessor");
                     cfg.setOutputDirectory(a[0]); cfg.setWriteSourceToFile(true);
                     cfg.setCompileSource(false); cfg.setResourcesOutputDirectory(a[1]); });
    }
}
