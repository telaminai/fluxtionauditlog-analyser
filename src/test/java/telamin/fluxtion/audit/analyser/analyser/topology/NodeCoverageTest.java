package telamin.fluxtion.audit.analyser.analyser.topology;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Coverage for a graph (M24.1) — raised by 54 dead nodes in a 275-node estate. */
class NodeCoverageTest {

    @Test
    void separatesCoveredFromNeverLogged() {
        NodeCoverage c = NodeCoverage.of(
                Set.of("a", "b", "c", "d"), Set.of("a", "c"), Set.of());
        assertEquals(List.of("a", "c"), c.covered().stream().sorted().toList());
        assertEquals(List.of("b", "d"), c.uncovered().stream().sorted().toList());
        assertEquals(0.5, c.ratio(), 1e-9);
        assertEquals(4, c.declaredCount());
    }

    /**
     * A node that cannot log must not be reported as uncovered. One line of noise per silent node in a
     * 300-node report is how a report gets ignored, and an ignored report is worse than none.
     */
    @Test
    void nodesThatCannotLogAreNotCountedAgainstCoverage() {
        NodeCoverage c = NodeCoverage.of(
                Set.of("a", "b", "quiet"), Set.of("a"), Set.of("quiet"));
        assertEquals(List.of("quiet"), c.silentByDesign());
        assertEquals(List.of("b"), c.uncovered());
        assertEquals(0.5, c.ratio(), 1e-9, "the silent node is out of the denominator entirely");
        assertEquals(3, c.declaredCount());
    }

    /**
     * The reverse direction is a different and worse fault: an instanceId in the log that the graph does
     * not contain means the graphml is from another build, and then no figure on screen can be trusted.
     */
    @Test
    void anInstanceIdAbsentFromTheTopologyIsABuildMismatch() {
        NodeCoverage c = NodeCoverage.of(Set.of("a"), Set.of("a", "ghost"), Set.of());
        assertTrue(c.buildMismatch());
        assertEquals(List.of("ghost"), c.loggedButNotInTopology());
    }

    @Test
    void fullCoverageAndTheEmptyCase() {
        assertEquals(1.0, NodeCoverage.of(Set.of("a"), Set.of("a"), Set.of()).ratio(), 1e-9);
        NodeCoverage empty = NodeCoverage.of(Set.of(), Set.of(), Set.of());
        assertEquals(1.0, empty.ratio(), 1e-9, "nothing declared cannot be under-covered");
        assertFalse(empty.buildMismatch());
    }

    /** The real shape: a fifth of an estate dead because the harness could not reach it. */
    @Test
    void theCaseThisWasBuiltFor() {
        Set<String> declared = new java.util.LinkedHashSet<>();
        for (int i = 1; i <= 24; i++) declared.add("chiller_" + i);
        Set<String> logged = Set.of("chiller_1", "chiller_13");   // i%12 emitter against a 24-wide estate

        NodeCoverage c = NodeCoverage.of(declared, logged, Set.of());
        assertEquals(22, c.uncovered().size());
        assertEquals(0.083, Math.round(c.ratio() * 1000) / 1000.0, 1e-9);
    }
}
