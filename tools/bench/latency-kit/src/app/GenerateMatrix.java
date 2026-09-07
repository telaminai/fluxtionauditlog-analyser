package app;

import com.benchv.MatrixNodes;
import com.telamin.fluxtion.builder.compile.generation.EventProcessorFactory;
import com.telamin.fluxtion.builder.generation.config.EventProcessorConfig;

/**
 * Round 62 — <b>the builder selects the node from build context.</b>
 *
 * <p>{@code -Dorder=4} is known when the processor is generated, so the builder adds
 * {@code Mat4Node} and the generated processor ends up with a <b>concrete field</b> whose arithmetic
 * is already unrolled for order 4. Nothing about the order survives into runtime as a decision.
 *
 * <p>This is the half a hand-written library cannot have: it must hold the interface and resolve the
 * implementation when it runs.
 */
public class GenerateMatrix {
    public static void main(String[] a) throws Exception {
        String src = System.getProperty("srcDir");
        String res = System.getProperty("resDir");
        String pkg = System.getProperty("pkg", "com.bench.genmatrix");
        int order = Integer.getInteger("order", 4);
        EventProcessorFactory.compile(
                c -> {
                    // THE POINT OF THE ROUND: a build-context decision, made once, here.
                    switch (order) {
                        case 2: c.addNode(new MatrixNodes.Mat2Node(), "mat"); break;
                        case 3: c.addNode(new MatrixNodes.Mat3Node(), "mat"); break;
                        case 4: c.addNode(new MatrixNodes.Mat4Node(), "mat"); break;
                        default: throw new IllegalArgumentException("no specialised node for order " + order);
                    }
                    c.performanceProfile(EventProcessorConfig.PerformanceProfile.LOWEST_LATENCY);
                },
                cfg -> {
                    cfg.setPackageName(pkg);
                    cfg.setClassName("MatrixProcessor");
                    cfg.setOutputDirectory(src);
                    cfg.setResourcesOutputDirectory(res);
                    cfg.setWriteSourceToFile(true);
                    cfg.setFormatSource(true);
                    cfg.generateReachabilityMetadata(true);
                });
        System.out.println("GENERATED " + pkg + ".MatrixProcessor for order " + order);
    }
}
