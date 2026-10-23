package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #177: an external edit to the open project's {@code .analyser/project.fluxtion-settings} was silently overwritten by
 * the next auto-save — observed: a ten-processor declaration list reverted to one. A write is now made only while the
 * file still holds what this session last read or wrote; anything newer is kept, the refusal is stated once, and the
 * person chooses: reload from disk, or keep this session's version.
 */
class ProfileChangedOnDiskTest {

    private final SettingsShare share = new SettingsShare("/home/tester");

    static List<String> processors(int n) {
        return IntStream.rangeClosed(1, n).mapToObj(i -> "com.acme.demo.Processor" + i).toList();
    }

    /** Write a profile at {@code file} as ANOTHER writer would: an editor, an agent, a teammate's pull. */
    private void externalEdit(Path file, List<String> fqns) throws Exception {
        AppConfig other = new AppConfig();
        ProjectProfile.load(file, other, share);
        other.eventProcessorFqns.clear();
        other.eventProcessorFqns.addAll(fqns);
        Files.writeString(file, ProjectProfile.write(other, share, file));
    }

    private ProjectSession openedWithOneProcessor(Path dir, AppConfig c) throws Exception {
        Path file = ProjectProfile.pathFor(dir);
        AppConfig seed = new AppConfig();
        seed.eventProcessorFqns.addAll(processors(1));
        ProjectProfile.save(file, seed, share);
        ProjectSession s = new ProjectSession(c, share, () -> { });
        assertTrue(s.open(file).loaded());
        assertEquals(processors(1), c.eventProcessorFqns, "control: the project is open with one processor");
        return s;
    }

    @Test
    @DisplayName("★ #177: edit the profile on disk while the project is open, auto-save — the edit SURVIVES (red before: reverted to 1)")
    void anExternalEditSurvivesAutoSave(@TempDir Path dir) throws Exception {
        AppConfig c = new AppConfig();
        ProjectSession s = openedWithOneProcessor(dir, c);
        Path file = s.activeFile();

        externalEdit(file, processors(10));
        c.sourceRoots.add("/work/src");          // an in-memory edit, so the debounced save has something to write
        s.requestSave();
        s.flush();

        AppConfig onDisk = new AppConfig();
        ProjectProfile.load(file, onDisk, share);
        assertEquals(processors(10), onDisk.eventProcessorFqns, "★ the ten-processor edit on disk is not overwritten");
        assertTrue(s.isDirty(), "the session's own edit is kept in memory, not lost");
        assertNotNull(s.conflict(), "the refusal is a stated fact");
        String error = s.takeError();
        assertNotNull(error, "and reported once");
        assertTrue(error.contains("changed on disk"), error);
        s.flush();
        assertNull(s.takeError(), "the same conflict is not reported again on every debounce");
        assertEquals(processors(10), onDiskProcessors(file), "a second save attempt does not overwrite it either");
    }

    @Test
    @DisplayName("#177: Reload from disk takes the on-disk settings and clears the conflict; the next save writes normally")
    void reloadTakesTheDiskVersion(@TempDir Path dir) throws Exception {
        AppConfig c = new AppConfig();
        ProjectSession s = openedWithOneProcessor(dir, c);
        externalEdit(s.activeFile(), processors(10));
        c.sourceRoots.add("/work/src");
        s.requestSave();
        s.flush();

        assertTrue(s.reload().loaded());
        assertEquals(processors(10), c.eventProcessorFqns, "the session now has the ten");
        assertFalse(s.isDirty());
        assertNull(s.conflict());
        c.eventProcessorFqns.add("com.acme.demo.Eleventh");
        s.requestSave();
        s.flush();
        assertEquals(11, onDiskProcessors(s.activeFile()).size(), "and saving resumes from the reloaded version");
    }

    @Test
    @DisplayName("#177: Keep my version is the person's choice — only then is the newer file overwritten")
    void keepMineOverwritesOnlyWhenChosen(@TempDir Path dir) throws Exception {
        AppConfig c = new AppConfig();
        ProjectSession s = openedWithOneProcessor(dir, c);
        externalEdit(s.activeFile(), processors(10));
        c.eventProcessorFqns.add("com.acme.demo.Mine");
        s.requestSave();
        s.flush();
        assertEquals(10, onDiskProcessors(s.activeFile()).size(), "control: refused");

        s.keepMine();
        assertEquals(List.of("com.acme.demo.Processor1", "com.acme.demo.Mine"), onDiskProcessors(s.activeFile()),
                "the person's choice writes the session's version");
        assertNull(s.conflict());
        assertFalse(s.isDirty());
    }

    @Test
    @DisplayName("#177: the session's own writes never look like someone else's — repeated auto-saves keep working")
    void ownWritesAreNotConflicts(@TempDir Path dir) throws Exception {
        AppConfig c = new AppConfig();
        ProjectSession s = openedWithOneProcessor(dir, c);
        for (int i = 2; i <= 4; i++) {
            c.eventProcessorFqns.add("com.acme.demo.P" + i);
            s.requestSave();
            s.flush();
            assertNull(s.conflict(), "write " + i);
        }
        assertEquals(4, onDiskProcessors(s.activeFile()).size());
    }

    private List<String> onDiskProcessors(Path file) {
        AppConfig onDisk = new AppConfig();
        ProjectProfile.load(file, onDisk, share);
        return List.copyOf(onDisk.eventProcessorFqns);
    }
}
