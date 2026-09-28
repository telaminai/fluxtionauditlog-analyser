package com.acme.demo.builder;

import com.acme.demo.event.Events;
import com.acme.demo.node.Nodes;
import com.acme.demo.replay.ReplayCapture;
import com.telamin.fluxtion.builder.compile.config.FluxtionCompilerConfig;
import com.telamin.fluxtion.builder.compile.config.FluxtionGraphBuilder;
import com.telamin.fluxtion.builder.generation.config.EventProcessorConfig;

/** R1: the DEMO graph with ReplayCapture compiled in; risk limit 2 (Capture). */
public class DemoQuoteCaptureProcessorBuilder implements FluxtionGraphBuilder {
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
        cfg.addAuditor(new ReplayCapture().inputs(Events.MarketDataEvent.class, Events.OrderUpdateEvent.class), ReplayCapture.NAME);
    }

    @Override
    public void configureGeneration(FluxtionCompilerConfig cfg) {
        cfg.setClassName("DemoQuoteCaptureProcessor");
        cfg.setPackageName("com.acme.demo.generated");
        cfg.setGenerateDescription(true);
        cfg.setOutputDirectory("src/main/java");
    }
}
