package com.acme;

import com.acme.generated.AppProcessor;
import com.vendor.Tick;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.regex.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The consumer's whole test. Five vendor subsystems, delivered as jars with no source, composed by a
 * bean file that declares no events and no order.
 */
class CompositionTest {

    private String cycle(Tick t) {
        AppProcessor flow = new AppProcessor();
        List<String> audit = new ArrayList<>();
        flow.setAuditLogProcessor(r -> audit.add(r.toString()));
        flow.init();
        flow.onEvent(t);
        flow.tearDown();
        return audit.stream().filter(r -> r.contains("Tick")).findFirst().orElseThrow();
    }

    private List<String> stages(String record) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("stage: ([a-z.]+)").matcher(record);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    @Test void everySubsystemRunsOnceAndDependenciesComeFirst() {
        List<String> s = stages(cycle(new Tick("S", 100.0, 102.0)));
        assertEquals(7, s.size(), "seven stages: " + s);
        assertEquals(new HashSet<>(s).size(), s.size(), "each once: " + s);
        assertTrue(s.indexOf("risk.notional")    > s.indexOf("marketdata.mid"));
        assertTrue(s.indexOf("pricing.adjusted") > s.indexOf("marketdata.depth"));
        assertTrue(s.indexOf("liquidity.score")  > s.indexOf("pricing.adjusted"));
        assertTrue(s.indexOf("risk.exposure")    > s.indexOf("liquidity.score"));
        assertTrue(s.indexOf("capital.charge")   > s.indexOf("risk.exposure"));
    }

    /** The property no atomic composition can produce: one vendor split across two others. */
    @Test void riskIsSplitAcrossOtherVendorsSubsystems() {
        List<String> s = stages(cycle(new Tick("S", 100.0, 102.0)));
        int early = s.indexOf("risk.notional"), late = s.indexOf("risk.exposure");
        int other = s.indexOf("liquidity.score");
        assertTrue(early < other && other < late,
                "liquidity must run between risk's two stages, so risk cannot be run as a unit: " + s);
    }

    @Test void theValuesAreRight() {
        String c = cycle(new Tick("S", 100.0, 102.0));
        assertTrue(c.contains("101.0"),    "mid 101: " + c);
        assertTrue(c.contains("200.0"),    "depth 200: " + c);
        assertTrue(c.contains("101000.0"), "notional 101000: " + c);
        assertTrue(c.contains("103.02"),   "adjusted 103.02: " + c);
    }
}
