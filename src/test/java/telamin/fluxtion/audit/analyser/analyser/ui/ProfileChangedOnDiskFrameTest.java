package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile;
import telamin.fluxtion.audit.analyser.analyser.config.SettingsShare;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.render;

/**
 * #177 on a REAL frame: the open project's profile is edited outside the analyser. The frame observes the file and
 * reports it; the session OFFERS the choice (the chooser seam answers here); "not now" keeps the file — an auto-save
 * does not overwrite it — and {@code open {project, reload: true}} reads it again with the log left open. "Keep my
 * version" is the only way the session's settings go over a newer file.
 */
class ProfileChangedOnDiskFrameTest {

    static List<String> processors(int n) {
        List<String> out = new ArrayList<>();
        for (int i = 1; i <= n; i++) out.add("com.acme.demo.Processor" + i);
        return out;
    }

    static void externalEdit(Path file, int n) throws Exception {
        var share = new SettingsShare();
        AppConfig other = new AppConfig();
        ProjectProfile.load(file, other, share);
        other.eventProcessorFqns.clear();
        other.eventProcessorFqns.addAll(processors(n));
        Files.writeString(file, ProjectProfile.write(other, share, file));
    }

    static List<String> onDisk(Path file) {
        AppConfig c = new AppConfig();
        ProjectProfile.load(file, c, new SettingsShare());
        return named(c.eventProcessorFqns);
    }

