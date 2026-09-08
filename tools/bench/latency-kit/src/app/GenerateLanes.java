package app;

import com.benchv.LaneGraph;
import com.telamin.fluxtion.builder.compile.generation.EventProcessorFactory;
import com.telamin.fluxtion.builder.generation.config.EventProcessorConfig;

/** Round 62 §13 — N lanes of three light nodes, N from build context. */
public class GenerateLanes {
    public static void main(String[] x) throws Exception {
        int lanes = Integer.getInteger("lanes", 16);
        EventProcessorFactory.compile(
                c -> {
                    LaneGraph.Src src = c.addNode(new LaneGraph.Src(), "src");
                    for (int i = 0; i < lanes; i++) {
                        LaneGraph.A a = c.addNode(new LaneGraph.A(src, 0.5 + i * 0.01), "a" + i);
                        LaneGraph.B b = c.addNode(new LaneGraph.B(a), "b" + i);
                        c.addNode(new LaneGraph.C(b), "c" + i);
                    }
                    c.performanceProfile(EventProcessorConfig.PerformanceProfile.LOWEST_LATENCY);
                },
                cfg -> {
                    cfg.setPackageName(System.getProperty("pkg"));
                    cfg.setClassName("LaneProcessor");
                    cfg.setOutputDirectory(System.getProperty("srcDir"));
                    cfg.setResourcesOutputDirectory(System.getProperty("resDir"));
                    cfg.setWriteSourceToFile(true);
                    cfg.setFormatSource(true);
                    cfg.generateReachabilityMetadata(true);
                });
        System.out.println("GENERATED " + (lanes * 3 + 1) + " nodes");
    }
}
