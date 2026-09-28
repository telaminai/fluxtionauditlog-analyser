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

    @Test
    @DisplayName("Review of #58: closing a log with a warning up takes the banner DOWN — nothing else will")
    void closingTheLogTakesTheBannerDown() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        long g = d.snapshot().logGeneration();
        d.post(new SessionEvents.LogIdentityObserved(g, "REPLACEMENT", "replaced"));
        assertTrue(a.identityBanners.get(a.identityBanners.size() - 1).shown(), "the warning is up");
        int after = a.identityBanners.size();

        d.post(new SessionEvents.LogCleared(g));

        assertEquals(after + 1, a.identityBanners.size(),
                "the three backends are the only writers of the banner: unless the session tells them the log is gone, "
                        + "the table, the charts and the detail pane keep saying 'reopen the log' over an empty screen");
        assertFalse(a.identityBanners.get(a.identityBanners.size() - 1).shown());

        d.post(new SessionEvents.LogCleared(g));
        assertEquals(after + 1, a.identityBanners.size(), "and a closed log is told once, not on every later cycle");
    }

    @Test
    @DisplayName("Review of #58: a render answer reaches only its own element's node — no fan-out across views")
    void aRenderAnswerReachesOnlyItsOwnNode() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionAuditSink sink = new SessionAuditSink();
        SessionDriver d = new SessionDriver(a, sink);
        SessionFixtures.openLog(d, a, "/logs/f.yaml", "DEMO", Set.of("a"), 1, 25, "TRACE");
        d.post(new SessionEvents.LogIdentityObserved(d.snapshot().logGeneration(), "REPLACEMENT", "replaced"));

        List<String> banner = sink.matching("event: ViewRendered").stream()
                .filter(r -> r.contains("element=identityBanner")).toList();
        assertFalse(banner.isEmpty(), "the banner's render was answered");
        for (String r : banner) {
            assertFalse(r.contains("statusLineView:"), "the status line's node ran on the banner's answer — every "
                    + "view node then pays for every other view's renders, (nodes × renders):\n" + r);
        }
        List<String> line = sink.matching("event: ViewRendered").stream()
                .filter(r -> r.contains("element=statusLine")).toList();
        for (String r : line) {
            assertFalse(r.contains("identityBannerView:"), "and the banner's node on the line's:\n" + r);
        }
    }

    @Test
    @DisplayName("An unchanged verdict is not re-drawn — this is why the element costs no records")
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
        assertEquals(2, sink.matching("rendered: identityBanner").size(),
                "TWO: opening the log draws the first view (clear), and the verdict draws the second. The first "
                        + "is not waste — without it a surface would keep the PREVIOUS log's banner, since nothing "
                        + "else tells it to take one down. The answer names this element either way, which is what "
                        + "StatusLineViewTest's over-broad match could not do.");
    }
}
