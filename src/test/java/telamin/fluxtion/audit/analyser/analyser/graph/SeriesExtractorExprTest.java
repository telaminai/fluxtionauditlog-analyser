package telamin.fluxtion.audit.analyser.analyser.graph;

import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Derived-series extraction (spec-graph-artifacts §B.2/§B.5): row-order LOCF vs strict, and the carry rule
 * — finite updates, explicit NaN clears (no fabricated continuity), absent leaves unchanged.
 */
class SeriesExtractorExprTest {

    private static final GraphKey A = new GraphKey("nodeA", "price");
    private static final GraphKey B = new GraphKey("nodeB", "price");
    private static final Set<GraphKey> KNOWN = Set.of(A, B);

    // nodeA: 10 → 12 → NaN(cancel) → 14 ;  nodeB: 8 (once, then absent — carried)
    private static HeapLogStore store() {
        String log =
                rec(1, "    - nodeA: { price: 10.0}\n    - nodeB: { price: 8.0}\n")
              + rec(2, "    - nodeA: { price: 12.0}\n")
              + rec(3, "    - nodeA: { price: NaN}\n")
              + rec(4, "    - nodeA: { price: 14.0}\n");
        return new HeapLogStore(log);
    }

    private static String rec(int t, String nodeLogs) {
        return "---\n#00:00:0" + t + ".000 [th] INFO L\neventLogRecord:\n  logTime: " + t
                + "\n  event: E\n  nodeLogs:\n" + nodeLogs;
    }

    private Series locf(String expr, Set<GraphKey> known) {
        return SeriesExtractor.extractExpr(store(), new FilterState(),
                Expr.parse(expr, known), expr, false, SeriesExtractor.Resolve.LOCF);
    }

    @Test
    void locfCarriesAcrossRecordsAndNanClearsTheCarry() {
        Series s = locf("nodeA.price - nodeB.price", KNOWN);
        // row1: 10-8=2 ; row2: 12-8=4 (B carried) ; row3: nodeA NaN → carry cleared → point OMITTED ;
        // row4: 14-8=6 (nodeA re-finite, B still carried)
        assertEquals(3, s.size(), "the NaN record produces no point — no fabricated continuity");
        assertArrayEquals(new long[]{1, 2, 4}, new long[]{s.x(0), s.x(1), s.x(2)}, "no point at the NaN record (t=3)");
        assertEquals(2.0, s.y(0), 1e-9);
        assertEquals(4.0, s.y(1), 1e-9);
        assertEquals(6.0, s.y(2), 1e-9);
        assertEquals("nodeA.price - nodeB.price", s.label());
    }

    @Test
    void strictRequiresCoOccurrenceInOneRecord() {
        Series s = SeriesExtractor.extractExpr(store(), new FilterState(),
                Expr.parse("nodeA.price - nodeB.price", KNOWN), "spread", false, SeriesExtractor.Resolve.STRICT);
        // only row1 has both nodeA and nodeB present-and-finite
        assertEquals(1, s.size(), "strict: only the co-occurring record yields a point");
        assertEquals(1L, s.x(0));
        assertEquals(2.0, s.y(0), 1e-9);
    }

    @Test
    void aRefThatNeverAppearsOmitsEveryPoint() {
        GraphKey never = new GraphKey("never", "x");
        Series s = locf("nodeA.price - never.x", Set.of(A, never));
        assertEquals(0, s.size(), "a missing ref → NaN → every point omitted");
    }

    @Test
    void resolveExistingFindsAKeyThatOnlyFiresLate() {
        // nodeA in every record; 'late.x' only in the last — a front-truncated discover would miss it
        String log = rec(1, "    - nodeA: { price: 1.0}\n")
                   + rec(2, "    - nodeA: { price: 2.0}\n")
                   + rec(3, "    - nodeA: { price: 3.0}\n")
                   + rec(4, "    - nodeA: { price: 4.0}\n    - late: { x: 9.0}\n");
        HeapLogStore st = new HeapLogStore(log);
        Set<String> found = SeriesExtractor.resolveExisting(st, Set.of("nodeA.price", "late.x", "typo.z"));
        assertTrue(found.contains("nodeA.price"));
        assertTrue(found.contains("late.x"), "a key that first fires in the last record still resolves");
        assertFalse(found.contains("typo.z"), "a non-existent key is not resolved");
    }

    @Test
    void singleRefLocfMatchesEveryFiniteObservation() {
        Series s = locf("nodeA.price", Set.of(A));
        // 10, 12, (NaN omitted), 14
        assertEquals(3, s.size());
        assertArrayEquals(new long[]{1, 2, 4}, new long[]{s.x(0), s.x(1), s.x(2)});
        assertEquals(10.0, s.y(0), 1e-9);
        assertEquals(14.0, s.y(2), 1e-9);
    }
}
