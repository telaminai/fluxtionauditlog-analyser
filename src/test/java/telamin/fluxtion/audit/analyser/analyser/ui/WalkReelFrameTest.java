package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkReel;

import java.awt.GraphicsEnvironment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.render;
import static telamin.fluxtion.audit.analyser.analyser.ui.EvidenceCaptureFrameTest.awaitDecided;
import static telamin.fluxtion.audit.analyser.analyser.ui.EvidenceCaptureFrameTest.exchange;
import static telamin.fluxtion.audit.analyser.analyser.ui.EvidenceCaptureFrameTest.openLog;
import static telamin.fluxtion.audit.analyser.analyser.ui.EvidenceCaptureFrameTest.shown;

/**
 * Issue #82 on a REAL frame: play a saved walk, photograph every settled step, and write one page.
 *
 * <p>The rule under test is the one the reel exists for — <b>no reel without its bundle named on the finish
 * page</b> — in both directions: a session that came from a verified bundle names it, and a session that did not
 * says so plainly rather than leaving the question open.
 */
class WalkReelFrameTest {

    /** The reel verb runs on the CALLING thread by design, so it is never called inside {@code onEdt}. */
    private static Map<String, Object> reel(AsyncOpenInterleavingFrameTest.Frame f, String walk, String file) {
        return f.ex.render("walk", new java.util.LinkedHashMap<>(Map.of("name", walk, "reel", file))).toMap();
    }

    private static String page(Path at) throws Exception {
        assertTrue(Files.exists(at), "the reel was written to " + at);
        return Files.readString(at, StandardCharsets.UTF_8);
    }

    /** A two-step walk of structural targets — it needs no records, so it plays inside a bundle unchanged. */
    private static void saveTwoStepWalk(AsyncOpenInterleavingFrameTest.Frame f) throws Exception {
        onEdt(() -> render(f.ex, "walk", Map.of("name", "tour", "title", "How a quote is priced", "steps", List.of(
                Map.of("caption", "every price arrives here first",
                        "view", Map.of("tab", "summary"),
                        "targets", List.of(Map.of("target", "tab:summary", "caption", "the event summary"))),
                Map.of("caption", "and this is where it is published",
                        "view", Map.of("tab", "topology"),
                        "targets", List.of(Map.of("target", "tab:topology", "caption", "the topology")))))));
    }

