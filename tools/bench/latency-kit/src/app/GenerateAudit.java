package app;

import com.benchv.AuditGraph;
import com.telamin.fluxtion.builder.compile.generation.EventProcessorFactory;
import com.telamin.fluxtion.builder.generation.config.EventProcessorConfig;
import com.benchv.NullSink;
import com.telamin.fluxtion.runtime.audit.EventLogManager;

/**
 * Round 63 — three audit configurations on one graph.
 *
 * <ul>
 *   <li>{@code -Dmode=none}    LOWEST_LATENCY, no audit at all — today's baseline</li>
 *   <li>{@code -Dmode=traced}  AUDITED + full node-invocation tracing — the documented 550 ns shape</li>
 *   <li>{@code -Dmode=minimal} the proposed low-latency audit: tracing OFF, event toString OFF,
 *       thread name OFF, values only, from three nodes</li>
 * </ul>
 */
public class GenerateAudit {
    public static void main(String[] a) throws Exception {
        String mode = System.getProperty("mode", "none");
        EventProcessorFactory.compile(
                c -> {
                    AuditGraph.TickIn tickIn = c.addNode(new AuditGraph.TickIn(), "tickIn");
                    AuditGraph.Mid mid = c.addNode(new AuditGraph.Mid(tickIn), "mid");
                    AuditGraph.Spread spread = c.addNode(new AuditGraph.Spread(tickIn), "spread");
                    AuditGraph.Ewma ewma = c.addNode(new AuditGraph.Ewma(mid), "ewma");
                    AuditGraph.Vol vol = c.addNode(new AuditGraph.Vol(mid, ewma), "vol");
                    AuditGraph.Notional not = c.addNode(new AuditGraph.Notional(mid, spread), "notional");
                    AuditGraph.Exposure exp = c.addNode(new AuditGraph.Exposure(not, vol), "exposure");
                    c.addNode(new AuditGraph.Limit(exp), "limit");
                    AuditGraph.Charge ch = c.addNode(new AuditGraph.Charge(exp), "charge");
                    c.addNode(new AuditGraph.Buffer(ch), "buffer");

                    if ("none".equals(mode)) {
                        c.performanceProfile(EventProcessorConfig.PerformanceProfile.LOWEST_LATENCY);
                    } else if ("traced".equals(mode)) {
                        c.performanceProfile(EventProcessorConfig.PerformanceProfile.AUDITED);
                        c.addFrameworkAuditor(new EventLogManager()
                                .tracingOn(com.telamin.fluxtion.runtime.audit.EventLogControlEvent.LogLevel.INFO)
                                .printEventToString(false).printThreadName(false), EventLogManager.NODE_NAME);
                    } else {
                        // PROPOSED low-latency audit: values, no tracing, no toString, no thread name
                        c.performanceProfile(EventProcessorConfig.PerformanceProfile.AUDITED);
                        c.addFrameworkAuditor(new EventLogManager()
                                .tracingOff()
                                .logLevel(com.telamin.fluxtion.runtime.audit.EventLogControlEvent.LogLevel.INFO)
                                .printEventToString(false)
                                .printThreadName(false), EventLogManager.NODE_NAME);
                    }
                },
                cfg -> {
                    cfg.setPackageName(System.getProperty("pkg"));
                    cfg.setClassName("AuditProcessor");
                    cfg.setOutputDirectory(System.getProperty("srcDir"));
                    cfg.setResourcesOutputDirectory(System.getProperty("resDir"));
                    cfg.setWriteSourceToFile(true);
                    cfg.setFormatSource(true);
                    cfg.generateReachabilityMetadata(true);
                });
        System.out.println("GENERATED mode=" + mode);
    }
}
