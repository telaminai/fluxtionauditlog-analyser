package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M73 review m1 — {@link ProjectProfile#createNew} removes what it made when the write fails after the profile was
 * created, and never what was already there.
 *
 * <p>Witness (run 2026-10-15): drop the {@code if (madeFile) Files.deleteIfExists(file)} in the catch and the
 * half-written profile is left behind ("the profile this call created is removed").
 */
class ProjectProfileCreateNewTest {

    @TempDir Path tmp;

    private static void failingAfterOpen(Runnable body) {
        ProjectProfile.CreateNewSeam was = ProjectProfile.afterCreateNewOpen;
        ProjectProfile.afterCreateNewOpen = file -> { throw new IOException("DEMO disk full after open"); };
        try {
            body.run();
        } finally {
            ProjectProfile.afterCreateNewOpen = was;
        }
    }

    @Test
    @DisplayName("a write that fails after the file was created removes the file, and the .analyser it made")
    void aFailedWriteRemovesWhatItMade() throws Exception {
        Path file = ProjectProfile.pathFor(Files.createDirectories(tmp.resolve("p")));
        failingAfterOpen(() -> {
            IOException e = assertThrows(IOException.class,
                    () -> ProjectProfile.createNew(file, new AppConfig(), new SettingsShare()));
            assertEquals("DEMO disk full after open", e.getMessage());
        });
        assertFalse(Files.exists(file), "the profile this call created is removed");
        assertFalse(Files.exists(file.getParent()), "and the .analyser it made");
        ProjectProfile.createNew(file, new AppConfig(), new SettingsShare());
        assertTrue(Files.isRegularFile(file), "so the next create is not refused as 'exists'");
    }

    @Test
    @DisplayName("an .analyser that was already there stays, with what it held")
    void aDirectoryItDidNotMakeStays() throws Exception {
        Path dir = Files.createDirectories(tmp.resolve("q/.analyser"));
        Files.writeString(dir.resolve("project.other.fluxtion-settings"), "kept\n");
        Path file = dir.resolve("project.fluxtion-settings");
        failingAfterOpen(() -> assertThrows(IOException.class,
                () -> ProjectProfile.createNew(file, new AppConfig(), new SettingsShare())));
        assertFalse(Files.exists(file));
        assertEquals("kept\n", Files.readString(dir.resolve("project.other.fluxtion-settings")));
    }

    @Test
    @DisplayName("a profile that was already there is never removed by a failed create")
    void anExistingProfileIsNeverRemoved() throws Exception {
        Path dir = Files.createDirectories(tmp.resolve("r/.analyser"));
        Path file = dir.resolve("project.fluxtion-settings");
        Files.writeString(file, "theirs\n");
        assertThrows(java.nio.file.FileAlreadyExistsException.class,
                () -> ProjectProfile.createNew(file, new AppConfig(), new SettingsShare()));
        assertEquals("theirs\n", Files.readString(file));
    }
}
