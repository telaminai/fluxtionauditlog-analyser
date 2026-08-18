package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.config.GraphSpec;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The synchronous half of M29.2's panel wiring — spec state, replace semantics, and the
 * persistable-mutation contract. Loading and painting are async/Swing and live on the eyeball list;
 * the loader itself is fully covered by {@code ExternalCsvLoaderTest}.
 */
class GraphExternalSeriesTest {

    private static GraphSpec.ExternalSpec spec(String label) {
        return new GraphSpec.ExternalSpec("/tmp/x.csv", label, "ts", "epochMillis", null, "mid", 0);
    }

    @Test
    void setExternalReplacesTheSetAndFiresTheMutationContract() {
        GraphPanel panel = new GraphPanel();
        int[] mutations = {0};
        panel.setOnMutation(() -> mutations[0]++);

        panel.setExternal(List.of(spec("venue mid")));
        assertEquals(1, panel.externalSpecs().size());
        assertEquals(1, mutations[0], "external series are persistable state — B-M20-3's contract applies");

        panel.setExternal(List.of());
        assertTrue(panel.externalSpecs().isEmpty(), "present-means-replace, like guides and bands");
        assertEquals(2, mutations[0]);
    }

    @Test
    void externalSpecsAreImmutableSnapshots() {
        GraphPanel panel = new GraphPanel();
        panel.setExternal(new java.util.ArrayList<>(List.of(spec("a"))));
        assertThrows(UnsupportedOperationException.class, () -> panel.externalSpecs().add(spec("b")));
    }
}
