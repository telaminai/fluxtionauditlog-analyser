package app;

import com.telamin.fluxtion.builder.compile.generation.EventProcessorFactory;
import com.telamin.fluxtion.builder.generation.config.EventProcessorConfig;
import com.telamin.fluxtion.runtime.flowfunction.aggregate.function.primitive.IntSumFlowFunction;
import com.telamin.fluxtion.runtime.flowfunction.groupby.GroupBy;

import static com.telamin.fluxtion.builder.DataFlowBuilder.subscribe;

/**
 * groupBy over a key whose CARDINALITY is set by the event stream, emitted for both targets.
 *
 * <p>The shape exists to answer one question the 6.4x groupBy figure could not: that figure was
 * measured over four keys, against a C++ store that was a linear scan. Four is far to the left of any
 * crossover, so the number said more about the benchmark than about the target.
 *
 * <p>Runs on DEFAULT, not LOWEST_LATENCY. Both arms use the same profile, so the comparison holds, but
 * the absolute numbers are not comparable to the plain chain's.
 */
public class GenGroupBy {
    public static class Tick {
        public int price;
        public int key;
        public int getPrice() { return price; }
        public int getKey() { return key; }
    }

    /** Reads ONE key out of the store, so the chain ends in an int the harness can checksum. */
    public static int keyZeroTotal(GroupBy<Integer, Integer> groupBy) {
        Integer v = groupBy.toMap().get(0);
        return v == null ? 0 : v;
    }

    static void graph(EventProcessorConfig c) {
        subscribe(Tick.class)
                .groupBy(Tick::getKey, Tick::getPrice, IntSumFlowFunction::new)
                .mapToInt(GenGroupBy::keyZeroTotal).id("total");
    }

    public static void main(String[] a) throws Exception {
        String target = System.getProperty("target");
        String dir = System.getProperty("outDir");
        System.setProperty("fluxtion.sourceGeneratorId", "cpp".equals(target) ? "cpp" : "local");
        EventProcessorFactory.compile(GenGroupBy::graph, cfg -> {
            cfg.setPackageName("app.gen");
            cfg.setClassName("GroupByProcessor");
            cfg.setOutputDirectory(dir);
            cfg.setResourcesOutputDirectory(dir);
            cfg.setWriteSourceToFile(true);
            cfg.setWriteGraphMlToFile(false);
            cfg.setCompileSource(false);
            cfg.setFormatSource(false);
        });
    }
}
