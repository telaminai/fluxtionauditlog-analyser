package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile;
import telamin.fluxtion.audit.analyser.analyser.config.SettingsShare;
import telamin.fluxtion.audit.analyser.analyser.session.SessionAuditSink;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;
import telamin.fluxtion.audit.analyser.analyser.session.node.SessionRecovery;
import telamin.fluxtion.audit.analyser.analyser.session.resume.ResumeEvents;
import telamin.fluxtion.audit.analyser.analyser.session.resume.SessionResumeStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** Headless: the recovery controller's file-I/O adapter, without a frame. */
class SessionRecoveryControllerTest {
    @TempDir Path dir;

    @Test void closeWaitsForDelayedCaptureAdmissionBeforeFinishing() throws Exception {
        for (boolean accepted : List.of(true, false)) {
            Path folder = Files.createDirectories(dir.resolve("admission-" + accepted));
            Path log = Files.writeString(folder.resolve("DEMO.yaml"), "DEMO");
            var files = new SessionResumeStore(folder.resolve("sessions"));
            var answer = new java.util.concurrent.atomic.AtomicReference<Consumer<Boolean>>();
            var finished = new java.util.concurrent.CountDownLatch(1);
            var controller = new SessionRecoveryController(files, new SessionRecoveryController.Host() {
                public SessionDriver driver() { throw new AssertionError("no restore in this fixture"); }
                public SessionRecoveryController.Capture capture() {
                    return new SessionRecoveryController.Capture(null, null,
                            List.of(new SessionResumeStore.Input("log", log.toString())), Map.of());
                }
                public void admit(SessionRecoveryController.Capture capture, Consumer<Boolean> completion) { answer.set(completion); }
                public void render() { }
                public void apply(long generation, SessionRecovery.Plan plan, Consumer<ResumeEvents.Outcome> completion) {
                    throw new AssertionError("no restore in this fixture");
                }
                public void failed(String message) { fail(message); }
            });
            javax.swing.SwingUtilities.invokeAndWait(() -> controller.closeThen(finished::countDown));
            assertNotNull(answer.get(), "the admission request was observed");
            assertFalse(finished.await(250, java.util.concurrent.TimeUnit.MILLISECONDS),
                    "shutdown must not pass a capture whose node admission is still outstanding");
            javax.swing.SwingUtilities.invokeAndWait(() -> answer.get().accept(accepted));
            assertTrue(finished.await(5, java.util.concurrent.TimeUnit.SECONDS), "the node answer releases shutdown");
            assertEquals(accepted, files.load(SessionResumeStore.NO_PROJECT).isPresent(),
                    "shutdown saves admitted personal content but not the untouched first minute");
        }
    }

    /**
     * PR #28 review finding 4: the capturing profile's identity is taken when the capture is BUILT, with its
     * inputs, not when the queued background save runs. Here the project is deleted and a new profile created
     * at the same path in the window between the two; the saved session must still name the profile that
     * captured it, or it would be offered to the new one.
     */
    @Test void theCapturingIdentityIsTakenWhenTheCaptureIsBuiltNotWhenItIsSaved() throws Exception {
        Path project = Files.createDirectories(dir.resolve("project"));
        Path profile = ProjectProfile.pathFor(project);
        ProjectProfile.save(profile, new AppConfig(), new SettingsShare());
        String atBuild = ProjectProfile.nonce(profile).orElseThrow();
        Path log = Files.writeString(dir.resolve("run.yml"), "x");
        var store = new SessionResumeStore(dir.resolve("sessions"));
        var driver = new SessionDriver(e -> { throw new AssertionError("no session effect"); }, new SessionAuditSink());
        List<String> failures = new CopyOnWriteArrayList<>();
        var controller = new SessionRecoveryController(store, new SessionRecoveryController.Host() {
            public SessionDriver driver() { return driver; }
            public SessionRecoveryController.Capture capture() {
                var built = new SessionRecoveryController.Capture(profile, atBuild,
                        List.of(new SessionResumeStore.Input("log", log.toString())), Map.of());
                try {                                  // replaced at the same path before the queued save runs
                    deleteTree(project);
                    Files.createDirectories(project);
                    ProjectProfile.save(profile, new AppConfig(), new SettingsShare());
                } catch (Exception e) { throw new IllegalStateException(e); }
                return built;
            }
            public void render() { }
            public void apply(long generation, SessionRecovery.Plan plan, Consumer<ResumeEvents.Outcome> completion) {
                throw new AssertionError("nothing is restored here");
            }
            public void failed(String message) { failures.add(message); }
        });
        controller.capture();
        String key = SessionResumeStore.key(profile);
        for (int i = 0; i < 500 && store.load(key).isEmpty() && failures.isEmpty(); i++) Thread.sleep(10);
        assertEquals(List.of(), failures);
        String recreated = ProjectProfile.nonce(profile).orElseThrow();
        assertNotEquals(atBuild, recreated, "fixture: the profile at this path was really replaced");
        assertEquals(atBuild, store.load(key).orElseThrow().profileIdentity(),
                "the saved session names the profile that captured it, not the one at the path when the save ran");
    }

    private static void deleteTree(Path root) throws java.io.IOException {
        try (var walk = Files.walk(root)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(p);
        }
    }
}
