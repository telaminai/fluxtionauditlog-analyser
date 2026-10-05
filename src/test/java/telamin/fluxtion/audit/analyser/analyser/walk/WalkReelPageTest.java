package telamin.fluxtion.audit.analyser.analyser.walk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #82 — the reel page itself. The rule the feature exists for is <b>no reel without its bundle named on the
 * finish page</b>, and its other half: a walk not captured from a bundle SAYS SO, rather than leaving the question
 * open. Both are asserted here, on the page a recipient actually opens.
 */
class WalkReelPageTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};

    private static WalkReel.Frame frame(int n, String caption) {
        return new WalkReel.Frame(n, caption, List.of("the first price arrives here"), "SHOWN", "", 1280, 720,
                List.of(), PNG);
    }

    private static WalkReel.Reel reel(WalkReel.Evidence evidence) {
        return new WalkReel.Reel("first-breach", "How the first recorded breach was found", "by a person",
                "2026-09-30T09:00:00Z", "2026-09-30T10:15:00Z", "",
                new WalkReel.Log("demo-quote-series.yaml", 1234, "2026-01-01 00:00:00.000 → 2026-01-01 00:05:00.000 UTC",
                        "com.acme.app.DemoProcessor"),
                evidence, List.of(frame(1, "start here"), frame(2, "and this is why")));
    }

    private static WalkReel.Evidence bundle() {
        return new WalkReel.Evidence("sha256:cbd8f28dbc50263d45678cc926f089e6ad5cffab1a990f37e8ec87b20fa21743",
                "recorded-run.fexp",
                List.of("unsigned: verification detects a changed member; it does not authenticate the sender",
                        "no replay: this bundle shows an investigation; it does not reproduce or fix it"),
                "Captured after the Tuesday incident.", "");
    }

    @Test
    @DisplayName("#82: the title page states what is being shown — log, records, span, processor and when")
    void theTitlePageCarriesTheContext() {
        String html = WalkReel.html(reel(bundle()));
        assertTrue(html.contains("<h1>first-breach</h1>"), "the walk's name");
        assertTrue(html.contains("How the first recorded breach was found"), "its one-line purpose");
        assertTrue(html.contains("demo-quote-series.yaml"), "the log being shown");
        assertTrue(html.contains("1,234"), "its record count");
        assertTrue(html.contains("2026-01-01 00:00:00.000 → 2026-01-01 00:05:00.000 UTC"), "its time span");
        assertTrue(html.contains("com.acme.app.DemoProcessor"), "the event processor the run came from");
        assertTrue(html.contains("2026-09-30T10:15:00Z"), "when the reel was recorded");
        assertTrue(html.contains("2026-09-30T09:00:00Z"), "and when the walk itself was saved");
    }

    @Test
    @DisplayName("#82: a walk with no title states that, rather than repeating its name as a purpose")
    void aWalkWithoutAPurposeSaysSo() {
        WalkReel.Reel r = new WalkReel.Reel("tour", "", "by a person", "", "2026-09-30T10:15:00Z", "",
                new WalkReel.Log("demo.yaml", 3, "", ""), bundle(), List.of(frame(1, "one")));
        String html = WalkReel.html(r);
        assertTrue(html.contains("This walk records no one-line purpose."));
        assertTrue(html.contains("not declared for this session"), "an unstated processor is not invented");
        assertTrue(html.contains("no timestamps"), "nor is a time span");
    }

    @Test
    @DisplayName("#82: the finish page names the bundle — identity, file, the invitation and the bundle's own limits")
    void theFinishPageNamesTheBundle() {
        String html = WalkReel.html(reel(bundle()));
        assertTrue(html.contains("sha256:cbd8f28dbc50263d45678cc926f089e6ad5cffab1a990f37e8ec87b20fa21743"),
                "the identity, as selectable text");
        assertTrue(html.contains("href=\"recorded-run.fexp\""), "a real link, relative to the page");
        assertTrue(html.contains(WalkReel.OPEN_IT_YOURSELF), "the answer to 'how do I know?'");
        assertTrue(html.contains("it does not authenticate the sender"), "the limits the bundle already states");
        assertTrue(html.contains("it does not reproduce or fix it"));
        assertTrue(html.contains("Captured after the Tuesday incident."), "and the sender's note");
        assertTrue(html.contains(WalkReel.esc(WalkReel.SENDER_WORDS)), "labelled as the sender's words");
        assertFalse(html.contains(WalkReel.NO_BUNDLE), "and it does not also claim there was no bundle");
    }

    @Test
    @DisplayName("#82: a write that fails leaves no reel at all, never a page cut off before its evidence")
    void aFailedWriteLeavesNothingAtTheTarget(@TempDir java.nio.file.Path dir) throws Exception {
        java.nio.file.Path out = dir.resolve("tour.html");
        // the staging name is occupied by a DIRECTORY, so the write fails after the target has been chosen
        java.nio.file.Files.createDirectory(dir.resolve("tour.html.part"));
        assertThrows(java.io.IOException.class, () -> WalkReel.write(out, WalkReel.bytes(reel(bundle()))));
        assertFalse(java.nio.file.Files.exists(out),
                "a half-written reel would render with its finish page missing: nothing may be left at the target");
    }

    @Test
    @DisplayName("#82: a reel is staged and moved, so the finished name never holds a partial page")
    void aWrittenReelIsCompleteAndLeavesNoStagingFile(@TempDir java.nio.file.Path dir) throws Exception {
        java.nio.file.Path out = dir.resolve("tour.html");
        WalkReel.write(out, WalkReel.bytes(reel(bundle())));
        String html = java.nio.file.Files.readString(out);
        assertTrue(html.contains(WalkReel.OPEN_IT_YOURSELF), "the finish page is present in the written file");
        assertTrue(html.trim().endsWith("</html>"), "and the document is whole");
        assertFalse(java.nio.file.Files.exists(dir.resolve("tour.html.part")), "no staging file is left behind");
    }

    @Test
    @DisplayName("#82: a bundle that does not carry these frames' log is not offered as their evidence")
    void aBundleThatDoesNotCoverTheFramesSaysSoInsteadOfInviting() {
        WalkReel.Evidence other = new WalkReel.Evidence(
                "sha256:cbd8f28dbc50263d45678cc926f089e6ad5cffab1a990f37e8ec87b20fa21743", "recorded-run.fexp",
                List.of("unsigned: verification detects a changed member; it does not authenticate the sender"),
                "", "The log shown above was opened separately, not from this bundle's unpacked copy.");
        assertFalse(other.coversTheseFrames(), "a stated relation means the bundle is not the evidence");
        String html = WalkReel.html(reel(other));
        assertTrue(html.contains(WalkReel.esc(WalkReel.BUNDLE_IS_NOT_THESE_RECORDS)),
                "the page says the bundle is not the evidence for these frames");
        assertTrue(html.contains("opened separately"), "and why, in the analyser's own words");
        assertFalse(html.contains(WalkReel.OPEN_IT_YOURSELF),
                "it must NOT also invite replaying these steps against a bundle that cannot reproduce them");
        assertTrue(html.contains("sha256:cbd8f28dbc50263d45678cc926f089e6ad5cffab1a990f37e8ec87b20fa21743"),
                "the identity is still shown: the bundle is real, it is just not the evidence here");
        assertFalse(html.contains(WalkReel.NO_BUNDLE), "and this is not the no-bundle case either");
    }

    @Test
    @DisplayName("#82: a reel with no bundle behind it says so plainly, rather than leaving the question open")
    void aReelWithoutABundleSaysSo() {
        String html = WalkReel.html(reel(null));
        assertTrue(html.contains("No evidence bundle"));
        assertTrue(html.contains(WalkReel.NO_BUNDLE));
        assertFalse(html.contains(WalkReel.OPEN_IT_YOURSELF), "there is nothing to invite anyone to open");
        assertFalse(html.contains("sha256:"), "and no identity is implied");
    }

    @Test
    @DisplayName("#82: an evidence record with no identity is not a bundle — a working copy alone never reads as one")
    void anEvidenceWithoutAnIdentityIsNotABundle() {
        WalkReel.Reel r = reel(new WalkReel.Evidence("", "recorded-run.fexp", List.of(), "", ""));
        assertFalse(r.fromBundle());
        assertTrue(WalkReel.html(r).contains(WalkReel.NO_BUNDLE));
    }

    @Test
    @DisplayName("#82: every frame is embedded in the page, so it opens with no files beside it")
    void theFramesAreEmbedded() {
        String html = WalkReel.html(reel(bundle()));
        String encoded = Base64.getEncoder().encodeToString(PNG);
        assertEquals(2, html.split("data:image/png;base64," + encoded, -1).length - 1,
                "both frames, inline");
        assertFalse(html.contains("<script"), "nothing to run, and nothing to fetch");
        assertFalse(html.contains("http://"), "the page is self-contained");
        assertTrue(html.contains("Step 1 of 2") && html.contains("Step 2 of 2"));
        assertTrue(html.contains("the first price arrives here"), "the step's target captions");
    }

    @Test
    @DisplayName("#82: a step the analyser could not fully show says so on the page, with its reason")
    void anUnshownStepIsNotHidden() {
        WalkReel.Frame partial = new WalkReel.Frame(1, "the chart", List.of("this series"), "PARTLY_SHOWN",
                "no room at 900x620 px, widen the window", 900, 620, List.of(), PNG);
        String html = WalkReel.html(new WalkReel.Reel("w", "", "", "", "now", "",
                new WalkReel.Log("demo.yaml", 1, "", ""), bundle(), List.of(partial)));
        assertTrue(html.contains("Partly shown"));
        assertTrue(html.contains("no room at 900x620 px, widen the window"));
    }

    @Test
    @DisplayName("#82: the dialogue a step reveals is carried, labelled as what it is and never as live")
    void theDialogueIsCarriedAndLabelled() {
        WalkReel.Frame f = new WalkReel.Frame(1, "one", List.of(), "SHOWN", "", 800, 600,
                List.of(new WalkSpec.Turn("t1", "user", "why is this price stale?")), PNG);
        String html = WalkReel.html(new WalkReel.Reel("w", "", "", "", "now", "Simulated conversation",
                new WalkReel.Log("demo.yaml", 1, "", ""), bundle(), List.of(f)));
        assertTrue(html.contains("Simulated conversation"));
        assertTrue(html.contains("why is this price stale?"));
    }

    @Test
    @DisplayName("#82: text from the log or the sender is escaped, never interpreted")
    void theTextIsEscaped() {
        WalkReel.Frame f = new WalkReel.Frame(1, "<script>alert(1)</script>", List.of("a & b"), "SHOWN", "", 8, 6,
                List.of(), PNG);
        String html = WalkReel.html(new WalkReel.Reel("w", "", "", "", "now", "",
                new WalkReel.Log("demo.yaml", 1, "", ""),
                new WalkReel.Evidence("sha256:aa", "a\"b.fexp", List.of(), "<b>hi</b>", ""), List.of(f)));
        assertFalse(html.contains("<script>alert"), "a caption cannot become a script");
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertTrue(html.contains("a &amp; b"));
        assertTrue(html.contains("href=\"a&quot;b.fexp\""), "and a quote cannot break out of an attribute");
        assertTrue(html.contains("&lt;b&gt;hi&lt;/b&gt;"));
    }
}
