package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * #73: the recent evidence bundles have to be tellable apart. Headless — the naming is a pure function,
 * deliberately, so it is tested without a frame.
 */
class StartPanelNamesTest {

    @Test
    @DisplayName("two bundles with the same file name are widened until they differ")
    void sameFileNameIsWidened() {
        List<String> names = StartPanel.distinctNames(List.of(
                "/w/scratch-demo/bundles/recorded-run.fexp",
                "/w/replay-divergence-demo/recorded-run.fexp"));

        assertEquals(List.of("bundles/recorded-run.fexp", "replay-divergence-demo/recorded-run.fexp"),
                names, "collidingNamesAreWidenedByOneSegment");
    }

    @Test
    @DisplayName("a name that is already unique stays short")
    void uniqueNamesAreNotWidened() {
        List<String> names = StartPanel.distinctNames(List.of(
                "/w/one/alpha.fexp", "/w/two/beta.fexp"));

        assertEquals(List.of("alpha.fexp", "beta.fexp"), names, "noNeedlessWidening");
    }

    @Test
    @DisplayName("widening continues past a shared parent")
    void widensPastASharedParent() {
        List<String> names = StartPanel.distinctNames(List.of(
                "/w/a/bundles/run.fexp", "/w/b/bundles/run.fexp"));

        assertEquals(List.of("a/bundles/run.fexp", "b/bundles/run.fexp"), names,
                "widensUntilTheyActuallyDiffer");
    }

    @Test
    @DisplayName("one bundle needs no context at all")
    void singleEntry() {
        assertEquals(List.of("run.fexp"), StartPanel.distinctNames(List.of("/w/a/run.fexp")));
    }
}
