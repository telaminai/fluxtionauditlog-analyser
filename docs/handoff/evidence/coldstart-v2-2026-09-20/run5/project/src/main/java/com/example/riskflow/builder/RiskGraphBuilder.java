package com.example.riskflow.builder;

import com.example.riskflow.node.ExposureCalculator;
import com.example.riskflow.node.MarketData;
import com.example.riskflow.node.OrderIntake;
import com.example.riskflow.node.PositionBook;
import com.example.riskflow.node.RiskAlerter;
import com.fluxtion.compiler.EventProcessorConfig;
import com.fluxtion.compiler.FluxtionCompilerConfig;
import com.fluxtion.compiler.FluxtionGraphBuilder;
import com.fluxtion.runtime.audit.EventLogControlEvent.LogLevel;
import com.fluxtion.runtime.output.SinkPublisher;

/**
 * The graph, declared by constructor wiring:
 *
 * <pre>
 *   PriceUpdate ─▶ MarketData ──────────────┐
 *                                           ├─▶ ExposureCalculator ─▶ RiskAlerter ─▶ "alerts" sink
 *   OrderEvent  ─▶ OrderIntake ─▶ PositionBook ┘
 * </pre>
 */
public class RiskGraphBuilder implements FluxtionGraphBuilder {

    public static final String ALERT_SINK = "alerts";
    public static final String POSITION_BOOK = "positionBook";
    public static final String EXPOSURE_CALCULATOR = "exposureCalculator";

    private final double symbolLimit;
    private final double totalLimit;

    public RiskGraphBuilder(double symbolLimit, double totalLimit) {
        this.symbolLimit = symbolLimit;
        this.totalLimit = totalLimit;
    }

    @Override
    public void buildGraph(EventProcessorConfig cfg) {
        MarketData marketData = new MarketData();
        OrderIntake orderIntake = new OrderIntake();
        PositionBook positionBook = new PositionBook(orderIntake);
        ExposureCalculator exposureCalculator = new ExposureCalculator(positionBook, marketData);
        RiskAlerter riskAlerter = new RiskAlerter(
                exposureCalculator, new SinkPublisher<>(ALERT_SINK), symbolLimit, totalLimit);

        cfg.addNode(positionBook, POSITION_BOOK);
        cfg.addNode(exposureCalculator, EXPOSURE_CALCULATOR);
        cfg.addNode(riskAlerter); // terminal pulls in its ancestors via ctor refs
        cfg.addEventAudit(LogLevel.INFO);
    }

    @Override
    public void configureGeneration(FluxtionCompilerConfig cfg) {
        cfg.setClassName("RiskDispatcher");
        cfg.setPackageName("com.example.riskflow.generated");
        cfg.setOutputDirectory("target/generated-sources/fluxtion");
    }
}
