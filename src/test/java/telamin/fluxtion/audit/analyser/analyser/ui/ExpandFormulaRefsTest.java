package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** {@link GraphPanel#expandFormulaRefs} — formulas referencing other formulas' labels. */
class ExpandFormulaRefsTest {

    private static final Set<String> KEYS = Set.of("ask.price", "bid.price");

    @Test
    void bareLabelExpandsParenthesised() {
        Map<String, String> derived = Map.of("spread", "ask.price - bid.price");
        assertEquals("(ask.price - bid.price) * 2",
                GraphPanel.expandFormulaRefs("spread * 2", derived, KEYS));
    }

    @Test
    void backtickedLabelWithSpacesExpands() {
        Map<String, String> derived = Map.of("quoted spread", "ask.price - bid.price");
        assertEquals("abs((ask.price - bid.price))",
                GraphPanel.expandFormulaRefs("abs(`quoted spread`)", derived, KEYS));
    }

    @Test
    void chainedLabelsExpandAcrossPasses() {
        Map<String, String> derived = new LinkedHashMap<>();
        derived.put("spread", "ask.price - bid.price");
        derived.put("halfSpread", "spread / 2");
        assertEquals("((ask.price - bid.price) / 2) + 1",
                GraphPanel.expandFormulaRefs("halfSpread + 1", derived, KEYS));
    }

    @Test
    void labelNeverReplacesInsideALongerToken() {
        Map<String, String> derived = Map.of("price", "1");
        assertEquals("ask.price - bid.price",
                GraphPanel.expandFormulaRefs("ask.price - bid.price", derived, KEYS));
    }

    @Test
    void realKeyWinsOverAFormulaLabelOfTheSameName() {
        Map<String, String> derived = Map.of("ask.price", "bid.price * 2");
        assertEquals("ask.price", GraphPanel.expandFormulaRefs("ask.price", derived, KEYS));
    }

    @Test
    void referenceCycleTerminates() {
        Map<String, String> derived = new LinkedHashMap<>();
        derived.put("a", "b + 1");
        derived.put("b", "a + 1");
        // must terminate (pass cap); the leftover ref then fails Expr.parse with unknown-key
        String out = GraphPanel.expandFormulaRefs("a", derived, KEYS);
        assertNotNull(out);
    }
}
