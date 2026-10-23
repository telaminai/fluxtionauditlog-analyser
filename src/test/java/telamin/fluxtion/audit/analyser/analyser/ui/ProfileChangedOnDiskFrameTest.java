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

    @Test
    @DisplayName("★ #177: an outside edit is offered, survives an auto-save, and open {project, reload: true} reads it (log stays open)")
    @SuppressWarnings("unchecked")
    void anOutsideEditIsOfferedKeptAndReloaded(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path dir = Files.createDirectories(tmp.resolve("demo-project"));
        Path file = ProjectProfile.pathFor(dir);
        AppConfig seed = new AppConfig();
        seed.eventProcessorFqns.addAll(processors(1));
        ProjectProfile.save(file, seed, new SettingsShare());
        List<String> asked = new ArrayList<>();
        var answer = new AtomicReference<>(SessionEvents.ProfileChoice.LATER);
        MainFrame.profileReloadChooser = reason -> { asked.add(reason); return answer.get(); };
        try (var f = EvidenceCaptureFrameTest.shown(tmp)) {
            onEdt(() -> render(f.ex, "open", Map.of("project", dir.toString())));
            EvidenceCaptureFrameTest.openLog(f, EvidenceCaptureFrameTest.DEMO_LOG);
            AppConfig config = (AppConfig) field(f.frame, "config");
            // the test observes by hand: the frame's own 1.5 s watch would race it on a slow display
            onEdt(() -> ((javax.swing.Timer) field(f.frame, "profileWatchTimer")).stop());
            assertEquals(processors(1), named(config.eventProcessorFqns), "control: one processor");
            assertNull(profileOnDisk(f), "control: in step, nothing to say");

            externalEdit(file, 10);
            onEdt(() -> f.frame.observeProfile(false));
            WalkPlaybackFrameTest.await("the offer was made", () -> !asked.isEmpty());
            assertTrue(asked.get(0).startsWith("Project settings changed on disk"), asked.get(0));
            Map<String, Object> state = profileOnDisk(f);
            assertEquals("changedOnDisk", state.get("state"), "context states it: " + state);

            // "not now", then an in-memory edit and the debounced save: the file is NOT overwritten
            onEdt(() -> {
                config.sourceRoots.add(tmp.resolve("src").toString());
                ((telamin.fluxtion.audit.analyser.analyser.config.ProjectSession) field(f.frame, "project")).requestSave();
            });
            flushProject(f);
            assertEquals(processors(10), onDisk(file), "★ the outside edit survives the auto-save (red before #177: reverted to 1)");
            assertEquals(Boolean.TRUE, profileOnDisk(f).get("saveRefused"), "and the refusal is stated");
            assertEquals(1, asked.size(), "one change is offered once");

            AtomicReference<Map<String, Object>> reply = new AtomicReference<>();
            onEdt(() -> reply.set(render(f.ex, "open", Map.of("project", dir.toString(), "reload", true))));
            Map<String, Object> echo = (Map<String, Object>) reply.get().get("opened");
            assertEquals(Boolean.TRUE, echo.get("reloaded"), "★ open {project, reload: true} re-reads the file: " + echo);
            assertNotNull(echo.get("replaced"), "and says what it replaced: " + echo);
            assertTrue(String.valueOf(echo.get("note")).contains("unsaved edits were discarded"), "and what it discarded: " + echo);
            assertEquals(processors(10), named(config.eventProcessorFqns), "the session now has the ten");
            AtomicReference<Boolean> logOpen = new AtomicReference<>();
            onEdt(() -> logOpen.set(field(f.frame, "store") != null));
            assertTrue(logOpen.get(), "the log stays open: a reload is not a switch");
            assertEquals("inStep", profileOnDisk(f).get("state"));

            // "keep my version" is the only way this session's settings go over a newer file
            answer.set(SessionEvents.ProfileChoice.KEEP_MINE);
            externalEdit(file, 3);
            onEdt(() -> f.frame.observeProfile(false));
            try {
                WalkPlaybackFrameTest.await("the second offer, answered keep", () -> asked.size() >= 2
                        && onDisk(file).size() == 10);
            } catch (AssertionError timedOut) {
                throw new AssertionError(timedOut.getMessage() + " — asked=" + asked + " onDisk=" + onDisk(file)
                        + " profileOnDisk=" + profileOnDisk(f) + " status=" + f.status(), timedOut);
            }
            assertEquals(processors(10), onDisk(file), "the person's choice wrote the session's version");
        } finally {
            MainFrame.profileReloadChooser = null;
        }
    }
}
