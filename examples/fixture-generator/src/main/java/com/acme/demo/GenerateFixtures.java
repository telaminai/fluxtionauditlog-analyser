package com.acme.demo;

import com.acme.demo.node.Nodes;
import com.fluxtion.compiler.EventProcessorConfig;
import com.fluxtion.compiler.Fluxtion;
import com.fluxtion.compiler.FluxtionCompilerConfig;
import com.fluxtion.runtime.EventProcessor;
import com.fluxtion.runtime.audit.EventLogControlEvent;
import com.fluxtion.runtime.time.ClockStrategy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Regenerates the analyser's topology test fixtures: a processor's {@code .graphml} and an audit log
 * from running that same processor.
 *
 * <p><b>Pairing is the point.</b> Both artefacts come from one invocation of one graph, so the instance
 * ids in the log are exactly the node ids in the graph. Hand-editing either would reintroduce the drift
 * the analyser's build-mismatch warning exists to catch — and which is invisible on screen.
 *
 * <p><b>Why a plain {@code main} rather than {@code fluxtion-maven-plugin}.</b> The plugin is the right
 * way to build a Fluxtion <em>application</em>, and its {@code scan} goal is bound to
 * {@code process-classes} so the builder is compiled before it runs. But that goal calls a hosted
 * source-generation service and needs an API key at build time. These fixtures must be regenerable by
 * anyone with a checkout and no credentials, so this calls the compiler directly:
 * {@link Fluxtion#compile} both writes the description and hands back a live processor, which is then
 * fed events — no second compile pass, no generated sources to build.
 */
public final class GenerateFixtures {

    /** Where the analyser keeps the fixtures, relative to this module. */
    private static final Path FIXTURES = Path.of("../../src/test/resources/topology");

    /** A fixed instant so regeneration is byte-reproducible: 2026-01-01T09:00:00Z. */
    private static final long FIXED_START_MILLIS = 1_767_258_000_000L;

    private static final Path EMITTED_GRAPHML =
            Path.of("target/generated-resources/com/acme/demo/generated/DemoQuoteProcessor.graphml");

    public static void main(String[] args) throws Exception {
        EventProcessor<?> processor = Fluxtion.compile(GenerateFixtures::graph, GenerateFixtures::generation);

        StringBuilder log = new StringBuilder();
        processor.init();
        // Pin the clock. Without it every regeneration rewrites the fixture with new timestamps, so a
        // genuine change to the graph is buried in noise and nobody re-runs this.
        long[] tick = {FIXED_START_MILLIS};
        processor.onEvent(ClockStrategy.registerClockEvent(() -> tick[0] += 10));
        processor.setAuditLogProcessor(record -> log.append("---\n").append(record).append('\n'));
        processor.setAuditLogLevel(EventLogControlEvent.LogLevel.INFO);

        // a few cycles down both branches, so there is something with shape to step through
        processor.onEvent(new Nodes.MarketDataEvent("DEMO-A", 100.10, 100.30));
        processor.onEvent(new Nodes.OrderUpdateEvent("ord-1", "LIVE"));
        processor.onEvent(new Nodes.MarketDataEvent("DEMO-A", 100.12, 100.28));
        processor.onEvent(new Nodes.OrderUpdateEvent("ord-1", "DONE"));
        processor.onEvent(new Nodes.MarketDataEvent("DEMO-B", 55.01, 55.09));

        Files.createDirectories(FIXTURES);
        Files.writeString(FIXTURES.resolve("demo-quote-audit.yaml"), log.toString());
        copyGraphMl();
        System.out.println("fixtures written to " + FIXTURES.toAbsolutePath().normalize());
    }

    /**
     * The graph. {@code spreadCalculator} deliberately does <b>not</b> extend {@code EventLogNode}, so it
     * runs on every market-data cycle and never writes an audit entry — the case the analyser's topology
     * view must not report as "did not run".
     */
    static void graph(EventProcessorConfig cfg) {
        Nodes.PriceListener prices = new Nodes.PriceListener();
        Nodes.SpreadCalculator spread = new Nodes.SpreadCalculator(prices);
        Nodes.OrderTracker orders = new Nodes.OrderTracker();
        Nodes.QuotePublisher publisher = new Nodes.QuotePublisher(spread, orders);
        // names become the instanceIds in nodeLogs, and the node ids in the graphml
        cfg.addNode(prices, "priceListener");
        cfg.addNode(spread, "spreadCalculator");
        cfg.addNode(orders, "orderTracker");
        cfg.addNode(publisher, "quotePublisher");
        cfg.addEventAudit();
    }

    static void generation(FluxtionCompilerConfig cfg) {
        cfg.setClassName("DemoQuoteProcessor");
        cfg.setPackageName("com.acme.demo.generated");
        cfg.setGenerateDescription(true);      // this is what emits the .graphml
        cfg.setWriteSourceToFile(true);
        cfg.setOutputDirectory("target/generated-sources/fluxtion");
        cfg.setResourcesOutputDirectory("target/generated-resources");
    }

    private static void copyGraphMl() throws IOException {
        if (!Files.exists(EMITTED_GRAPHML)) {
            throw new IllegalStateException("no .graphml at " + EMITTED_GRAPHML.toAbsolutePath()
                    + " — setGenerateDescription(true) is what produces it");
        }
        Files.copy(EMITTED_GRAPHML, FIXTURES.resolve("demo-quote-processor.graphml"),
                StandardCopyOption.REPLACE_EXISTING);
    }
}
