package com.example.riskflow;

import com.example.riskflow.builder.RiskGraphBuilder;
import com.example.riskflow.event.OrderEvent;
import com.example.riskflow.event.OrderEvent.Side;
import com.example.riskflow.event.PriceUpdate;
import com.example.riskflow.node.ExposureCalculator;
import com.example.riskflow.node.MarketData;
import com.example.riskflow.node.OrderIntake;
import com.example.riskflow.node.PositionBook;
import com.example.riskflow.node.RiskAlerter;
import com.fluxtion.compiler.Fluxtion;
import com.fluxtion.runtime.EventProcessor;
import com.fluxtion.runtime.output.SinkPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Expected values are calculated by hand from the scenario, not read back from the graph:
 * exposure = |net position| x mid, mid = (bid + ask) / 2.
 */
class RiskGraphTest {

    private static final double SYMBOL_LIMIT = 2_000_000;
    private static final double TOTAL_LIMIT = 3_000_000;
    private static final double TOLERANCE = 1e-9;

    private ExposureCalculator exposure;
    private PositionBook positions;
    private EventProcessor<?> flow;
    private final List<String> alerts = new ArrayList<>();

    @BeforeEach
    void buildGraph() {
        // compileDispatcher: the references below ARE the dispatch targets, so assertions read live state.
        MarketData marketData = new MarketData();
        OrderIntake orderIntake = new OrderIntake();
        positions = new PositionBook(orderIntake);
        exposure = new ExposureCalculator(positions, marketData);
        RiskAlerter alerter = new RiskAlerter(
                exposure, new SinkPublisher<>(RiskGraphBuilder.ALERT_SINK), SYMBOL_LIMIT, TOTAL_LIMIT);

        flow = Fluxtion.compileDispatcher(cfg -> cfg.addNode(alerter));
        alerts.clear();
        flow.addSink(RiskGraphBuilder.ALERT_SINK, (String alert) -> alerts.add(alert));
        flow.init();
    }

    @Test
    void positionIsNettedAcrossOrders() {
        flow.onEvent(new PriceUpdate("AAPL", 189.50, 190.50));
        flow.onEvent(new OrderEvent("o1", "AAPL", Side.BUY, 5000));
        flow.onEvent(new OrderEvent("o2", "AAPL", Side.SELL, 2000));

        assertEquals(3000, positions.positionFor("AAPL"));
        // 3000 x mid 190.00
        assertEquals(570_000d, exposure.exposureFor("AAPL"), TOLERANCE);
        assertEquals(570_000d, exposure.getTotalExposure(), TOLERANCE);
        assertTrue(alerts.isEmpty(), "570k is under both limits");
    }

    @Test
    void priceMoveAloneRevaluesTheBook() {
        flow.onEvent(new PriceUpdate("MSFT", 410.00, 411.00));
        flow.onEvent(new OrderEvent("o1", "MSFT", Side.BUY, 3000));
        assertEquals(1_231_500d, exposure.getTotalExposure(), TOLERANCE); // 3000 x 410.50

        flow.onEvent(new PriceUpdate("MSFT", 419.00, 421.00));
        assertEquals(1_260_000d, exposure.getTotalExposure(), TOLERANCE); // 3000 x 420.00, no new order
    }

    @Test
    void positionWithoutAPriceIsExcludedFromExposure() {
        flow.onEvent(new OrderEvent("o1", "GOOG", Side.BUY, 1000));

        assertEquals(1000, positions.positionFor("GOOG"));
        assertEquals(0d, exposure.getTotalExposure(), TOLERANCE);
        assertTrue(alerts.isEmpty());
    }

    @Test
    void shortPositionsAddToExposure() {
        flow.onEvent(new PriceUpdate("AAPL", 199.50, 200.50));
        flow.onEvent(new OrderEvent("o1", "AAPL", Side.SELL, 4000));

        assertEquals(-4000, positions.positionFor("AAPL"));
        assertEquals(800_000d, exposure.exposureFor("AAPL"), TOLERANCE);
    }

    @Test
    void alertsFireOnLimitTransitionsOnly() throws java.io.IOException {
        List<Object> scenario = EventFileReader.read(Path.of("data/market-events.csv"));
        scenario.forEach(flow::onEvent);

        assertEquals(List.of(
                "BREACH  AAPL    exposure=2,200,000.00 limit=2,000,000.00",
                "BREACH  *TOTAL* exposure=3,431,500.00 limit=3,000,000.00",
                "CLEARED AAPL    exposure=1,980,000.00 limit=2,000,000.00",
                "CLEARED *TOTAL* exposure=2,801,000.00 limit=3,000,000.00",
                "BREACH  GOOG    exposure=2,800,000.00 limit=2,000,000.00",
                "BREACH  *TOTAL* exposure=5,601,000.00 limit=3,000,000.00",
                "CLEARED GOOG    exposure=0.00 limit=2,000,000.00",
                "CLEARED *TOTAL* exposure=2,801,000.00 limit=3,000,000.00"),
                alerts);

        assertEquals(11_000, positions.positionFor("AAPL"));
        assertEquals(2_000, positions.positionFor("MSFT"));
        assertEquals(0, positions.positionFor("GOOG"));
        assertEquals(2_801_000d, exposure.getTotalExposure(), TOLERANCE);
    }
}
