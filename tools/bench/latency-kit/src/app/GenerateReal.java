package app;

import com.benchv.RealGraph;
import com.telamin.fluxtion.builder.compile.generation.EventProcessorFactory;
import com.telamin.fluxtion.builder.generation.config.EventProcessorConfig;

/** Round 62 §11 — the same graph, wired by the builder, with the strategies resolved at BUILD time. */
public class GenerateReal {
    public static void main(String[] a) throws Exception {
        EventProcessorFactory.compile(
                c -> {
                    // the deployment's choice, resolved HERE and never again
                    RealGraph.Smoother midS = c.addNode(new RealGraph.Ewma(0.3), "midSmoother");
                    RealGraph.Smoother spreadS = c.addNode(new RealGraph.Ewma(0.5), "spreadSmoother");
                    RealGraph.TickIn tickIn = c.addNode(new RealGraph.TickIn(), "tickIn");
                    RealGraph.TradeIn tradeIn = c.addNode(new RealGraph.TradeIn(), "tradeIn");
                    RealGraph.LimitIn limitIn = c.addNode(new RealGraph.LimitIn(), "limitIn");
                    RealGraph.Mid mid = c.addNode(new RealGraph.Mid(tickIn), "mid");
                    RealGraph.Spread spread = c.addNode(new RealGraph.Spread(tickIn), "spread");
                    RealGraph.SmoothMid sMid = c.addNode(new RealGraph.SmoothMid(mid, midS), "sMid");
                    RealGraph.SmoothSpread sSpread = c.addNode(new RealGraph.SmoothSpread(spread, spreadS), "sSpread");
                    RealGraph.Vol vol = c.addNode(new RealGraph.Vol(mid, sMid), "vol");
                    RealGraph.Position pos = c.addNode(new RealGraph.Position(tradeIn), "position");
                    RealGraph.Notional not = c.addNode(new RealGraph.Notional(sMid, sSpread), "notional");
                    RealGraph.Exposure exp = c.addNode(new RealGraph.Exposure(not, vol, pos), "exposure");
                    c.addNode(new RealGraph.Breach(exp, limitIn), "breach");
                    RealGraph.Charge ch = c.addNode(new RealGraph.Charge(exp), "charge");
                    c.addNode(new RealGraph.Buffer(ch), "buffer");
                    c.performanceProfile(EventProcessorConfig.PerformanceProfile.LOWEST_LATENCY);
                },
                cfg -> {
                    cfg.setPackageName(System.getProperty("pkg"));
                    cfg.setClassName("RealProcessor");
                    cfg.setOutputDirectory(System.getProperty("srcDir"));
                    cfg.setResourcesOutputDirectory(System.getProperty("resDir"));
                    cfg.setWriteSourceToFile(true);
                    cfg.setFormatSource(true);
                    cfg.generateReachabilityMetadata(true);
                });
        System.out.println("GENERATED RealProcessor");
    }
}
