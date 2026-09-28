package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.parse.ProducerDiagnostics;
import telamin.fluxtion.audit.analyser.analyser.parse.TimeOrderReport;
import telamin.fluxtion.audit.analyser.analyser.session.view.IdentityBannerView;
import telamin.fluxtion.audit.analyser.analyser.session.view.StatusLineView;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * readable-surfaces step 1: the view models are published in the snapshot, and therefore in {@code context}.
 *
 * <p>The snapshot already said what the session KNOWS. These say what each surface was last TOLD to state, which is
 * a different fact — and the tests that matter here are the ones where the two disagree. A reader of the snapshot
 * could not previously tell "the session knows 30 records" from "the person is being shown 30 records".
 *
 * <p>Published, deliberately, as the record the backends were handed — not recomputed. A second computation beside
 * the node would be the {@code MainFrame.renderLogEvidence} copy again, in a new place.
 */
class PublishedSurfacesTest {

    private static SessionDriver opened(FakeSessionAdapter a, int total) {
        SessionDriver d = new SessionDriver(a);
        SessionFixtures.openLog(d, a, "/logs/f.yaml", "DEMO", Set.of("a"), 1, total, "TRACE");
        return d;
    }

    private static void scanLands(SessionDriver d, int records) {
        long g = d.snapshot().logGeneration();
        d.post(new SessionEvents.LogShapeObserved(g, records, 1_000L, 5_000L, false, 0, 0));
        d.post(new SessionEvents.ProducerFindingsObserved(g, ProducerDiagnostics.clean()));
        d.post(new SessionEvents.TimeOrderObserved(g, TimeOrderReport.clean()));
    }

    @Test
    @DisplayName("The snapshot publishes exactly the view the backends were handed")
    void theSnapshotCarriesWhatWasStated() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a, 25);
        scanLands(d, 25);

        StatusLineView published = d.snapshot().statusLine();

        assertNotNull(published);
        assertSame(a.statusLines.get(a.statusLines.size() - 1), published,
                "the same object, not an equal one: a recomputation beside the node is the duplicate this "
                        + "whole design exists to remove");
        assertEquals(25, published.records());
    }

    @Test
    @DisplayName("Before anything has been stated, the surface fields are null — not an empty view")
    void nothingStatedIsNotAnEmptyStatement() {
        assertNull(SessionSnapshot.EMPTY.statusLine());
        assertNull(SessionSnapshot.EMPTY.identityBanner());

        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a, 25);

        assertNull(d.snapshot().statusLine(),
                "the scan is outstanding, so the line has stated nothing — an agent must be able to tell that "
                        + "from a line stating zero records");
    }

    // ---- the point of the exercise: KNOWS and STATES come apart ---------------------------------

    @Test
    @DisplayName("While a consistency gate holds a view back, the snapshot says what is SHOWN, not what is known")
    void whatIsKnownAndWhatIsShownDiverge() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a, 25);
        scanLands(d, 25);

        // an append lands; the store has not been re-scanned, so the line's gate holds the new count back
        d.post(new SessionEvents.LogAppended(d.snapshot().logGeneration(), Set.of("a"), 1, 30, "TRACE"));

        SessionSnapshot snap = d.snapshot();
        assertEquals(30, snap.total(), "the session KNOWS 30");
        assertEquals(25, snap.statusLine().records(),
                "and the person is being SHOWN 25 — the gate is doing its job, and until now nothing published "
                        + "that fact to a reader of the snapshot or of context");

        scanLands(d, 30);
        assertEquals(30, d.snapshot().statusLine().records(), "the re-scan settles it and the two agree again");
    }

    @Test
    @DisplayName("A closed log states nothing, even though the session still answers questions about it")
    void closingClearsWhatIsStated() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a, 25);
        scanLands(d, 25);
        assertNotNull(d.snapshot().statusLine());

        d.post(new SessionEvents.LogCleared(d.snapshot().logGeneration()));

        assertNull(d.snapshot().statusLine(), "no log, nothing stated");
        assertNull(d.snapshot().identityBanner());
    }

    // ---- the banner: 'no banner' is a decision, and it is now legible --------------------------

    @Test
    @DisplayName("A banner the session decided NOT to draw is published as shown=false, not as absent")
    void notDrawnIsNotTheSameAsNoVerdict() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a, 25);

        IdentityBannerView banner = d.snapshot().identityBanner();

        assertNotNull(banner, "the banner element HAS stated something: that there is nothing to warn about");
        assertFalse(banner.shown(),
                "and an agent can now distinguish 'the session decided no banner belongs' from 'no element ran'");
    }

    @Test
    @DisplayName("fields() is the same vocabulary the audit log uses for the same render")
    void contextAndTheAuditNameTheSameThings() {
        IdentityBannerView view = IdentityBannerView.of(7, "REPLACEMENT", "a different file", true);

        assertEquals(view.fields().keySet(), view.changedFrom(null).keySet(),
                "context publishes fields(); the audit records changedFrom(). A reader correlating the log with a "
                        + "context payload must not have to translate between two sets of names");
        assertEquals(java.util.List.of("generation", "verdict", "reason", "shown"),
                java.util.List.copyOf(view.fields().keySet()));
    }
}
