package app;

import com.benchv.MultiNodes;
import com.telamin.fluxtion.builder.compile.generation.EventProcessorFactory;
import com.telamin.fluxtion.builder.generation.config.EventProcessorConfig;

/**
 * Generates the THREE-EVENT-TYPE processor, same documented configuration as {@link Generate}.
 *
 * <p>System properties: {@code -DsrcDir=} {@code -DresDir=} {@code -Dpkg=}
 */
public class GenerateMulti {
    public static void main(String[] a) throws Exception {
        String src = System.getProperty("srcDir");
        String res = System.getProperty("resDir");
        String pkg = System.getProperty("pkg", "com.bench.genmulti");
        EventProcessorFactory.compile(
                c -> {
                    MultiNodes.TickIn tickIn = c.addNode(new MultiNodes.TickIn(), "tickIn");
                    MultiNodes.TradeIn tradeIn = c.addNode(new MultiNodes.TradeIn(), "tradeIn");
                    MultiNodes.LimitIn limitIn = c.addNode(new MultiNodes.LimitIn(), "limitIn");
                    MultiNodes.Mid mid = c.addNode(new MultiNodes.Mid(tickIn), "mid");
                    MultiNodes.Spread spread = c.addNode(new MultiNodes.Spread(tickIn), "spread");
                    MultiNodes.Ewma ewma = c.addNode(new MultiNodes.Ewma(mid), "ewma");
                    MultiNodes.Vol vol = c.addNode(new MultiNodes.Vol(mid, ewma), "vol");
                    MultiNodes.Notional notional = c.addNode(new MultiNodes.Notional(mid, spread), "notional");
                    MultiNodes.Position position = c.addNode(new MultiNodes.Position(tradeIn), "position");
                    MultiNodes.Exposure exposure =
                            c.addNode(new MultiNodes.Exposure(notional, vol, position), "exposure");
                    c.addNode(new MultiNodes.Limit(exposure, limitIn), "limit");
                    MultiNodes.Charge charge = c.addNode(new MultiNodes.Charge(exposure), "charge");
                    c.addNode(new MultiNodes.Buffer(charge), "buffer");

                    c.performanceProfile(EventProcessorConfig.PerformanceProfile.LOWEST_LATENCY);
                },
                cfg -> {
                    cfg.setPackageName(pkg);
                    cfg.setClassName("MultiProcessor");
                    cfg.setOutputDirectory(src);
                    cfg.setResourcesOutputDirectory(res);
                    cfg.setWriteSourceToFile(true);
                    cfg.setFormatSource(true);
                    cfg.generateReachabilityMetadata(true);
                });
        System.out.println("GENERATED " + pkg + ".MultiProcessor");
    }
}