    /** The declared processors, without the blank entry the Settings list keeps for a new row. */
    static List<String> named(List<String> fqns) {
        return fqns.stream().filter(s -> s != null && !s.isBlank()).toList();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> profileOnDisk(AsyncOpenInterleavingFrameTest.Frame f) throws Exception {
        AtomicReference<Map<String, Object>> out = new AtomicReference<>();
        onEdt(() -> {
            var ctx = (Map<String, Object>) render(f.ex, "context", Map.of("sections", List.of("project"))).get("context");
            out.set((Map<String, Object>) ((Map<String, Object>) ctx.get("project")).get("profileOnDisk"));
        });
        return out.get();
    }

    static void flushProject(AsyncOpenInterleavingFrameTest.Frame f) throws Exception {
        onEdt(() -> {
            try {
                var m = MainFrame.class.getDeclaredMethod("flushProject");
                m.setAccessible(true);
                m.invoke(f.frame);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    static void open(AsyncOpenInterleavingFrameTest.Frame f, Path dir) throws Exception {
        onEdt(() -> render(f.ex, "open", Map.of("project", dir.toString())));
        EvidenceCaptureFrameTest.openLog(f, EvidenceCaptureFrameTest.DEMO_LOG);
        // the test observes by hand: the frame's own 1.5 s watch would race it on a slow display
        onEdt(() -> ((javax.swing.Timer) field(f.frame, "profileWatchTimer")).stop());
    }

    static Path seeded(Path tmp) throws Exception {
        Path dir = Files.createDirectories(tmp.resolve("demo-project"));
        AppConfig seed = new AppConfig();
        seed.eventProcessorFqns.addAll(processors(1));
        ProjectProfile.save(ProjectProfile.pathFor(dir), seed, new SettingsShare());
        return dir;
    }

    static boolean bannerShown(AsyncOpenInterleavingFrameTest.Frame f) throws Exception {
        AtomicReference<Boolean> shown = new AtomicReference<>();
        onEdt(() -> shown.set(f.frame.profileBanner.isVisible()));
        return shown.get();
    }

    static void changeOnDisk(AsyncOpenInterleavingFrameTest.Frame f, Path file, int n) throws Exception {
        externalEdit(file, n);
        onEdt(() -> f.frame.observeProfile(false));
        WalkPlaybackFrameTest.await("the banner offers the change", () -> f.frame.profileBanner.isVisible());   // runs on the EDT
    }

    static void editAndSave(AsyncOpenInterleavingFrameTest.Frame f, Path tmp) throws Exception {
        AppConfig config = (AppConfig) field(f.frame, "config");
        onEdt(() -> {
            config.sourceRoots.add(tmp.resolve("src").toString());
            ((telamin.fluxtion.audit.analyser.analyser.config.ProjectSession) field(f.frame, "project")).requestSave();
        });
        flushProject(f);
    }

    @Test
    @DisplayName("★ #177: an outside edit is offered on a banner, survives an auto-save, and open {project, reload: true} reads it (log stays open)")
    @SuppressWarnings("unchecked")
    void anOutsideEditIsOfferedKeptAndReloaded(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path dir = seeded(tmp);
        Path file = ProjectProfile.pathFor(dir);
        try (var f = EvidenceCaptureFrameTest.shown(tmp)) {
            open(f, dir);
            AppConfig config = (AppConfig) field(f.frame, "config");
            assertEquals(processors(1), named(config.eventProcessorFqns), "control: one processor");
            assertNull(profileOnDisk(f), "control: in step, nothing to say");
            assertFalse(bannerShown(f), "control: no banner");

            changeOnDisk(f, file, 10);
            onEdt(() -> assertTrue(f.frame.profileBanner.text().startsWith("Project settings changed on disk"),
                    f.frame.profileBanner.text()));
            assertEquals("changedOnDisk", profileOnDisk(f).get("state"), "context states it");

            // "Not now", then an in-memory edit and the debounced save: the file is NOT overwritten
            onEdt(f.frame.profileBanner.notNow::doClick);
            assertFalse(bannerShown(f), "Not now puts the banner away");
            editAndSave(f, tmp);
            assertEquals(processors(10), onDisk(file), "★ the outside edit survives the auto-save (red before #177: reverted to 1)");
            assertEquals(Boolean.TRUE, profileOnDisk(f).get("saveRefused"), "and the refusal is stated");
            assertFalse(bannerShown(f), "the same change is not offered again");

            // owner decision on #198: Not now is for THAT change. The file changing again brings the banner back,
            // so Keep mine stays reachable (this test used to stop at "not offered again")
            changeOnDisk(f, file, 12);
            assertTrue(bannerShown(f), "★ a further change on disk is offered again after Not now");
            onEdt(() -> assertTrue(f.frame.profileBanner.keep.isShowing() && f.frame.profileBanner.keep.isEnabled(),
                    "and Keep mine is reachable"));

            AtomicReference<Map<String, Object>> reply = new AtomicReference<>();
            onEdt(() -> reply.set(render(f.ex, "open", Map.of("project", dir.toString(), "reload", true))));
            Map<String, Object> echo = (Map<String, Object>) reply.get().get("opened");
            assertEquals(Boolean.TRUE, echo.get("reloaded"), "★ open {project, reload: true} re-reads the file: " + echo);
            assertNotNull(echo.get("replaced"), "and says what it replaced: " + echo);
            assertTrue(String.valueOf(echo.get("note")).contains("unsaved edits were discarded"), "and what it discarded: " + echo);
            assertEquals(processors(12), named(config.eventProcessorFqns), "the session now has the twelve");
            AtomicReference<Boolean> logOpen = new AtomicReference<>();
            onEdt(() -> logOpen.set(field(f.frame, "store") != null));
            assertTrue(logOpen.get(), "the log stays open: a reload is not a switch");
            assertEquals("inStep", profileOnDisk(f).get("state"));

            // "Keep mine" is the only way this session's settings go over a newer file
            changeOnDisk(f, file, 3);
            onEdt(f.frame.profileBanner.keep::doClick);
            WalkPlaybackFrameTest.await("keep mine wrote the session's version", () -> onDisk(file).size() == 12);
            assertFalse(bannerShown(f), "and the banner goes");
        }
    }

    @Test
    @DisplayName("★ #177: closing the project with unsaved edits while 'Not now' stands keeps them beside the profile, and says where")
    @SuppressWarnings("unchecked")
    void closingWithUnsavedEditsDoesNotLoseThem(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path dir = seeded(tmp);
        Path file = ProjectProfile.pathFor(dir);
        try (var f = EvidenceCaptureFrameTest.shown(tmp)) {
            open(f, dir);
            changeOnDisk(f, file, 10);
            onEdt(f.frame.profileBanner.notNow::doClick);
            editAndSave(f, tmp);
            assertEquals(processors(10), onDisk(file), "control: the save was refused");

            onEdt(() -> render(f.ex, "open", Map.of("close", "project")));

            assertEquals(processors(10), onDisk(file), "the outside edit still wins on disk");
            List<Path> aside;
            try (var listing = Files.list(file.getParent())) {
                aside = listing.filter(p -> p.getFileName().toString().startsWith("project.fluxtion-settings.unsaved-")).toList();
            }
            assertEquals(1, aside.size(), "★ the window's unsaved edits were kept beside the profile (red before: dropped)");
            assertTrue(Files.readString(aside.get(0)).contains("src"), "with the edit in it");
            WalkPlaybackFrameTest.await("the status line says where", () -> String.valueOf(f.status()).startsWith("Unsaved project edits kept in"));
            assertTrue(f.status().contains(aside.get(0).getFileName().toString()), f.status());
            AtomicReference<Map<String, Object>> ctx = new AtomicReference<>();
            onEdt(() -> ctx.set((Map<String, Object>) ((Map<String, Object>) render(f.ex, "context",
                    Map.of("sections", List.of("project"))).get("context")).get("project")));
            assertEquals("…/" + aside.get(0).getFileName(), ((Map<String, Object>) ctx.get().get("profileOnDisk")).get("setAside"),
                    "context says it too: " + ctx.get());
        }
    }

    /** F1: a writer that fails every write as a full disk does. */
    static telamin.fluxtion.audit.analyser.analyser.config.ProjectSession.Writes diskFull() {
        return new telamin.fluxtion.audit.analyser.analyser.config.ProjectSession.Writes() {
            @Override
            public void saveProfile(Path file, AppConfig config, SettingsShare share) throws java.io.IOException {
                throw new java.io.IOException("No space left on device");
            }

            @Override
            public void writeNew(Path file, String text) throws java.io.IOException {
                throw new java.io.IOException("No space left on device");
            }
        };
    }

    static telamin.fluxtion.audit.analyser.analyser.config.ProjectSession session(AsyncOpenInterleavingFrameTest.Frame f) {
        return (telamin.fluxtion.audit.analyser.analyser.config.ProjectSession) field(f.frame, "project");
    }

    @Test
    @DisplayName("★ F1: close on a full disk is held BEFORE the log closes — project, log and edit stay; the person is asked")
    void aHeldCloseKeepsTheLogTheProjectAndTheEdit(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path dir = seeded(tmp);
        Path file = ProjectProfile.pathFor(dir);
        List<String> asked = new java.util.concurrent.CopyOnWriteArrayList<>();
        MainFrame.heldLeaveChooser = reason -> { asked.add(reason); return null; };   // "Keep the project open"
        try (var f = EvidenceCaptureFrameTest.shown(tmp)) {
            open(f, dir);
            AppConfig config = (AppConfig) field(f.frame, "config");
            onEdt(() -> session(f).setWrites(diskFull()));
            editAndSave(f, tmp);

            AtomicReference<telamin.fluxtion.audit.analyser.analyser.llm.ActionResult> reply = new AtomicReference<>();
            onEdt(() -> reply.set(f.ex.render("open", new java.util.LinkedHashMap<>(Map.of("close", "project")))));

            assertFalse(reply.get().ok(), "★ the close is refused, and says why: " + reply.get());
            assertTrue(String.valueOf(reply.get().error()).contains("No space left on device"), reply.get().error());
            WalkPlaybackFrameTest.await("the person is asked", () -> !asked.isEmpty());
            AtomicReference<Boolean> logOpen = new AtomicReference<>();
            onEdt(() -> logOpen.set(field(f.frame, "store") != null));
            assertTrue(logOpen.get(), "★ the log was not closed (the hold comes before any teardown)");
            assertEquals(file, session(f).activeFile(), "the project is still open");
            assertTrue(config.sourceRoots.contains(tmp.resolve("src").toString()), "the edit is still in memory");

            // the person saves the edits elsewhere; the next close goes ahead
            Path elsewhere = tmp.resolve("kept-edits.fluxtion-settings");
            MainFrame.heldLeaveChooser = reason -> new MainFrame.HeldLeaveAnswer(elsewhere, false);
            onEdt(() -> reply.set(f.ex.render("open", new java.util.LinkedHashMap<>(Map.of("close", "project")))));
            assertFalse(reply.get().ok(), "still held until the person answers: " + reply.get());
            WalkPlaybackFrameTest.await("the edits were saved where the person chose", () -> Files.exists(elsewhere));
            assertTrue(Files.readString(elsewhere).contains("src"));
            onEdt(() -> reply.set(f.ex.render("open", new java.util.LinkedHashMap<>(Map.of("close", "project")))));
            assertTrue(reply.get().ok(), "now the close goes ahead: " + reply.get());
            assertNull(session(f).activeFile());
        } finally {
            MainFrame.heldLeaveChooser = null;
        }
    }

    @Test
    @DisplayName("★ F1: quit on a full disk is held before anything is torn down; quitting after an explicit discard exits")
    void aHeldQuitDoesNotExitUntilThePersonAnswers(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path dir = seeded(tmp);
        List<String> asked = new java.util.concurrent.CopyOnWriteArrayList<>();
        MainFrame.heldLeaveChooser = reason -> { asked.add(reason); return null; };   // "Keep the project open"
        try (var f = EvidenceCaptureFrameTest.shown(tmp)) {
            open(f, dir);
            AppConfig config = (AppConfig) field(f.frame, "config");
            java.util.concurrent.atomic.AtomicInteger exited = new java.util.concurrent.atomic.AtomicInteger(-1);
            f.frame.exitProcess = exited::set;
            onEdt(() -> session(f).setWrites(diskFull()));
            editAndSave(f, tmp);

            onEdt(() -> f.frame.dispatchEvent(new java.awt.event.WindowEvent(f.frame, java.awt.event.WindowEvent.WINDOW_CLOSING)));

            assertEquals(1, asked.size(), "★ the person is asked before quitting");
            assertTrue(asked.get(0).contains("No space left on device"), asked.get(0));
            assertEquals(-1, exited.get(), "★ the analyser did not exit");
            AtomicReference<Boolean> alive = new AtomicReference<>();
            onEdt(() -> alive.set(f.frame.isDisplayable() && f.frame.isEnabled() && field(f.frame, "store") != null));
            assertTrue(alive.get(), "the window, its log and its controls are untouched");
            assertTrue(config.sourceRoots.contains(tmp.resolve("src").toString()), "the edit is still in memory");

            MainFrame.heldLeaveChooser = reason -> new MainFrame.HeldLeaveAnswer(null, true);   // "Discard my edits"
            onEdt(() -> f.frame.dispatchEvent(new java.awt.event.WindowEvent(f.frame, java.awt.event.WindowEvent.WINDOW_CLOSING)));
            WalkPlaybackFrameTest.await("the explicit discard lets the quit go ahead", () -> exited.get() == 0);
        } finally {
            MainFrame.heldLeaveChooser = null;
        }
    }
}
