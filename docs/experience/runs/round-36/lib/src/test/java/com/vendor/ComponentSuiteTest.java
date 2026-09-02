package com.vendor;

import com.vendor.generated.AppProcessor;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * COMPONENT VALIDATION — run before any of this is handed to an author.
 *
 * <p>Proves each vendor component is a functioning Fluxtion graph and that the seven stages compose
 * into one correct dispatch. Without this, an integration failure downstream is ambiguous: you cannot
 * tell a broken component from a broken integration.
 */
class ComponentSuiteTest {

    private List<String> run(Tick... ticks) {
        AppProcessor flow = new AppProcessor();
        List<String> audit = new ArrayList<>();
        flow.setAuditLogProcessor(r -> audit.add(r.toString()));
        flow.init();
        for (Tick t : ticks) flow.onEvent(t);
        flow.tearDown();
        return audit;
    }

    private List<String> stagesOf(String record) {
        List<String> out = new ArrayList<>();
        var m = java.util.regex.Pattern.compile("stage: ([a-z.]+)").matcher(record);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    @Test void everyStageRunsOnceAndInDependencyOrder() {
        List<String> audit = run(new Tick("S", 100.0, 102.0));
        String cycle = audit.stream().filter(r -> r.contains("Tick")).findFirst().orElseThrow();
        List<String> stages = stagesOf(cycle);
        assertEquals(7, stages.size(), "seven stages, each once:\n" + stages);
        assertEquals(new HashSet<>(stages).size(), stages.size(), "no stage twice:\n" + stages);
        // the constraint that matters: every stage after its dependencies
        assertTrue(stages.indexOf("risk.notional")   > stages.indexOf("pricing.mid"));
        assertTrue(stages.indexOf("pricing.adjusted")> stages.indexOf("liquidity.depth"));
        assertTrue(stages.indexOf("liquidity.score") > stages.indexOf("pricing.adjusted"));
        assertTrue(stages.indexOf("risk.exposure")   > stages.indexOf("liquidity.score"));
        assertTrue(stages.indexOf("capital.charge")  > stages.indexOf("risk.exposure"));
    }

    @Test void theArithmeticIsWhatTheVendorsSay() {
        List<String> audit = run(new Tick("S", 100.0, 102.0));
        String c = audit.stream().filter(r -> r.contains("Tick")).findFirst().orElseThrow();
        // mid 101, depth 200, notional 101000, adjusted 101*(1+200/10000)=103.02,
        // score 10.302, exposure 101000*(1+10.302/1000)=102040.502, charge *0.08
        assertTrue(c.contains("101.0"),    "mid:\n" + c);
        assertTrue(c.contains("200.0"),    "depth:\n" + c);
        assertTrue(c.contains("101000.0"), "notional:\n" + c);
        assertTrue(c.contains("103.02"),   "adjusted:\n" + c);
    }
}
