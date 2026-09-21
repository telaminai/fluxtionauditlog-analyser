package telamin.fluxtion.audit.analyser.analyser.graph;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.model.KV;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LiteralFormulaTest {
    @Test void textEqualityKeepsBooleanAndTextDistinctAndMissingUnknown() {
        var k = new GraphKey("n", "v");
        var e = Expr.parse("n.v == \"true\"").newEvaluator();
        assertEquals(1, e.eval(1, Map.of(k, new KV("v", "true", true))));
        assertEquals(0, e.eval(1, Map.of(k, new KV("v", "true", false))));
        assertEquals(0, e.eval(1, Map.of(k, new KV("v", "false", true))));
        assertTrue(Double.isNaN(e.eval(1, Map.of())));
        assertEquals(1, Expr.parse("n.v == false").newEvaluator().eval(1, Map.of(k, new KV("v", "false"))));
        assertEquals(1, Expr.parse("n.v == true").newEvaluator().eval(1, Map.of(k, new KV("v", "true"))));
    }
    @Test void textOnlyPermitsEqualityAndDurationsKeepTheirWindowMeaning() {
        for (String bad : new String[]{"n.v > \"true\"", "\"true\" + 1", "abs(\"text\")"})
            assertThrows(IllegalArgumentException.class, () -> Expr.parse(bad));
        assertFalse(Expr.parse("mean(n.v,\"5s\")").windowFunctions().isEmpty());
        assertEquals(1, Expr.parse("n.v == \"5s\"").newEvaluator().eval(1,
                Map.of(new GraphKey("n", "v"), new KV("v", "5s", true))));
        assertEquals(1, Expr.parse("n.v == \"a\\\"b\"").newEvaluator().eval(1,
                Map.of(new GraphKey("n", "v"), new KV("v", "a\"b", true))));
    }
}
