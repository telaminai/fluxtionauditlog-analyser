package telamin.fluxtion.audit.analyser.analyser.topology;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M27.1 — focus as a filter context: the focused subgraph becomes "the whole graph" for every
 * subsequent operation, contexts nest and only narrow, dimming and filtering never share an exit, and
 * out-of-context execution is reported rather than cropped.
 */
class FocusStackTest {

    /** a → b → c, plus d → c, and c → e: routes through c reach everything. */
    private static ProcessorTopology diamond() {
        var nodes = new LinkedHashMap<String, ProcessorTopology.Node>();
        for (String id : List.of("a", "b", "c", "d", "e")) {
            nodes.put(id, new ProcessorTopology.Node(id, "", "com.acme." + id, ProcessorTopology.Kind.NODE));
        }
        return new ProcessorTopology(nodes, List.of(
                new ProcessorTopology.Edge("1", "a", "b"),
                new ProcessorTopology.Edge("2", "b", "c"),
                new ProcessorTopology.Edge("3", "d", "c"),
                new ProcessorTopology.Edge("4", "c", "e")));
    }

    @Test
    void theWorldStartsAsTheFullGraphAndAContextBecomesTheWorld() {
        FocusStack f = new FocusStack(diamond());
        assertTrue(f.atFull());
        assertEquals(Set.of("a", "b", "c", "d", "e"), f.world());

        assertTrue(f.push(Set.of("a", "b", "c"), "abc"));
        assertFalse(f.atFull());
        assertEquals(Set.of("a", "b", "c"), f.world(), "the context IS the whole graph now");
    }

    @Test
    void contextsNestAndOnlyNarrow_outOfWorldIdsCannotBeSmuggledIn() {
        FocusStack f = new FocusStack(diamond());
        f.push(Set.of("a", "b", "c"), "abc");
        assertTrue(f.push(Set.of("b", "c", "e"), "attempt to widen"),
                "the in-world part (b,c) is a legitimate deeper context");
        assertEquals(Set.of("b", "c"), f.world(), "e is outside the parent context and must be dropped");
    }

    @Test
    void anEmptyOrFullyForeignPushIsRefused() {
        FocusStack f = new FocusStack(diamond());
        f.push(Set.of("a", "b"), "ab");
        assertFalse(f.push(Set.of("e"), "entirely outside"), "a world with nothing in it is never meant");
        assertFalse(f.push(Set.of(), "empty"));
        assertEquals(1, f.depth());
    }

    @Test
    void allMeansAllOfThisContext_notTheFullGraph() {
        FocusStack f = new FocusStack(diamond());
        f.push(Set.of("a", "b", "c"), "abc");
        assertEquals(Set.of("a", "b", "c"),
                f.expandInWorld(Set.of("b"), TopologyFocus.Scope.ALL),
                "'whole graph' inside a context is the context");
    }

    @Test
    void routesAreConfinedToTheContext_pathsThatLeaveAndReenterDoNotCount() {
        // context {a, c, e}: b is excluded, so a's only route to c (via b) leaves the world.
        // Routes of a within the context must NOT include c or e.
        FocusStack f = new FocusStack(diamond());
        f.push(Set.of("a", "c", "e"), "ace");
        assertEquals(Set.of("a"), f.expandInWorld(Set.of("a"), TopologyFocus.Scope.ROUTES),
                "a reaches c only through b, which is outside the context — the route does not exist here");
        assertEquals(Set.of("c", "e"), f.expandInWorld(Set.of("c"), TopologyFocus.Scope.ROUTES),
                "c → e is an in-context edge and survives");
    }

    @Test
    void neighboursAreConfinedToo() {
        FocusStack f = new FocusStack(diamond());
        f.push(Set.of("b", "c", "e"), "bce");
        assertEquals(Set.of("c", "b", "e"),
                f.expandInWorld(Set.of("c"), TopologyFocus.Scope.NEIGHBOURS),
                "c's neighbours are b, d, e — d is outside the context and must not appear");
    }

    @Test
    void popIsOneLevel_popToFullClearsEverything_popToTargetsADepth() {
        FocusStack f = new FocusStack(diamond());
        f.push(Set.of("a", "b", "c"), "abc");
        f.push(Set.of("b", "c"), "bc");
        assertEquals(2, f.depth());

        assertTrue(f.pop());
        assertEquals(Set.of("a", "b", "c"), f.world(), "pop returns to the PARENT context, not to full");

        f.push(Set.of("b", "c"), "bc again");
        f.popTo(1);
        assertEquals(1, f.depth());
        f.popToFull();
        assertTrue(f.atFull());
        assertFalse(f.pop(), "nothing left to pop");
    }

    @Test
    void executionOutsideTheContextIsReportedNotCropped() {
        FocusStack f = new FocusStack(diamond());
        f.push(Set.of("b", "c"), "bc");
        assertEquals(Set.of("a", "e"), f.outsideWorld(List.of("a", "b", "c", "e")),
                "a cycle that ran through a and e must be reported as outside this view");
        f.popToFull();
        assertTrue(f.outsideWorld(List.of("a", "b")).isEmpty(), "at full graph nothing is outside");
    }

    @Test
    void theBreadcrumbNamesEveryLevelWithItsSize() {
        FocusStack f = new FocusStack(diamond());
        assertEquals("All (5)", f.breadcrumb());
        f.push(Set.of("a", "b", "c"), "hedge path");
        f.push(Set.of("b", "c"), "neighbours of b");
        assertEquals("All (5) ▸ hedge path (3) ▸ neighbours of b (2)", f.breadcrumb());
    }
}
