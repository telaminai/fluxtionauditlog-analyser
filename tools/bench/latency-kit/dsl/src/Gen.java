package app;

import com.telamin.fluxtion.builder.compile.generation.EventProcessorFactory;
import com.telamin.fluxtion.builder.generation.config.EventProcessorConfig;
import com.telamin.fluxtion.runtime.flowfunction.aggregate.function.primitive.IntSumFlowFunction;
import java.util.function.Consumer;
import static com.telamin.fluxtion.builder.DataFlowBuilder.subscribe;

/** map -> map -> filter -> aggregate, the DSL's natural deep chain, emitted for both targets. */
public class Gen {
    public static class Tick { public int price; public int getPrice() { return price; } }
    public static int twice(int v) { return v * 2; }
    public static int plusOne(int v) { return v + 1; }
    public static boolean positive(int v) { return v > 0; }

    static void graph(EventProcessorConfig c) {
        subscribe(Tick.class)
                .mapToInt(Tick::getPrice)
                .map(Gen::twice)
                .map(Gen::plusOne)
                .filter(Gen::positive)
                .aggregate(IntSumFlowFunction::new).id("total");
        if (Boolean.getBoolean("lowest")) {
            c.performanceProfile(EventProcessorConfig.PerformanceProfile.LOWEST_LATENCY);
        }
    }

    public static void main(String[] a) throws Exception {
        String target = System.getProperty("target");
        String dir = System.getProperty("outDir");
        if ("cpp".equals(target)) {
            System.setProperty("fluxtion.sourceGeneratorId", "cpp");
        } else {
            System.setProperty("fluxtion.sourceGeneratorId", "local");
        }
        EventProcessorFactory.compile(Gen::graph, cfg -> {
            cfg.setPackageName("app.gen");
            cfg.setClassName("DslProcessor");
            cfg.setOutputDirectory(dir);
            cfg.setResourcesOutputDirectory(dir);
            cfg.setWriteSourceToFile(true);
            cfg.setWriteGraphMlToFile(false);
            cfg.setFormatSource(false);
            cfg.setCompileSource(false);
            cfg.generateReachabilityMetadata(true);
        });
        System.out.println("GENERATED " + target);
    }
}
