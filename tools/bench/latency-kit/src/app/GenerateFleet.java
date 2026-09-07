package app;

import com.benchv.MatrixFleet;
import com.telamin.fluxtion.builder.compile.generation.EventProcessorFactory;
import com.telamin.fluxtion.builder.generation.config.EventProcessorConfig;

/** Round 62 §9 — a processor with N nodes, N from build context. */
public class GenerateFleet {
    public static void main(String[] a) throws Exception {
        int nodes = Integer.getInteger("nodes", 50);
        EventProcessorFactory.compile(
                c -> {
                    for (int i = 0; i < nodes; i++) {
                        c.addNode(new MatrixFleet.CellNode(), "cell" + i);
                    }
                    c.performanceProfile(EventProcessorConfig.PerformanceProfile.LOWEST_LATENCY);
                },
                cfg -> {
                    cfg.setPackageName(System.getProperty("pkg"));
                    cfg.setClassName("FleetProcessor");
                    cfg.setOutputDirectory(System.getProperty("srcDir"));
                    cfg.setResourcesOutputDirectory(System.getProperty("resDir"));
                    cfg.setWriteSourceToFile(true);
                    cfg.setFormatSource(true);
                    cfg.generateReachabilityMetadata(true);
                });
        System.out.println("GENERATED " + nodes + " nodes");
    }
}
