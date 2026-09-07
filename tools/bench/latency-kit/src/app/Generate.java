package app;

import com.benchv.Nodes;
import com.telamin.fluxtion.builder.compile.generation.EventProcessorFactory;

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
                    c.setSupportDirtyFiltering(false);   // void triggers: no dirty flags, no guards

                    // No auditors. The framework registers Clock, NodeNameLookup and ServiceRegistry
                    // by default; Clock alone reads System.currentTimeMillis on every event, and the
                    // three together consume the escape-analysis budget that lets the node graph
                    // dissolve. This is the "no auditors" baseline -- it gives up the audit log.
                    if (c.getAuditorMap() != null) {
                        c.getAuditorMap().keySet()
                                .removeAll(new java.util.HashSet<>(c.getFrameworkAuditorNames()));
                    }

                    // No node registration. initialiseAuditor() otherwise calls
                    // auditor.nodeRegistered(node, name) for EVERY node, publishing each into two
                    // HashMaps -- so no node can be scalar-replaced and the graph materialises. It is
                    // the single largest cost and PGO cannot rescue it. Node lookup still works: the
                    // generator emits getInstanceById/lookupInstanceName as code (M50/W4).
                    c.setSupportNodeNameLookup(false);

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
