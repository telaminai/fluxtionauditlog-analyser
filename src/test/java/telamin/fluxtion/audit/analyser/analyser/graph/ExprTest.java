package telamin.fluxtion.audit.analyser.analyser.graph;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The derived-series expression engine (spec-graph-artifacts §B): parse + eval, precedence, ASCII and
 * Unicode operators, functions, div-by-zero / missing-ref → NaN, and parse errors that name the failing
 * ref / point at the token.
 */
class ExprTest {

    private static final GraphKey AB = new GraphKey("askMakerOrder", "price");
    private static final GraphKey CD = new GraphKey("bidMakerOrder", "price");
    private static final Set<GraphKey> KNOWN = Set.of(AB, CD);

    private static double eval(String expr, Map<GraphKey, Double> vals) {
        return Expr.parse(expr, KNOWN).eval(vals);
    }

    @Test
    void arithmeticAndPrecedence() {
        assertEquals(14.0, Expr.parse("2 + 3 * 4", Set.of()).eval(Map.of()), 1e-9);
        assertEquals(20.0, Expr.parse("(2 + 3) * 4", Set.of()).eval(Map.of()), 1e-9);
        assertEquals(-3.0, Expr.parse("-5 + 2", Set.of()).eval(Map.of()), 1e-9);
        assertEquals(2.0, Expr.parse("8 / 2 / 2", Set.of()).eval(Map.of()), 1e-9, "left-associative");
    }

    @Test
    void acceptsUnicodeOperators() {
        // − U+2212, × U+00D7, ÷ U+00F7 — an LLM will emit these
        assertEquals(1.0, Expr.parse("3 − 2", Set.of()).eval(Map.of()), 1e-9);
        assertEquals(6.0, Expr.parse("2 × 3", Set.of()).eval(Map.of()), 1e-9);
        assertEquals(3.0, Expr.parse("6 ÷ 2", Set.of()).eval(Map.of()), 1e-9);
    }

    @Test
    void functions() {
        assertEquals(5.0, Expr.parse("abs(0 - 5)", Set.of()).eval(Map.of()), 1e-9);
        assertEquals(3.0, Expr.parse("max(1, 2, 3)", Set.of()).eval(Map.of()), 1e-9);
        assertEquals(2.0, Expr.parse("min(4, 2)", Set.of()).eval(Map.of()), 1e-9);
        assertEquals(7.0, Expr.parse("abs(-3) + max(1, 4)", Set.of()).eval(Map.of()), 1e-9);
    }

    @Test
    void resolvesRefsAndEvaluatesTheSpread() {
        Expr e = Expr.parse("askMakerOrder.price - bidMakerOrder.price", KNOWN);
        assertEquals(Set.of(AB, CD), e.refs());
        assertEquals(0.09, eval("askMakerOrder.price - bidMakerOrder.price",
                Map.of(AB, 20.082, CD, 19.992)), 1e-9);
    }

    @Test
    void missingRefYieldsNaNSoThePointIsOmitted() {
        assertTrue(Double.isNaN(eval("askMakerOrder.price - bidMakerOrder.price", Map.of(AB, 20.0))),
                "a ref not in the values → NaN → omitted point");
    }

    // ---- conditionals (M28.1) ------------------------------------------------------------------

    @Test
    void comparisonsYieldOneOrZero_andNaNStaysUnknown() {
        assertEquals(1.0, Expr.parse("3 > 2", Set.of()).eval(Map.of()), 1e-9);
        assertEquals(0.0, Expr.parse("2 > 3", Set.of()).eval(Map.of()), 1e-9);
        assertEquals(1.0, Expr.parse("2 >= 2", Set.of()).eval(Map.of()), 1e-9);
        assertEquals(1.0, Expr.parse("2 <= 3", Set.of()).eval(Map.of()), 1e-9);
        assertEquals(1.0, Expr.parse("2 == 2", Set.of()).eval(Map.of()), 1e-9);
        assertEquals(1.0, Expr.parse("2 != 3", Set.of()).eval(Map.of()), 1e-9);
        // a comparison against a missing ref is unknown, not false
        assertTrue(Double.isNaN(eval("askMakerOrder.price > 2", Map.of())));
    }

    @Test
    void comparisonsAcceptUnicodeForms() {
        assertEquals(1.0, Expr.parse("3 ≥ 3", Set.of()).eval(Map.of()), 1e-9);
        assertEquals(1.0, Expr.parse("2 ≤ 3", Set.of()).eval(Map.of()), 1e-9);
        assertEquals(1.0, Expr.parse("2 ≠ 3", Set.of()).eval(Map.of()), 1e-9);
    }

    @Test
    void twoArgIfDefaultsElseToNoPoint() {
        // THE feature (spec M28 §C): a false condition yields NaN → the point is simply not plotted
        String gate = "if(askMakerOrder.price - bidMakerOrder.price > 0.004, askMakerOrder.price)";
        assertEquals(5.0, eval(gate, Map.of(AB, 5.0, CD, 4.0)), 1e-9, "in breach → plot f(x)");
        assertTrue(Double.isNaN(eval(gate, Map.of(AB, 5.0, CD, 4.999))), "not in breach → no point");
    }

