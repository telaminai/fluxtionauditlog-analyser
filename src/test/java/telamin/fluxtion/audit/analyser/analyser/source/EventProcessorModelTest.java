package telamin.fluxtion.audit.analyser.analyser.source;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EventProcessorModelTest {

    private static final String SRC = """
            package com.acme.marketmaker.strategy;

            import com.acme.tradecalculator.api.lib.node.marketdata.MarketDataBookNode;
            import java.util.Map;

            public class DemoMarketMakerStrategy {
              private final transient MarketDataBookNode hedgeRateSource = new MarketDataBookNode();
              public final transient Clock clock = new Clock();
              private final Map<String, Object> ctx = new HashMap<>();
              private MakerConfigNode makerContext = new MakerConfigNode();

              public boolean handleEvent(Object e) {
                int localVar = 3;          // no modifier -> not a field
                return true;
              }
            }
            """;

    @Test
    void extractsFieldInstanceIdsNotLocals() {
        EventProcessorModel m = EventProcessorModel.parse("com.acme.marketmaker.strategy.DemoMarketMakerStrategy", SRC);
        assertTrue(m.hasInstance("hedgeRateSource"));
        assertTrue(m.hasInstance("clock"));
        assertTrue(m.hasInstance("ctx"));
        assertTrue(m.hasInstance("makerContext"));
        assertFalse(m.hasInstance("localVar"), "method locals are not fields");
    }

    @Test
    void resolvesTypesViaImportsSamePackageAndStripsGenerics() {
        EventProcessorModel m = EventProcessorModel.parse("com.acme.marketmaker.strategy.DemoMarketMakerStrategy", SRC);
        assertEquals("com.acme.tradecalculator.api.lib.node.marketdata.MarketDataBookNode",
                m.fieldTypeFqn("hedgeRateSource"), "resolved via import");
        assertEquals("com.acme.marketmaker.strategy.Clock",
                m.fieldTypeFqn("clock"), "unimported type -> same-package guess");
        assertEquals("java.util.Map", m.fieldTypeFqn("ctx"), "generics stripped, resolved via import");
        assertNull(m.fieldTypeFqn("unknownNode"));
    }

    @Test
    void coverageCountsMatchingInstanceIds() {
        EventProcessorModel m = EventProcessorModel.parse("x.Y", SRC);
        assertEquals(2, m.coverage(Set.of("hedgeRateSource", "clock", "notThere")));
    }
}
