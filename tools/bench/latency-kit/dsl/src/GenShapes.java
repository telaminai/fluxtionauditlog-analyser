package app;

import com.telamin.fluxtion.builder.compile.generation.EventProcessorFactory;
import com.telamin.fluxtion.builder.generation.config.EventProcessorConfig;
import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.flowfunction.aggregate.function.primitive.IntSumFlowFunction;

import static com.telamin.fluxtion.builder.DataFlowBuilder.subscribe;

/**
 * The M55.1 shapes — merge, notify and mapOnNotify — emitted for both targets from one generator.
 *
 * <p>notify and mapOnNotify share a prefix and differ only in their LAST node, which is what makes
 * P14 answerable: any difference between them is the difference between the two constructs and not
 * between two benchmarks.
 */
public class GenShapes {
    public static class Tick {
        public int price;
        public int getPrice() { return price; }
    }

    /** The notified node. It must do something, or a compiler is entitled to delete the notification. */
    public static class Sink {
        public int fires;
        @OnTrigger
        public boolean fired() { fires++; return true; }
    }

    private static final java.util.List<String> PARTS = java.util.Arrays.asList("aa", "b", "ccc");

    public static java.util.List<String> parts(Tick t) { return PARTS; }

    public static boolean pos(Tick t) { return t.getPrice() > 0; }

    // Two DISTINCT methods, not one used twice: the emitter names a stub after its method reference,
    // and one reference in both branches would collapse to a single node.
    public static boolean always(Tick t) { return t.getPrice() != Integer.MIN_VALUE; }

    public static boolean alsoAlways(Tick t) { return t.getPrice() != Integer.MIN_VALUE; }
    public static boolean neg(Tick t) { return t.getPrice() <= 0; }

    static void graph(EventProcessorConfig c) {
        String shape = System.getProperty("shape", "merge");
        if (Boolean.getBoolean("audit")) {
            // Exactly the configuration the spec's audit-path claim names.
            c.performanceProfile(EventProcessorConfig.PerformanceProfile.LOW_LATENCY_AUDIT);
            c.addLowLatencyEventLog(
                    com.telamin.fluxtion.runtime.audit.EventLogControlEvent.LogLevel.INFO,
                    EventProcessorConfig.AuditRecordFormat.BINARY);
        }
        switch (shape) {
            case "merge":
                subscribe(Tick.class).filter(GenShapes::pos)
                        .merge(subscribe(Tick.class).filter(GenShapes::neg))
                        .mapToInt(Tick::getPrice)
                        .aggregate(IntSumFlowFunction::new).id("total");
                break;
            case "mergeboth":
                // IDENTICAL topology to "merge" - same nodes, same wiring, same join - but BOTH
                // filters pass, so the merge takes two inputs per event instead of one. The delta
                // between the two is the cost of a second merge input, with nothing else moved. That
                // is the isolation the plain-chain comparison could not give: the merge shape carries
                // two filters and a second subscription the reference does not, so its ratio measured
                // a graph rather than an operator.
                subscribe(Tick.class).filter(GenShapes::always)
                        .merge(subscribe(Tick.class).filter(GenShapes::alsoAlways))
                        .mapToInt(Tick::getPrice)
                        .aggregate(IntSumFlowFunction::new).id("total");
                break;
            case "notify":
                subscribe(Tick.class).mapToInt(Tick::getPrice)
                        .aggregate(IntSumFlowFunction::new).id("total")
                        .notify(new Sink());
                break;
            case "maponnotify": {
                Sink sink = c.addNode(new Sink(), "sink");
                subscribe(Tick.class).mapToInt(Tick::getPrice)
                        .aggregate(IntSumFlowFunction::new).id("total")
                        .mapOnNotify(sink);
                break;
            }
            case "flatmap":
                // Three elements per event, so four graph cycles and four audit records for one
                // arrival - the shape the re-entrant clock sharing exists for.
                subscribe(Tick.class)
                        .flatMap(GenShapes::parts)
                        .mapToInt(String::length)
                        .aggregate(IntSumFlowFunction::new).id("total");
                break;
            case "plain":
                subscribe(Tick.class).mapToInt(Tick::getPrice)
                        .aggregate(IntSumFlowFunction::new).id("total");
                break;
            default:
                throw new IllegalArgumentException("unknown shape: " + shape);
        }
    }

    public static void main(String[] a) throws Exception {
        String target = System.getProperty("target");
        String dir = System.getProperty("outDir");
        System.setProperty("fluxtion.sourceGeneratorId", "cpp".equals(target) ? "cpp" : "local");
        EventProcessorFactory.compile(GenShapes::graph, cfg -> {
            cfg.setPackageName("app.gen");
            cfg.setClassName("ShapeProcessor");
            cfg.setOutputDirectory(dir);
            cfg.setResourcesOutputDirectory(dir);
            cfg.setWriteSourceToFile(true);
            cfg.setWriteGraphMlToFile(false);
            cfg.setCompileSource(false);
            cfg.setFormatSource(false);
        });
    }
}
