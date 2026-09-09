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

    public static boolean pos(Tick t) { return t.getPrice() > 0; }
    public static boolean neg(Tick t) { return t.getPrice() <= 0; }

    static void graph(EventProcessorConfig c) {
        String shape = System.getProperty("shape", "merge");
        switch (shape) {
            case "merge":
                subscribe(Tick.class).filter(GenShapes::pos)
                        .merge(subscribe(Tick.class).filter(GenShapes::neg))
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