    @Test
    void threeArgIfSelectsAndNaNConditionNeverPicksABranch() {
        assertEquals(7.0, Expr.parse("if(1 > 0, 7, 9)", Set.of()).eval(Map.of()), 1e-9);
        assertEquals(9.0, Expr.parse("if(1 < 0, 7, 9)", Set.of()).eval(Map.of()), 1e-9);
        assertTrue(Double.isNaN(eval("if(askMakerOrder.price > 0, 7, 9)", Map.of())),
                "an unknowable condition must not silently pick a branch");
    }

    @Test
    void andOrNotWithNaNPoisoning() {
        assertEquals(1.0, Expr.parse("and(1 > 0, 2 > 1)", Set.of()).eval(Map.of()), 1e-9);
        assertEquals(0.0, Expr.parse("and(1 > 0, 1 > 2)", Set.of()).eval(Map.of()), 1e-9);
        assertEquals(1.0, Expr.parse("or(1 > 2, 2 > 1)", Set.of()).eval(Map.of()), 1e-9);
        assertEquals(0.0, Expr.parse("or(1 > 2, 2 > 3)", Set.of()).eval(Map.of()), 1e-9);
        assertEquals(0.0, Expr.parse("not(1 > 0)", Set.of()).eval(Map.of()), 1e-9);
        assertEquals(1.0, Expr.parse("not(0)", Set.of()).eval(Map.of()), 1e-9);
        assertTrue(Double.isNaN(eval("and(1 > 0, askMakerOrder.price > 0)", Map.of())), "NaN poisons and()");
        assertTrue(Double.isNaN(eval("or(2 > 1, askMakerOrder.price > 0)", Map.of())), "NaN poisons or()");
    }

    @Test
    void comparisonsNestInsideArithmeticAndCalls() {
        assertEquals(5.0, Expr.parse("(3 > 2) * 5", Set.of()).eval(Map.of()), 1e-9);
        assertEquals(1.0, Expr.parse("min(3 > 2, 9)", Set.of()).eval(Map.of()), 1e-9);
    }

    @Test
    void chainedComparisonsAreRefusedWithGuidance() {
        var ex = assertThrows(IllegalArgumentException.class, () -> Expr.parse("1 < 2 < 3", Set.of()));
        assertTrue(ex.getMessage().contains("and("), "the error must teach the and(...) form");
    }

    @Test
    void conditionalArityAndBareEqualsAreClearErrors() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> Expr.parse("if(1)", Set.of()))
                .getMessage().contains("if()"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> Expr.parse("not(1, 2)", Set.of()))
                .getMessage().contains("not()"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> Expr.parse("and(1)", Set.of()))
                .getMessage().contains("and()"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> Expr.parse("2 = 3", Set.of()))
                .getMessage().contains("=="), "bare '=' must suggest '=='");
    }

    @Test
    void compatibilityGuardrail_variadicMinMaxAndRefsNamedIfAreUntouched() {
        // the review REJECTED overloading min/max for windows precisely to keep this true forever
        assertEquals(2.0, Expr.parse("min(4, 2)", Set.of()).eval(Map.of()), 1e-9);
        // 'if' is only a function when followed by '(' — a node with that instanceId keeps working
        GraphKey ifKey = new GraphKey("if", "x");
        assertEquals(3.0, Expr.parse("if.x + 1", Set.of(ifKey)).eval(Map.of(ifKey, 2.0)), 1e-9);
    }

    @Test
    void divisionByZeroIsNaNNotInfinity() {
        assertTrue(Double.isNaN(Expr.parse("1 / 0", Set.of()).eval(Map.of())));
    }

    @Test
    void unknownRefNamesTheRefAndSuggestsTheNearest() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> Expr.parse("askMakerOrder.prce - bidMakerOrder.price", KNOWN));
        assertTrue(ex.getMessage().contains("askMakerOrder.prce"), "names the failing ref");
        assertTrue(ex.getMessage().contains("askMakerOrder.price"), "suggests the nearest known key");
    }

    @Test
    void parseErrorsPointAtTheToken() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> Expr.parse("2 +", Set.of()))
                .getMessage().toLowerCase().contains("end"), "trailing operator → unexpected end");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> Expr.parse("2 + )", Set.of()))
                .getMessage().contains("')'"), "names the offending token");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> Expr.parse("(2 + 3", Set.of()))
                .getMessage().contains("expected ')'"), "unbalanced paren");
    }

    @Test
    void fromDisplaySplitsOnTheFirstDot() {
        assertEquals(new GraphKey("a", "b"), GraphKey.fromDisplay("a.b"));
        assertEquals(new GraphKey("a", "b.c"), GraphKey.fromDisplay("a.b.c"), "key keeps later dots");
        assertNull(GraphKey.fromDisplay("nodot"));
        assertNull(GraphKey.fromDisplay("trailing."));
    }

    @Test
    void parseWithoutKnownSetBuildsRefsBySplitting() {
        Expr e = Expr.parse("a.b.c - d.e");   // no known-key set → refs by split-on-first-dot
        assertEquals(Set.of(new GraphKey("a", "b.c"), new GraphKey("d", "e")), e.refs());
        assertEquals(3.0, e.eval(Map.of(new GraphKey("a", "b.c"), 5.0, new GraphKey("d", "e"), 2.0)), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> Expr.parse("foo + 1"),
                "a single-segment token is not a key reference");
    }

    @Test
    void wrongArgCountIsRejected() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> Expr.parse("abs(1, 2)", Set.of()))
                .getMessage().contains("abs"));
    }
}
