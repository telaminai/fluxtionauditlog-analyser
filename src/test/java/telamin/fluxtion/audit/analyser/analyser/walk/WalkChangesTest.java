package telamin.fluxtion.audit.analyser.analyser.walk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Review PR57 R6, second round — the bulk paths' diff (see {@link WalkChanges}).
 *
 * <p>The interesting case is the one that must NOT be reported: an import that re-states a walk exactly as it
 * already was must not end a walk that is showing, for the same reason an unchanged save does not.
 */
class WalkChangesTest {

    private static WalkSpec walk(String name, String caption) {
        return new WalkSpec(name, "", "person", "", "", null, List.of(),
                List.of(new WalkSpec.Step(caption, WalkSpec.View.NONE,
                        List.of(new WalkSpec.Target("status", "", null)))), Map.of());
    }

    @Test
    @DisplayName("A definition replaced under the same name is reported, with its new version")
    void aReplacementIsReported() {
        var changes = WalkChanges.between(List.of(walk("w", "before")), List.of(walk("w", "after")));

        assertEquals(1, changes.size(), String.valueOf(changes));
        assertEquals("w", changes.get(0).name());
        assertEquals(walk("w", "after"), changes.get(0).now());
    }

    @Test
    @DisplayName("A walk that is gone is reported as gone, not as unchanged")
    void aRemovalIsReported() {
        var changes = WalkChanges.between(List.of(walk("w", "x")), List.of());

        assertEquals(1, changes.size(), String.valueOf(changes));
        assertEquals("w", changes.get(0).name());
        assertNull(changes.get(0).now(), "null is how the node is told a walk was removed");
    }

    @Test
    @DisplayName("An identical walk is NOT reported — a re-stated import must not end a showing walk")
    void anUnchangedWalkIsNotAChange() {
        assertEquals(List.of(), WalkChanges.between(List.of(walk("w", "x")), List.of(walk("w", "x"))));
    }

    @Test
    @DisplayName("Order does not make a change: the diff is by name, not by position")
    void reorderingIsNotAChange() {
        assertEquals(List.of(), WalkChanges.between(
                List.of(walk("a", "1"), walk("b", "2")), List.of(walk("b", "2"), walk("a", "1"))));
    }

    @Test
    @DisplayName("A rename reads as a removal and an addition — the node decides what that means")
    void aRenameIsBothHalves() {
        var changes = WalkChanges.between(List.of(walk("old", "x")), List.of(walk("new", "x")));

        assertEquals(2, changes.size(), String.valueOf(changes));
        assertEquals("new", changes.get(0).name(), "the added one first, in the new list's order");
        assertNotNull(changes.get(0).now());
        assertEquals("old", changes.get(1).name());
        assertNull(changes.get(1).now());
    }

    @Test
    @DisplayName("An empty or null list on either side is handled, and a new walk is an addition")
    void theEdges() {
        assertEquals(List.of(), WalkChanges.between(null, null));
        assertEquals(List.of(), WalkChanges.between(List.of(), List.of()));
        assertEquals(1, WalkChanges.between(null, List.of(walk("w", "x"))).size());
        assertEquals(1, WalkChanges.between(List.of(walk("w", "x")), null).size());
    }
}
