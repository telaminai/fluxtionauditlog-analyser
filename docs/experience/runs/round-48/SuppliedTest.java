package com.acme.app;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * SUPPLIED. Do not modify. It drives your Main against the sample scenario and asserts the
 * business requirements from the outside, so it works whatever design you chose.
 */
class SuppliedTest {

    static final String[] FIGURES = {
        "marketdata.mid","marketdata.depth","marketdata.vol","marketdata.ewma",
        "pricing.adjusted","pricing.spread","liquidity.book","liquidity.score",
        "risk.notional","risk.exposure","risk.var",
        "capital.charge","capital.buffer","capital.fee"
    };

    static String run() throws Exception {
        Path audit = Files.createTempFile("audit", ".txt");
        Path alerts = Files.createTempFile("alerts", ".txt");
        Main.main(new String[]{"sample-scenario.txt", audit.toString(), alerts.toString()});
        return Files.readString(audit);
    }

    @Test void allFourteenFiguresAppear() throws Exception {
        String log = run();
        for (String f : FIGURES) assertTrue(log.contains(f), "missing figure: " + f);
    }

    @Test void theEngineProducesAnAuditTrail() throws Exception {
        assertFalse(run().isBlank(), "audit trail is empty");
    }

    @Test void anUnownedConfigKeyProducesNoWork() throws Exception {
        String log = run();
        int i = log.indexOf("someKeyNoVendorOwns");
        assertTrue(i > 0, "the sample scenario's stray config key never reached the engine");
        String after = log.substring(i, Math.min(log.length(), i + 400));
        assertFalse(after.contains("stage: marketdata.mid"),
                "a config key no vendor owns caused work downstream");
    }
}
