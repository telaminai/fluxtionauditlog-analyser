package telamin.fluxtion.audit.analyser.analyser.graph;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The W0 mirror evaluator (spec-expr-conditionals-windows M28.2): per-scan, position-keyed. The
 * semantics themselves are pinned by {@link ExprTest} running entirely through evaluators; this class
 * pins the properties the refactor exists for.
 */
class EvaluatorTest {

    @Test
    void statelessExpressionsProveIt() {
        // stateSlotCount()==0 is the checkable proof a formula carries no window state — every
        // pre-M28.3 expression must satisfy it
        for (String expr : new String[]{"1 + 2 * 3", "abs(-1)", "min(4, 2)",
                "if(1 > 0, 7, 9)", "and(1, or(0, 1))", "not(0)"}) {
            assertEquals(0, Expr.parse(expr, Set.of()).newEvaluator().stateSlotCount(),
                    expr + " is stateless and must compile with zero state slots");
        }
    }

    @Test
    void eachScanGetsItsOwnEvaluator() {
        // one AST, two scans — the mirrors must be distinct objects, because window state (M28.3)
        // lives in the mirror and a shared one would bleed history across scans
        Expr e = Expr.parse("1 + 2", Set.of());
        assertNotSame(e.newEvaluator(), e.newEvaluator());
    }

    @Test
    void evaluatorMatchesTheOldEngineOnTheOddCases() {
        // the exact edge semantics the old Expr.eval carried, now answered by the mirror
        Evaluator ev = Expr.parse("1 / 0", Set.of()).newEvaluator();
        assertTrue(Double.isNaN(ev.eval(0, Map.of())), "div-by-zero is no-point, not Infinity");
        GraphKey k = new GraphKey("a", "x");
        assertTrue(Double.isNaN(Expr.parse("a.x + 1").newEvaluator().eval(0, Map.of())),
                "missing ref propagates NaN");
        assertEquals(3.0, Expr.parse("a.x + 1").newEvaluator().eval(0, Map.of(k, 2.0)), 1e-9);
    }
}
