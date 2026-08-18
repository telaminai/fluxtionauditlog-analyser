package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Natural ordering for the topology index — the owner ask: file each category alphabetically, reading
 * numbers in an instance id as numbers.
 */
class NaturalOrderTest {

    private static List<String> sorted(String... ids) {
        List<String> out = new ArrayList<>(List.of(ids));
        out.sort(NaturalOrder.ID);
        return out;
    }

    /** The whole point: lexicographic order files CHILL-10 before CHILL-2. */
    @Test
    void digitRunsCompareByValueNotByText() {
        assertEquals(List.of("CHILL-1", "CHILL-2", "CHILL-9", "CHILL-10", "CHILL-24"),
                sorted("CHILL-10", "CHILL-2", "CHILL-24", "CHILL-1", "CHILL-9"));
    }

    @Test
    void plainNamesStillSortAlphabetically() {
        assertEquals(List.of("breachHandler", "orderTracker", "priceListener", "quotePublisher"),
                sorted("quotePublisher", "priceListener", "breachHandler", "orderTracker"));
    }

    /** Mixed families interleave correctly rather than clustering by digit count. */
    @Test
    void familiesGroupAndThenCountUp() {
        assertEquals(List.of("till-2", "till-11", "zone-1", "zone-10"),
                sorted("zone-10", "till-11", "zone-1", "till-2"));
    }

    @Test
    void caseIsIgnoredForOrderingButNeverCollapses() {
        assertEquals(List.of("alpha", "Beta"), sorted("Beta", "alpha"));
        assertNotEquals(0, NaturalOrder.compare("Node1", "node1"),
                "ids differing only in case must not compare EQUAL — a set-backed list could drop one");
    }

    /** Embedded numbers anywhere, not just at the end. */
    @Test
    void numbersInTheMiddleAreRead() {
        assertEquals(List.of("zone2Rollup", "zone10Rollup"), sorted("zone10Rollup", "zone2Rollup"));
    }

    /**
     * Leading zeros carry no VALUE, so node-007 files between node-6 and node-8 rather than in a
     * separate zero-padded family. It does not compare EQUAL to node-7 though: these are two distinct
     * ids, and a comparator that collapsed them could let a set-backed list drop one — the same
     * totality requirement as the case tie-break.
     */
    @Test
    void leadingZerosDoNotChangeValueButStillOrderTotally() {
        assertEquals(List.of("node-6", "node-007", "node-8"), sorted("node-8", "node-6", "node-007"));
        assertNotEquals(0, NaturalOrder.compare("node-007", "node-7"),
                "value-equal but textually different ids must still order deterministically");
        assertEquals(List.of("node-007", "node-7"), sorted("node-7", "node-007"),
                "and they sort ADJACENTLY — same value, so nothing comes between them");
    }

    /**
     * A digit run can be longer than any integer type — a build stamp, a nanosecond id. Parsing would
     * overflow into a WRONG answer; this compares by significant-digit length instead.
     */
    @Test
    void hugeDigitRunsDoNotOverflow() {
        String big = "build-20260818120000000000";
        String bigger = "build-20260818120000000001";
        assertTrue(NaturalOrder.compare(big, bigger) < 0, "must not overflow to an arbitrary answer");
        assertEquals(List.of("build-9", big, bigger), sorted(bigger, big, "build-9"));
    }

    @Test
    void nullsAreOrderedRatherThanThrowing() {
        assertEquals(0, NaturalOrder.compare(null, null));
        assertTrue(NaturalOrder.compare(null, "a") < 0);
        assertTrue(NaturalOrder.compare("a", null) > 0);
    }

    /** A prefix sorts before the longer id that extends it. */
    @Test
    void aPrefixComesFirst() {
        assertEquals(List.of("node", "node1", "nodeAlpha"), sorted("nodeAlpha", "node1", "node"));
    }
}