    @Test
    @DisplayName("#82: a reel records every step, and a session with no bundle behind it says so on the finish page")
    void aReelIsRecordedAndSaysWhenThereIsNoBundle(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = shown(tmp)) {
            Path dir = exchange(f, tmp);
            openLog(f, EvidenceCaptureFrameTest.DEMO_LOG);
            saveTwoStepWalk(f);

            Map<String, Object> answer = reel(f, "tour", "tour-reel.html");
            assertEquals(Boolean.TRUE, answer.get("ok"), "the reel was recorded: " + answer);
            @SuppressWarnings("unchecked")
            Map<String, Object> wrote = (Map<String, Object>) answer.get("reel");
            assertEquals(2, wrote.get("steps"), "one frame per step");
            assertNotNull(wrote.get("evidence"));

            String html = page(dir.resolve("tour-reel.html"));
            assertTrue(html.contains("<h1>tour</h1>"), "the walk's name is on the title page");
            assertTrue(html.contains("How a quote is priced"), "and its purpose");
            assertTrue(html.contains("demo-quote-audit.yaml"), "the log being shown, by file name");
            assertTrue(html.contains("Step 1 of 2") && html.contains("Step 2 of 2"), "a page per step");
            assertEquals(2, html.split("data:image/png;base64,", -1).length - 1, "both frames are embedded");
            assertTrue(html.contains("every price arrives here first"), "the step's own words");
            assertFalse(html.contains("PREPARING"), "noStepWasPhotographedWhilePreparing");

            assertTrue(html.contains(WalkReel.NO_BUNDLE), "aReelWithNoBundleSaysSoOnTheFinishPage");
            assertFalse(html.contains("sha256:"), "and claims no identity it does not have");

            // it is a page, not a folder of pictures: nothing was written beside it
            try (var s = Files.list(dir)) {
                assertEquals(List.of("tour-reel.html"), s.map(p -> p.getFileName().toString()).sorted().toList());
            }
        }
    }

    @Test
    @DisplayName("#82: a reel recorded from an opened evidence bundle names it, its identity and its limits")
    void aReelFromABundleNamesItOnTheFinishPage(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = shown(tmp)) {
            Path dir = exchange(f, tmp);
            openLog(f, EvidenceCaptureFrameTest.DEMO_LOG);
            saveTwoStepWalk(f);
            onEdt(() -> render(f.ex, "report", Map.of("bundle", Map.of("path", "recorded-run.fexp",
                    "notes", "Captured for the reel test."))));
            Map<String, Object> written = awaitDecided(f);
            assertEquals("WRITTEN", written.get("phase"), "the fixture bundle was written: " + written);
            String identity = String.valueOf(written.get("identity"));

            openBundle(f, dir.resolve("recorded-run.fexp"));

            Map<String, Object> answer = reel(f, "tour", "from-bundle.html");
            assertEquals(Boolean.TRUE, answer.get("ok"), "the reel was recorded: " + answer);

            String html = page(dir.resolve("from-bundle.html"));
            assertTrue(html.contains(identity), "theFinishPageCarriesTheBundleIdentity: " + identity);
            assertTrue(html.contains("href=\"recorded-run.fexp\""), "theFinishPageLinksTheBundleByFileName");
            assertTrue(html.contains(WalkReel.OPEN_IT_YOURSELF), "theFinishPageInvitesTheRecipientToCheckIt");
            assertTrue(html.contains("it does not authenticate the sender"),
                    "theFinishPageStatesTheBundlesOwnLimits");
            assertTrue(html.contains("Captured for the reel test."), "and the sender's note, labelled as theirs");
            assertFalse(html.contains(WalkReel.NO_BUNDLE), "and it does not also deny having one");
            // the working copy the bundle was unpacked into is a temp folder nobody can follow: never on the page
            assertFalse(html.contains("bundle-"), "the page names the .fexp, never the unpacked copy");
        }
    }

    @Test
    @DisplayName("#82: a reel is a walk operation like any other — a second one beside it is refused")
    void aReelBesideAnotherOperationIsRefused(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = shown(tmp)) {
            exchange(f, tmp);
            openLog(f, EvidenceCaptureFrameTest.DEMO_LOG);
            saveTwoStepWalk(f);
            Map<String, Object> refused = f.ex.render("walk", new java.util.LinkedHashMap<>(
                    Map.of("name", "tour", "reel", "x.html", "delete", true))).toMap();
            assertEquals(Boolean.FALSE, refused.get("ok"), "two operations in one call: " + refused);
            assertTrue(String.valueOf(refused.get("error")).contains("ONE operation per call"), refused.toString());
            assertFalse(Files.exists(tmp.resolve("exchange").resolve("x.html")), "and nothing was written");

            Map<String, Object> missing = f.ex.render("walk", new java.util.LinkedHashMap<>(
                    Map.of("name", "nope", "reel", "y.html"))).toMap();
            assertEquals(Boolean.FALSE, missing.get("ok"), missing.toString());
            assertTrue(String.valueOf(missing.get("error")).contains("no walk called 'nope'"), missing.toString());
        }
    }

    /** Open a bundle the way a drop does, and wait until the session says the project in force is that bundle. */
    @SuppressWarnings("unchecked")
    private static void openBundle(AsyncOpenInterleavingFrameTest.Frame f, Path fexp) throws Exception {
        onEdt(() -> {
            try {
                var m = MainFrame.class.getDeclaredMethod("loadExperiment", Path.class);
                m.setAccessible(true);
                m.invoke(f.frame, fexp);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
        for (int i = 0; i < 300; i++) {
            AtomicReference<Object> bundle = new AtomicReference<>();
            onEdt(() -> {
                var ctx = (Map<String, Object>) render(f.ex, "context", Map.of("sections", List.of("project")))
                        .get("context");
                var project = (Map<String, Object>) ctx.get("project");
                bundle.set(project == null ? null : project.get("bundle"));
            });
            if (bundle.get() != null) return;
            Thread.sleep(50);
        }
        throw new AssertionError("the bundle never came into force");
    }
}
