package app;

import com.benchv.DagNodes;
import com.benchv.DagNodes.DagNode;
import com.telamin.fluxtion.builder.compile.generation.EventProcessorFactory;
import com.telamin.fluxtion.builder.generation.config.EventProcessorConfig;
import com.telamin.fluxtion.runtime.audit.EventLogManager;

/** Round 63 §6 — 30 nodes, 5 event types, one shared tail, -Dmode=none|minimal|traced. */
public class GenerateDagLLA {
    public static void main(String[] x) throws Exception {
        String mode = System.getProperty("mode", "none");
        EventProcessorFactory.compile(
                c -> {
                    DagNode r0 = c.addNode(new DagNodes.R0(), "r0");
                    DagNode r1 = c.addNode(new DagNodes.R1(), "r1");
                    DagNode r2 = c.addNode(new DagNodes.R2(), "r2");
                    DagNode r3 = c.addNode(new DagNodes.R3(), "r3");
                    DagNode r4 = c.addNode(new DagNodes.R4(), "r4");
                    DagNode c0_0 = c.addNode(new DagNodes.D1(r0), "c0_0");
                    DagNode c0_1 = c.addNode(new DagNodes.D1(c0_0), "c0_1");
                    DagNode c0_2 = c.addNode(new DagNodes.D1(c0_1), "c0_2");
                    DagNode c0_3 = c.addNode(new DagNodes.D1(c0_2), "c0_3");
                    DagNode c0_4 = c.addNode(new DagNodes.D1(c0_3), "c0_4");
                    DagNode c0_5 = c.addNode(new DagNodes.D1(c0_4), "c0_5");
                    DagNode c1_0 = c.addNode(new DagNodes.D1(r1), "c1_0");
                    DagNode c1_1 = c.addNode(new DagNodes.D1(c1_0), "c1_1");
                    DagNode c1_2 = c.addNode(new DagNodes.D1(c1_1), "c1_2");
                    DagNode c2_0 = c.addNode(new DagNodes.D1(r2), "c2_0");
                    DagNode c2_1 = c.addNode(new DagNodes.D1(c2_0), "c2_1");
                    DagNode c2_2 = c.addNode(new DagNodes.D1(c2_1), "c2_2");
                    DagNode c2_3 = c.addNode(new DagNodes.D1(c2_2), "c2_3");
                    DagNode c3_0 = c.addNode(new DagNodes.D1(r3), "c3_0");
                    DagNode c4_0 = c.addNode(new DagNodes.D1(r4), "c4_0");
                    DagNode c4_1 = c.addNode(new DagNodes.D1(c4_0), "c4_1");
                    DagNode c4_2 = c.addNode(new DagNodes.D1(c4_1), "c4_2");
                    DagNode c4_3 = c.addNode(new DagNodes.D1(c4_2), "c4_3");
                    DagNode c4_4 = c.addNode(new DagNodes.D1(c4_3), "c4_4");
                    DagNode t0 = c.addNode(new DagNodes.D3(c0_5, c1_2, c2_3), "t0");
                    DagNode t1 = c.addNode(new DagNodes.D3(t0, c3_0, c4_4), "t1");
                    DagNode t2 = c.addNode(new DagNodes.D1(t1), "t2");
                    DagNode t3 = c.addNode(new DagNodes.D1(t2), "t3");
                    DagNode t4 = c.addNode(new DagNodes.D1(t3), "t4");
                    DagNode t5 = c.addNode(new DagNodes.Tail(t4), "t5");
                    if ("none".equals(mode)) {
                        c.performanceProfile(EventProcessorConfig.PerformanceProfile.LOWEST_LATENCY);
                    } else {
                        // M52.2 — the profile under test: audit on, tracing off, neither allocating
                        // default, no runtime name map, no buffer-and-trigger, no subscriptions.
                        c.performanceProfile(EventProcessorConfig.PerformanceProfile.LOW_LATENCY_AUDIT);
                        c.addLowLatencyEventLog(
                                com.telamin.fluxtion.runtime.audit.EventLogControlEvent.LogLevel.INFO);
                    }
                },
                cfg -> {
                    cfg.setPackageName(System.getProperty("pkg"));
                    cfg.setClassName("DagProcessor");
                    cfg.setOutputDirectory(System.getProperty("srcDir"));
                    cfg.setResourcesOutputDirectory(System.getProperty("resDir"));
                    cfg.setWriteSourceToFile(true);
                    cfg.setFormatSource(true);
                    cfg.generateReachabilityMetadata(true);
                });
        System.out.println("GENERATED " + mode);
    }
}
