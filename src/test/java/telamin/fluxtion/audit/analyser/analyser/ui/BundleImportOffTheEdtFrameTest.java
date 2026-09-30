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

            assertNotNull(readOnEdt.get(), "the read did not happen at all: " + echo.get());
            assertFalse(readOnEdt.get(),
                    "theVerifyAndUnzipRanONTheEventThread — a large bundle freezes the window there");
        }
    }
}
