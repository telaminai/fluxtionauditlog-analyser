package com.acme.demo.builder;

import com.acme.demo.node.Nodes;
import com.telamin.fluxtion.builder.compile.config.FluxtionCompilerConfig;
import com.telamin.fluxtion.builder.compile.config.FluxtionGraphBuilder;
import com.telamin.fluxtion.builder.generation.config.EventProcessorConfig;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent;

/**
 * Authors the dispatch graph. Implementing {@code FluxtionGraphBuilder} is what lets the
 * {@code fluxtion-maven-plugin} find and invoke it at build time.
 *
 * <pre>
 * MarketDataEvent → priceListener → spreadCalculator → quotePublisher ← orderTracker ← OrderUpdateEvent
 *                   (logs)          (SILENT)           (logs)
 * </pre>
 */
public class DemoQuoteTracedProcessorBuilder implements FluxtionGraphBuilder {

    @Override
    public void buildGraph(EventProcessorConfig cfg) {
        Nodes.PriceListener prices = new Nodes.PriceListener();
        Nodes.SpreadCalculator spread = new Nodes.SpreadCalculator(prices);
        Nodes.OrderTracker orders = new Nodes.OrderTracker();
        Nodes.QuotePublisher publisher = new Nodes.QuotePublisher(spread, orders);
        // the names become the instanceIds in nodeLogs, and the node ids in the graphml
        cfg.addNode(prices, "priceListener");
        cfg.addNode(spread, "spreadCalculator");
        cfg.addNode(orders, "orderTracker");
        cfg.addNode(publisher, "quotePublisher");
        // WITH a level: compiles node-invocation tracing into the processor, so every node that runs
        // logs a thread + method entry whether or not it makes auditLog calls of its own. The runtime
        // level then gates it. This is the regime where absence from the log really does mean the node
        // did not run — the analyser detects it and says so.
        cfg.addEventAudit(EventLogControlEvent.LogLevel.TRACE);
    }

    @Override
    public void configureGeneration(FluxtionCompilerConfig cfg) {
        cfg.setClassName("DemoQuoteTracedProcessor");
        cfg.setPackageName("com.acme.demo.generated");
        cfg.setGenerateDescription(true);   // emits the .graphml the analyser reads
        // Generated source is checked in, as the starter does. It means anyone can regenerate the audit
        // log from a bare checkout; only regenerating the GRAPH needs the compiler (and its API key).
        cfg.setOutputDirectory("src/main/java");
    }
}
