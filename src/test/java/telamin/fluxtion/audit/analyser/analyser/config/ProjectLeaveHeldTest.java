package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F1 (#198 review): leaving a project — a switch, a create, a close or a quit — must not discard unsaved project edits
 * when neither the profile nor the {@code .unsaved-*} rescue copy can be written. Before, {@code leave()} noticed the
 * failed rescue write, recorded an error and returned normally; {@code close()} then restored the no-project defaults and
 * the edit was gone. Now leaving returns an explicit {@link ProjectSession.Leave}, and a held leave changes nothing until
 * the person saves the edits elsewhere or discards them.
 *
 * <p>Failures are simulated with an injectable writer ({@link ProjectSession#setWrites}) — a full disk, a denied
 * permission — except the reviewer's reproduction, which uses a real unwritable directory.
 */
class ProjectLeaveHeldTest {

    private static final String EDIT = "com.acme.DEMOUnsaved";
    private final SettingsShare share = new SettingsShare("");

    /** A writer whose every write fails as {@code failure} does. */
    private static ProjectSession.Writes failing(IOException failure) {
        return new ProjectSession.Writes() {
            @Override
            public void saveProfile(Path file, AppConfig config, SettingsShare share) throws IOException {
                throw failure;
            }

            @Override
            public void writeNew(Path file, String text) throws IOException {
                throw failure;
            }
        };
    }

    private static IOException diskFull() {
        return new IOException("No space left on device");
    }

    private ProjectSession openedWithAnEdit(Path dir, AppConfig live) throws Exception {
        Path file = ProjectProfile.pathFor(dir);
        AppConfig seed = new AppConfig();
        seed.eventProcessorFqns.add("com.acme.DEMOOriginal");
        ProjectProfile.save(file, seed, share);
        ProjectSession session = new ProjectSession(live, share, () -> { });
        assertTrue(session.open(file).loaded());
        live.eventProcessorFqns.add(EDIT);
        session.requestSave();
        return session;
    }

    private static void assertStillOpenWithTheEdit(ProjectSession session, AppConfig live, Path file) {
        assertEquals(file, session.activeFile(), "the project stays open");
        assertEquals(file.toString(), live.activeProjectPath, "and stays the active project");
        assertTrue(live.eventProcessorFqns.contains(EDIT), "the unsaved edit is still in memory");
        assertTrue(session.isDirty(), "and still unsaved");
        assertNotNull(session.heldReason(), "the session says why it did not leave");
    }

    @Test
    @DisplayName("★ reviewer F1: close after a conflict with an unwritable directory — the unsaved edit survives (red before: discarded)")
    void failedSideFileMustNotDiscardUnsavedEdits(@TempDir Path dir) throws Exception {
        Path file = ProjectProfile.pathFor(dir);
        AppConfig seed = new AppConfig();
        seed.eventProcessorFqns.add("com.acme.DEMOOriginal");
        ProjectProfile.save(file, seed, share);
        AppConfig live = new AppConfig();
        ProjectSession session = new ProjectSession(live, share, () -> { });
        assertTrue(session.open(file).loaded());
        AppConfig disk = new AppConfig();
        assertTrue(ProjectProfile.load(file, disk, share).loaded());
        disk.eventProcessorFqns.add("com.acme.DEMOOutside");
        ProjectProfile.save(file, disk, share);
        live.eventProcessorFqns.add(EDIT);
        session.requestSave();
        session.flush();
        assertNotNull(session.conflict());
        var permissions = Files.getPosixFilePermissions(file.getParent());
        try {
            Files.setPosixFilePermissions(file.getParent(), PosixFilePermissions.fromString("r-x------"));
            assertFalse(Files.isWritable(file.getParent()), "reproduction really has an unwritable directory");
            ProjectSession.Leave left = session.close();
            assertNull(session.takeSetAside(), "no rescue file was written");
            assertNotNull(session.takeError(), "save failure was detected");
            assertTrue(live.eventProcessorFqns.contains(EDIT), "unsaved edit must survive if the rescue file cannot be written");
            assertFalse(left.mayLeave(), "★ the close is held");
            assertStillOpenWithTheEdit(session, live, file);
        } finally {
            Files.setPosixFilePermissions(file.getParent(), permissions);
        }
    }

    @Test
    @DisplayName("★ close on a full disk (no conflict): held, the project and its edit stay")
    void aFullDiskHoldsTheClose(@TempDir Path dir) throws Exception {
        AppConfig live = new AppConfig();
        ProjectSession session = openedWithAnEdit(dir, live);
        Path file = session.activeFile();
        session.setWrites(failing(diskFull()));

        ProjectSession.Leave left = session.close();

        assertEquals(ProjectSession.Leave.Outcome.HELD, left.outcome());
        assertTrue(left.reason().contains("No space left on device"), left.reason());
        assertStillOpenWithTheEdit(session, live, file);
        assertFalse(Files.readString(file).contains(EDIT), "control: the profile was never written");
    }

    @Test
    @DisplayName("★ switch with a denied permission: the other project is not opened and the edit stays")
    void aDeniedPermissionHoldsTheSwitch(@TempDir Path dir) throws Exception {
        AppConfig live = new AppConfig();
        ProjectSession session = openedWithAnEdit(Files.createDirectories(dir.resolve("one")), live);
        Path file = session.activeFile();
        Path other = ProjectProfile.pathFor(Files.createDirectories(dir.resolve("two")));
        AppConfig otherSeed = new AppConfig();
        otherSeed.eventProcessorFqns.add("com.acme.DEMOOther");
        ProjectProfile.save(other, otherSeed, share);
        session.setWrites(failing(new AccessDeniedException(file.toString())));

        ProjectProfile.LoadResult result = session.open(other);

        assertFalse(result.loaded(), "★ the switch is held");
        assertNotNull(result.message());
        assertFalse(live.eventProcessorFqns.contains("com.acme.DEMOOther"), "the other project's settings were not loaded");
        assertStillOpenWithTheEdit(session, live, file);
    }

    @Test
    @DisplayName("★ new project on a full disk: held, nothing created, the edit stays")
    void aFullDiskHoldsTheCreate(@TempDir Path dir) throws Exception {
        AppConfig live = new AppConfig();
        ProjectSession session = openedWithAnEdit(Files.createDirectories(dir.resolve("one")), live);
        Path file = session.activeFile();
        Path fresh = ProjectProfile.pathFor(Files.createDirectories(dir.resolve("two")));
        session.setWrites(failing(diskFull()));

        assertThrows(IOException.class, () -> session.create(fresh), "★ the create is held");

        assertFalse(Files.exists(fresh), "nothing was created");
        assertStillOpenWithTheEdit(session, live, file);
    }

    @Test
    @DisplayName("★ quit on a full disk: held, the edit stays in memory (red before: the window was disposed regardless)")
    void aFullDiskHoldsTheQuit(@TempDir Path dir) throws Exception {
        AppConfig live = new AppConfig();
        ProjectSession session = openedWithAnEdit(dir, live);
        Path file = session.activeFile();
        session.setWrites(failing(diskFull()));

        ProjectSession.Leave left = session.leaveForQuit();

        assertFalse(left.mayLeave(), "★ quitting must wait for the person");
        assertStillOpenWithTheEdit(session, live, file);
    }

    @Test
    @DisplayName("quit held, then the person saves elsewhere: the edits are in that file and the quit may go ahead")
    void aHeldQuitSavedElsewhereMayLeave(@TempDir Path dir) throws Exception {
        AppConfig live = new AppConfig();
        ProjectSession session = openedWithAnEdit(Files.createDirectories(dir.resolve("one")), live);
        session.setWrites(failing(diskFull()));
        assertFalse(session.leaveForQuit().mayLeave());

        session.setWrites(null);                                       // the person picks a disk with room
        Path elsewhere = Files.createDirectories(dir.resolve("elsewhere")).resolve("project-edits.fluxtion-settings");
        session.setAsideTo(elsewhere);

        assertTrue(Files.readString(elsewhere).contains(EDIT), "the edit is in the chosen file");
        assertEquals(elsewhere.toString(), session.takeSetAside(), "and the session says where");
        assertTrue(session.leaveForQuit().mayLeave(), "the quit may now go ahead");
        assertNull(session.heldReason());
    }

    @Test
    @DisplayName("quit held, then the person explicitly discards: the quit may go ahead and nothing is written")
    void aHeldQuitDiscardedMayLeave(@TempDir Path dir) throws Exception {
        AppConfig live = new AppConfig();
        ProjectSession session = openedWithAnEdit(dir, live);
        Path file = session.activeFile();
        session.setWrites(failing(diskFull()));
        assertFalse(session.leaveForQuit().mayLeave());

        session.discardUnsaved();

        ProjectSession.Leave left = session.leaveForQuit();
        assertTrue(left.mayLeave(), "★ an explicit discard is the person's answer: the quit goes ahead");
        assertNull(session.takeSetAside(), "nothing was set aside");
        assertFalse(Files.readString(file).contains(EDIT), "and nothing was written");
    }

    @Test
    @DisplayName("held close saved elsewhere, then closed: the no-project settings are back")
    void aHeldCloseSavedElsewhereThenCloses(@TempDir Path dir) throws Exception {
        AppConfig live = new AppConfig();
        live.sourceRoots.add("/work/DEMO/own");                       // the person's own settings before any project
        ProjectSession session = openedWithAnEdit(Files.createDirectories(dir.resolve("one")), live);
        session.setWrites(failing(diskFull()));
        assertFalse(session.close().mayLeave());
        session.setWrites(null);
        session.setAsideTo(dir.resolve("kept.fluxtion-settings"));

        assertTrue(session.close().mayLeave());

        assertFalse(session.hasProject());
        assertEquals(java.util.List.of("/work/DEMO/own"), live.sourceRoots, "your own settings are back");
    }

    @Test
    @DisplayName("control: the profile write fails but the rescue copy lands — set aside, and leaving goes ahead")
    void aRescueCopyStillLetsTheCloseProceed(@TempDir Path dir) throws Exception {
        AppConfig live = new AppConfig();
        ProjectSession session = openedWithAnEdit(dir, live);
        session.setWrites(new ProjectSession.Writes() {
            @Override
            public void saveProfile(Path file, AppConfig config, SettingsShare share) throws IOException {
                throw new AccessDeniedException(file.toString());
            }

            @Override
            public void writeNew(Path file, String text) throws IOException {
                ProjectSession.Writes.FILES.writeNew(file, text);
            }
        });

        ProjectSession.Leave left = session.close();

        assertEquals(ProjectSession.Leave.Outcome.SET_ASIDE, left.outcome());
        assertTrue(Files.readString(Path.of(left.setAside())).contains(EDIT));
        assertFalse(session.hasProject());
    }
}
