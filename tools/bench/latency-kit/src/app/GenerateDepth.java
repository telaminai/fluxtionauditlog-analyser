package app;

import com.benchv.DagNodesPlain;
import com.benchv.DagNodesPlain.DagNode;
import com.telamin.fluxtion.builder.compile.generation.EventProcessorFactory;
import com.telamin.fluxtion.builder.generation.config.EventProcessorConfig;

/**
 * Round 63 §17 — a DEPTH-PARAMETERISED version of the converging graph, to test whether the measured
 * baseline is executing real work or whether the compiler has eliminated it.
 *
 * <p>Five roots, each with a chain of {@code -Ddepth=N} nodes, converging on a shared tail of 5. If the
 * arithmetic is really being performed, ns/event must rise with depth. If it does not, the graph is
 * being optimised away and every baseline in this work is measuring an empty loop.
 */
public class GenerateDepth {
    public static void main(String[] x) throws Exception {
        int depth = Integer.getInteger("depth", 6);
        EventProcessorFactory.compile(
                c -> {
                    DagNode[] roots = {
                            c.addNode(new DagNodesPlain.R0(), "r0"),
                            c.addNode(new DagNodesPlain.R1(), "r1"),
                            c.addNode(new DagNodesPlain.R2(), "r2"),
                            c.addNode(new DagNodesPlain.R3(), "r3"),
                            c.addNode(new DagNodesPlain.R4(), "r4")};
                    DagNode[] ends = new DagNode[5];
                    for (int r = 0; r < 5; r++) {
                        DagNode prev = roots[r];
                        for (int d = 0; d < depth; d++) {
                            prev = c.addNode(new DagNodesPlain.D1(prev), "c" + r + "_" + d);
                        }
                        ends[r] = prev;
                    }
                    // converge: a 3-input join over the five chain ends, then a shared tail of 5
                    DagNode j1 = c.addNode(new DagNodesPlain.D3(ends[0], ends[1], ends[2]), "j1");
                    DagNode j2 = c.addNode(new DagNodesPlain.D3(ends[3], ends[4], j1), "j2");
                    DagNode t = j2;
                    for (int i = 1; i <= 5; i++) {
                        t = c.addNode(new DagNodesPlain.Tail(t), "t" + i);
                    }
                    c.performanceProfile(EventProcessorConfig.PerformanceProfile.LOWEST_LATENCY);
                },
                cfg -> {
                    cfg.setPackageName(System.getProperty("pkg"));
                    cfg.setClassName("DepthProcessor");
                    cfg.setOutputDirectory(System.getProperty("srcDir"));
                    cfg.setResourcesOutputDirectory(System.getProperty("resDir"));
                    cfg.setWriteSourceToFile(true);
                    cfg.setFormatSource(true);
                    cfg.generateReachabilityMetadata(true);
                });
        System.out.println("GENERATED depth=" + depth);
    }
}
