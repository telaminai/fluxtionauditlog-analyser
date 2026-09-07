package app;

import com.benchv.MatrixScale;
import com.telamin.fluxtion.builder.compile.generation.EventProcessorFactory;
import com.telamin.fluxtion.builder.generation.config.EventProcessorConfig;

/** Round 62 §6.2 — one processor per matrix order, the order chosen from build context. */
public class GenerateScale {
    public static void main(String[] a) throws Exception {
        int order = Integer.getInteger("order", 4);
        EventProcessorFactory.compile(
                c -> {
                    switch (order) {
                        case 2: c.addNode(new MatrixScale.Node2(), "mat"); break;
                        case 3: c.addNode(new MatrixScale.Node3(), "mat"); break;
                        case 4: c.addNode(new MatrixScale.Node4(), "mat"); break;
                        case 8: c.addNode(new MatrixScale.Node8(), "mat"); break;
                        default: throw new IllegalArgumentException("no node for order " + order);
                    }
                    c.performanceProfile(EventProcessorConfig.PerformanceProfile.LOWEST_LATENCY);
                },
                cfg -> {
                    cfg.setPackageName(System.getProperty("pkg"));
                    cfg.setClassName("ScaleProcessor");
                    cfg.setOutputDirectory(System.getProperty("srcDir"));
                    cfg.setResourcesOutputDirectory(System.getProperty("resDir"));
                    cfg.setWriteSourceToFile(true);
                    cfg.setFormatSource(true);
                    cfg.generateReachabilityMetadata(true);
                });
        System.out.println("GENERATED order " + order);
    }
}
