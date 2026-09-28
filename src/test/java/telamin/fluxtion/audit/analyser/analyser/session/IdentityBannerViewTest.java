package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.view.IdentityBannerView;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * View-model spike, second element: the file-identity banner as a node.
 *
 * <p>Three surfaces state this verdict. Before this they were hand-fed from {@code onSessionSnapshot}, and two of
 * them asked the third whether to draw at all. The decision is now the session's, made once.
 */
class IdentityBannerViewTest {

    private static SessionDriver driver(FakeSessionAdapter a) {
        SessionDriver d = new SessionDriver(a, new SessionAuditSink());
        SessionFixtures.openLog(d, a, "/logs/f.yaml", "DEMO", Set.of("a"), 1, 25, "TRACE");
        return d;
    }

    @Test
    @DisplayName("A verdict that warns is drawn once, and the answer names all its backends")
    void aWarningVerdictIsDrawn() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        int before = a.identityBanners.size();

        d.post(new SessionEvents.LogIdentityObserved(d.snapshot().logGeneration(), "REPLACEMENT",
                "the file was replaced on disk"));

        assertEquals(before + 1, a.identityBanners.size(), "one view");
        IdentityBannerView v = a.identityBanners.get(a.identityBanners.size() - 1);
        assertEquals("REPLACEMENT", v.verdict());
        assertTrue(v.shown(), "the POLICY is on the view — no surface decides this any more");
        assertEquals("the file was replaced on disk", v.reason());
    }

    @Test
    @DisplayName("A verdict that does not warn is still a view — it is how a banner is taken DOWN")
    void aClearVerdictIsAlsoAView() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        long g = d.snapshot().logGeneration();
        d.post(new SessionEvents.LogIdentityObserved(g, "REPLACEMENT", "replaced"));
        int after = a.identityBanners.size();

        d.post(new SessionEvents.LogIdentityObserved(g, "VERIFIED", null));

        assertEquals(after + 1, a.identityBanners.size(),
                "a surface cannot know to clear its banner unless it is TOLD the verdict is clear");
        assertFalse(a.identityBanners.get(a.identityBanners.size() - 1).shown());
    }

    /**
     * Note which gate does the work here: {@code OpenLog.onLogIdentityObserved} returns {@code moved}, so a
     * repeated identical observation never propagates and the node's own {@code view.equals(emitted)} is not
     * reached. The behaviour is doubly protected, which is why no single mutation can fail this test — see
     * {@link #anAppendIsNotAVerdictChange} for the one that pins the node's gate.
     */
    @Test
    @DisplayName("An unchanged verdict is not re-drawn — stopped upstream, before the node's own gate")
    void anUnchangedVerdictIsNotRedrawn() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        long g = d.snapshot().logGeneration();
        d.post(new SessionEvents.LogIdentityObserved(g, "UNVERIFIED", "changed after it was read"));
        int after = a.identityBanners.size();

        for (int i = 0; i < 5; i++) {
            d.post(new SessionEvents.LogIdentityObserved(g, "UNVERIFIED", "changed after it was read"));
        }

        assertEquals(after, a.identityBanners.size(),
                "the measured claim of RESULTS-2: an element that does not change writes nothing");
    }

    @Test
    @DisplayName("A record append does not redraw the banner")
    void anAppendIsNotAVerdictChange() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        long g = d.snapshot().logGeneration();
        d.post(new SessionEvents.LogIdentityObserved(g, "UNVERIFIED", "changed"));
        int after = a.identityBanners.size();

        d.post(new SessionEvents.LogAppended(g, Set.of("a"), 1, 26, "TRACE"));

        assertEquals(after, a.identityBanners.size(),
                "this is the whole difference from the status line, and the reason the cost is per CHANGE");
    }

    @Test
    @DisplayName("The audit records what the banner was told, as the fields that changed")
    void theAuditSaysWhatTheBannerWasTold() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionAuditSink sink = new SessionAuditSink();
        SessionDriver d = new SessionDriver(a, sink);
        SessionFixtures.openLog(d, a, "/logs/f.yaml", "DEMO", Set.of("a"), 1, 25, "TRACE");

        d.post(new SessionEvents.LogIdentityObserved(d.snapshot().logGeneration(), "REPLACEMENT", "replaced"));

        List<String> renders = sink.matching("render: identityBanner");
        assertFalse(renders.isEmpty(), "the render is in the audit");
        String entry = renders.get(renders.size() - 1).lines()
                .filter(l -> l.contains("- identityBannerView:")).findFirst().orElse("");
        assertTrue(entry.contains("verdict: REPLACEMENT"), entry);
        assertTrue(entry.contains("shown: true"), entry);
        // The DIFF, not the view: the generation did not change between these two renders, so it is not written.
        // Asserting only what is PRESENT cannot tell changedFrom(emitted) from fields() -- the mutation gate
        // caught exactly that, because the two differ here by this one absent field and nothing else.
        assertFalse(entry.contains("generation:"),
                "an unchanged field is not re-stated — this is what makes the audit cost per CHANGE: " + entry);
        assertEquals(2, sink.matching("rendered: identityBanner").size(),
                "TWO: opening the log draws the first view (clear), and the verdict draws the second. The first "
                        + "is not waste — without it a surface would keep the PREVIOUS log's banner, since nothing "
                        + "else tells it to take one down. The answer names this element either way, which is what "
                        + "StatusLineViewTest's over-broad match could not do.");
    }
}
