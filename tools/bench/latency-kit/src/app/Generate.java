package app;

import com.benchv.Nodes;
import com.telamin.fluxtion.builder.compile.generation.EventProcessorFactory;
import com.telamin.fluxtion.builder.generation.config.EventProcessorConfig;

/**
 * Generates the processor under test with the documented lowest-latency configuration.
 *
 * <p>The node classes are read from a SEPARATELY COMPILED JAR — the generator sees them only as
 * bytecode, which is the vendor-library case.
 *
 * <p>System properties: {@code -DsrcDir=} {@code -DresDir=} {@code -Dpkg=}
 */
public class Generate {
    public static void main(String[] a) throws Exception {
        String src = System.getProperty("srcDir");
        String res = System.getProperty("resDir");
        String pkg = System.getProperty("pkg", "com.bench.gen");
        EventProcessorFactory.compile(
                c -> {
                    Nodes.TickIn tickIn = c.addNode(new Nodes.TickIn(), "tickIn");
                    Nodes.Mid mid = c.addNode(new Nodes.Mid(tickIn), "mid");
                    Nodes.Spread spread = c.addNode(new Nodes.Spread(tickIn), "spread");
                    Nodes.Ewma ewma = c.addNode(new Nodes.Ewma(mid), "ewma");
                    Nodes.Vol vol = c.addNode(new Nodes.Vol(mid, ewma), "vol");
                    Nodes.Notional notional = c.addNode(new Nodes.Notional(mid, spread), "notional");
                    Nodes.Exposure exposure = c.addNode(new Nodes.Exposure(notional, vol), "exposure");
                    c.addNode(new Nodes.Limit(exposure), "limit");
                    Nodes.Charge charge = c.addNode(new Nodes.Charge(exposure), "charge");
                    c.addNode(new Nodes.Buffer(charge), "buffer");

                    // --- the documented configuration -------------------------------------
                    // ONE line replaces the whole tuning checklist. It drops the framework
                    // auditors, turns off dirty filtering and stops node registration - the three
                    // settings whose individual omission cost 3-5x and gave no diagnostic.
                    c.performanceProfile(EventProcessorConfig.PerformanceProfile.LOWEST_LATENCY);

                    // re-entrancy, subscriptions and buffering are left ON: all measured free
                },
                cfg -> {
                    cfg.setPackageName(pkg);
                    cfg.setClassName("BenchProcessor");
                    cfg.setOutputDirectory(src);
                    cfg.setResourcesOutputDirectory(res);
                    cfg.setWriteSourceToFile(true);
                    cfg.setFormatSource(true);
                    // emits META-INF/native-image/<fqn>/native-image.properties with the
                    // PriorityForceInline directive the AOT build needs
                    cfg.generateReachabilityMetadata(true);
                });
        System.out.println("GENERATED " + pkg + ".BenchProcessor");
    }
}
