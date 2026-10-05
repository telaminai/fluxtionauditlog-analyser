package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.render;

/**
 * #83: borrowing from a bundle verifies the zip twice and unzips it. On the event thread that froze
 * the window on a large bundle, with no progress and no cancel — while every other bundle path
 * already read off the EDT.
 */
class BundleImportOffTheEdtFrameTest {

    @AfterEach
    void clearSeam() {
        MainFrame.beforeBundleRead = () -> { };
    }

    @Test
    @DisplayName("#83: a bundle is verified and unpacked off the event thread")
    void theReadIsNotOnTheEventThread(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = EvidenceCaptureFrameTest.shown(tmp)) {
            Path dir = EvidenceCaptureFrameTest.exchange(f, tmp);
            Path mine = Files.createDirectories(tmp.resolve("mine"));
            Path profile = mine.resolve(".analyser").resolve("project.fluxtion-settings");
            Files.createDirectories(profile.getParent());
            Files.writeString(profile, "sourceRoot.count=0\n");
            onEdt(() -> render(f.ex, "open", Map.of("project", profile.toString())));

            EvidenceCaptureFrameTest.openLog(f, EvidenceCaptureFrameTest.DEMO_LOG);
            onEdt(() -> render(f.ex, "report", Map.of("bundle", Map.of("path", "borrow.fexp"))));
            assertEquals("WRITTEN", EvidenceCaptureFrameTest.awaitDecided(f).get("phase"));
            Path fexp = dir.resolve("borrow.fexp");

            AtomicReference<Boolean> readOnEdt = new AtomicReference<>();
            MainFrame.beforeBundleRead = () -> readOnEdt.set(SwingUtilities.isEventDispatchThread());

            // the verb as a socket caller reaches it: from a worker, never the event thread
            AtomicReference<Object> echo = new AtomicReference<>();
            Thread caller = new Thread(() ->
                    echo.set(f.ex.render("import", new java.util.LinkedHashMap<>(
                            Map.of("bundle", fexp.toString())))), "DEMO-socket");
            caller.start();
            caller.join(30_000);

            assertFalse(caller.isAlive(), "the read caller completed before the assertion");
            org.junit.jupiter.api.Assertions.assertTrue(((telamin.fluxtion.audit.analyser.analyser.llm.ActionResult) echo.get()).ok(),
                    "the worker preview succeeds");
            assertNotNull(readOnEdt.get(), "the read did not happen at all: " + echo.get());
            assertFalse(readOnEdt.get(),
                    "theVerifyAndUnzipRanONTheEventThread — a large bundle freezes the window there");
        }
    }
    private static Path borrowable(Path tmp) throws Exception {
        Path payload = Files.createDirectories(tmp.resolve("payload/profile"));
        Files.writeString(payload.resolve("project.fluxtion-settings"),
                "hiddenColumn.count=1\nhiddenColumn.0=DEMO-borrowed\n");
        Path log = Files.createDirectories(tmp.resolve("payload/log"));
        Files.writeString(log.resolve("DEMO.yaml"), "eventLogRecord:\n  event: DEMO\n");
        Path bundle = tmp.resolve("DEMO.fexp");
        telamin.fluxtion.audit.analyser.bundle.EvidenceBundle.pack(tmp.resolve("payload"), bundle,
                java.time.Instant.parse("2026-10-05T00:00:00Z"), "DEMO");
        return bundle;
    }

    private static List<String> hidden(AsyncOpenInterleavingFrameTest.Frame f) throws Exception {
        AtomicReference<List<String>> answer = new AtomicReference<>();
        onEdt(() -> answer.set(List.copyOf(((telamin.fluxtion.audit.analyser.analyser.config.AppConfig)
                field(f.frame, "config")).hiddenColumns)));
        return answer.get();
    }

    @Test
    void cancellationDuringReadCannotApplyTheBorrow(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path bundle = borrowable(tmp);
        try (var f = EvidenceCaptureFrameTest.shown(tmp)) {
            Path a = Issue84BundleFrameTest.profile(tmp.resolve("A"));
            onEdt(() -> render(f.ex, "open", Map.of("project", a.toString())));
            List<String> before = hidden(f);
            var allowed = new java.util.concurrent.atomic.AtomicBoolean(true);
            MainFrame.beforeBundleRead = () -> allowed.set(false);
            ActionExecutor.bindGuard(allowed::get);
            try { f.ex.render("import", Map.of("bundle", bundle.toString(), "categories", List.of("VIEW"))); }
            catch (ActionExecutor.Superseded expected) { /* refusal is acceptable; mutation is not */ }
            finally { ActionExecutor.bindGuard(null); }
            org.junit.jupiter.api.Assertions.assertEquals(before, hidden(f), "cancelledBorrowMustNotApply");
        }
    }

    @Test
    void aProjectSwitchDuringReadCannotReceiveTheBorrow(@TempDir Path tmp) throws Exception {
        projectSwitchDuringRead(tmp, false);
    }

    @Test
    void switchingAwayAndBackDoesNotReviveTheBorrow(@TempDir Path tmp) throws Exception {
        projectSwitchDuringRead(tmp, true);
    }

    private static void projectSwitchDuringRead(Path tmp, boolean returnToA) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path bundle = borrowable(tmp);
        try (var f = EvidenceCaptureFrameTest.shown(tmp)) {
            Path a = Issue84BundleFrameTest.profile(tmp.resolve("A"));
            Path b = Issue84BundleFrameTest.profile(tmp.resolve("B"));
            onEdt(() -> render(f.ex, "open", Map.of("project", a.toString())));
            List<String> before = hidden(f);
            MainFrame.beforeBundleRead = () -> {
                try {
                    onEdt(() -> {
                        render(f.ex, "open", Map.of("project", b.toString()));
                        if (returnToA) render(f.ex, "open", Map.of("project", a.toString()));
                    });
                } catch (Exception e) { throw new IllegalStateException(e); }
            };
            var reply = f.ex.render("import", Map.of("bundle", bundle.toString(), "categories", List.of("VIEW")));
            org.junit.jupiter.api.Assertions.assertEquals(before, hidden(f),
                    returnToA ? "returningToAProjectMustNotReviveTheBorrow" : "newProjectMustNotReceiveTheOldBorrow");
            assertFalse(reply.ok(), "a superseded borrow must report refusal");
        }
    }

    @Test
    void anUnchangedProjectReceivesTheBorrow(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path bundle = borrowable(tmp);
        try (var f = EvidenceCaptureFrameTest.shown(tmp)) {
            Path a = Issue84BundleFrameTest.profile(tmp.resolve("A"));
            onEdt(() -> render(f.ex, "open", Map.of("project", a.toString())));
            var reply = f.ex.render("import", Map.of("bundle", bundle.toString(), "categories", List.of("VIEW")));
            org.junit.jupiter.api.Assertions.assertTrue(reply.ok(), "the current project accepts a valid borrow");
            assertEquals(List.of("DEMO-borrowed"), hidden(f), "the current project receives the requested settings");
        }
    }

    @Test
    void anEdtCallerIsRefusedBeforeAnyRead(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path bundle = borrowable(tmp);
        try (var f = EvidenceCaptureFrameTest.shown(tmp)) {
            Path a = Issue84BundleFrameTest.profile(tmp.resolve("A"));
            onEdt(() -> render(f.ex, "open", Map.of("project", a.toString())));
            var read = new java.util.concurrent.atomic.AtomicBoolean();
            MainFrame.beforeBundleRead = () -> read.set(true);
            onEdt(() -> {
                var reply = f.ex.render("import", Map.of("bundle", bundle.toString()));
                assertFalse(reply.ok(), "an EDT caller is explicitly refused");
            });
            assertFalse(read.get(), "anEdtCallerMustNotStartBundleIO");
        }
    }

    @Test
    void anOpenedCopySurvivesCleanupAndAClosedOneIsReaped(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path bundle = borrowable(tmp);
        try (var f = EvidenceCaptureFrameTest.shown(tmp)) {
            Issue84BundleFrameTest.openBundle(f, bundle);
            var driver = (telamin.fluxtion.audit.analyser.analyser.session.SessionDriver) field(f.frame, "session");
            Path copy = Path.of(driver.snapshot().bundle().workingCopy());
            assertEquals(0, telamin.fluxtion.audit.analyser.bundle.EvidenceBundle.reap(List.of(copy), null),
                    "theOpenWindowMustOwnItsBundleCopy");
            org.junit.jupiter.api.Assertions.assertTrue(Files.exists(copy.resolve("log/DEMO.yaml")),
                    "cleanupMustLeaveTheOpenLogReadable");
            onEdt(() -> render(f.ex, "open", Map.of("close", "project")));
            telamin.fluxtion.audit.analyser.bundle.EvidenceBundle.reap(List.of(copy), null);
            assertFalse(Files.exists(copy), "aSettledProjectCloseReleasesAndReapsItsCopy");
        }
    }

    @Test
    void anUnpackedProfileOpenAlsoOwnsTheCopy(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path bundle = borrowable(tmp);
        try (var f = EvidenceCaptureFrameTest.shown(tmp)) {
            var unpacked = telamin.fluxtion.audit.analyser.bundle.EvidenceBundle.unpack(bundle,
                    telamin.fluxtion.audit.analyser.bundle.EvidenceBundle.workingCopiesRoot());
            Path copy = unpacked.workingCopy();
            onEdt(() -> render(f.ex, "open", Map.of("project", copy.resolve("profile/project.fluxtion-settings").toString())));
            assertEquals(0, telamin.fluxtion.audit.analyser.bundle.EvidenceBundle.reap(List.of(copy), null),
                    "openingAWorkingProfileDirectlyMustAcquireOwnership");
            org.junit.jupiter.api.Assertions.assertTrue(Files.exists(copy.resolve("profile/project.fluxtion-settings")),
                    "theDirectProjectProfileMustRemainReadable");
        }
    }

}
