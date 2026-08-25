package com.acme.demo.builder;

import com.acme.demo.node.Nodes;
import com.telamin.fluxtion.builder.compile.config.FluxtionCompilerConfig;
import com.telamin.fluxtion.builder.compile.config.FluxtionGraphBuilder;
import com.telamin.fluxtion.builder.generation.config.EventProcessorConfig;

/**
 * Authors the dispatch graph. Implementing {@code FluxtionGraphBuilder} is what lets the
 * {@code fluxtion-maven-plugin} find and invoke it at build time.
 *
 * <pre>
 * MarketDataEvent → priceListener → spreadCalculator → quotePublisher ← orderTracker ← OrderUpdateEvent
 *                   (logs)          (SILENT)           (logs)
 * </pre>
 */
public class DemoQuoteProcessorBuilder implements FluxtionGraphBuilder {

    @Override
    public void buildGraph(EventProcessorConfig cfg) {
        Nodes.PriceListener prices = new Nodes.PriceListener();
        Nodes.SpreadCalculator spread = new Nodes.SpreadCalculator(prices);
        Nodes.OrderTracker orders = new Nodes.OrderTracker();
        Nodes.QuotePublisher publisher = new Nodes.QuotePublisher(spread, orders);
        // raises an event on the graph itself when the order book gets too long
        Nodes.RiskMonitor risk = new Nodes.RiskMonitor(orders, 2);
        Nodes.BreachHandler breaches = new Nodes.BreachHandler();
        // the names become the instanceIds in nodeLogs, and the node ids in the graphml
        cfg.addNode(prices, "priceListener");
        cfg.addNode(spread, "spreadCalculator");
        cfg.addNode(orders, "orderTracker");
        cfg.addNode(publisher, "quotePublisher");
        cfg.addNode(risk, "riskMonitor");
        cfg.addNode(breaches, "breachHandler");
        // No level argument. addEventAudit(LogLevel.INFO) additionally traces every node invocation
        // (thread + method per node), which would make every executed node appear — the opposite of the
        // case these fixtures exist to capture, and unlike the production logs the analyser reads.
        cfg.addEventAudit();
    }

    @Override
    public void configureGeneration(FluxtionCompilerConfig cfg) {
        cfg.setClassName("DemoQuoteProcessor");
        cfg.setPackageName("com.acme.demo.generated");
        cfg.setGenerateDescription(true);   // emits the .graphml the analyser reads
        // Generated source is checked in, as the starter does. It means anyone can regenerate the audit
        // log from a bare checkout; only regenerating the GRAPH needs the compiler (and its API key).
        cfg.setOutputDirectory("src/main/java");
    }
}
