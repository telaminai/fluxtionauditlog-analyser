package com.acme.demo.builder;

import com.acme.demo.node.Nodes;
import com.acme.demo.replay.EventTypes;
import com.acme.demo.replay.ReplayCapture;
import com.telamin.fluxtion.builder.compile.config.FluxtionCompilerConfig;
import com.telamin.fluxtion.builder.compile.config.FluxtionGraphBuilder;
import com.telamin.fluxtion.builder.generation.config.EventProcessorConfig;

/**
 * The DEMO graph with a replay writer compiled in, so a run can be recorded and replayed
 * (spec-evidence-bundle-replay). The writer's codec is exactly the event types these nodes handle.
 */
public class DemoQuoteRecordedProcessorBuilder implements FluxtionGraphBuilder {

    @Override
    public void buildGraph(EventProcessorConfig cfg) {
        Nodes.PriceListener prices = new Nodes.PriceListener();
        Nodes.SpreadCalculator spread = new Nodes.SpreadCalculator(prices);
        Nodes.OrderTracker orders = new Nodes.OrderTracker();
        Nodes.QuotePublisher publisher = new Nodes.QuotePublisher(spread, orders);
        Nodes.RiskMonitor risk = new Nodes.RiskMonitor(orders, 2);
        Nodes.BreachHandler breaches = new Nodes.BreachHandler();
        cfg.addNode(prices, "priceListener");
        cfg.addNode(spread, "spreadCalculator");
        cfg.addNode(orders, "orderTracker");
        cfg.addNode(publisher, "quotePublisher");
        cfg.addNode(risk, "riskMonitor");
        cfg.addNode(breaches, "breachHandler");
        cfg.addEventAudit();
        cfg.addAuditor(new ReplayCapture()
                .handles(EventTypes.handledBy(prices, spread, orders, publisher, risk, breaches)), ReplayCapture.NAME);
    }

    @Override
    public void configureGeneration(FluxtionCompilerConfig cfg) {
        cfg.setClassName("DemoQuoteRecordedProcessor");
        cfg.setPackageName("com.acme.demo.generated");
        cfg.setGenerateDescription(true);
        cfg.setOutputDirectory("src/main/java");
    }
}
