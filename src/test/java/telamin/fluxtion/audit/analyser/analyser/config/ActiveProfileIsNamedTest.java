package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Which profile is in force must be visible (#22).
 *
 * <p>A project root holds several profiles and switching between them is routine. Project edits
 * AUTO-SAVE into whichever is active, with no save step and no undo, so a title that reads the same
 * for all of them is how someone deletes charts from the wrong one. Found with four profiles under
 * one root all displaying "maker-fxoc".
 */
class ActiveProfileIsNamedTest {

    /** A real session with a real profile opened — no reflection, no faked wiring. */
    private static ProjectSession sessionOn(Path settingsFile) throws Exception {
        Files.createDirectories(settingsFile.getParent());
        ProjectProfile.save(settingsFile, new AppConfig(), new SettingsShare());
        ProjectSession s = new ProjectSession(new AppConfig(), new SettingsShare(), () -> { });
        assertTrue(s.open(settingsFile).loaded(), "the fixture profile must open");
        return s;
    }

    private static ProjectSession sessionWithNoProject() {
        return new ProjectSession(new AppConfig(), new SettingsShare(), () -> { });
    }

    @Test
    @DisplayName("The canonical profile shows the bare project name")
    void canonicalProfileIsJustTheProject(@TempDir Path dir) throws Exception {
        ProjectSession s = sessionOn(dir.resolve("maker-fxoc/.analyser/project.fluxtion-settings"));

        assertEquals("maker-fxoc", s.activeLabel(), "nothing to disambiguate, so nothing is added");
        assertNull(s.activeProfileName());
    }

    @Test
    @DisplayName("A named profile is named")
    void namedProfileIsDistinguished(@TempDir Path dir) throws Exception {
        ProjectSession s = sessionOn(dir.resolve("maker-fxoc/.analyser/project.reciprocal.fluxtion-settings"));

        assertEquals("maker-fxoc — reciprocal", s.activeLabel());
        assertEquals("reciprocal", s.activeProfileName());
    }

    @Test
    @DisplayName("Two profiles under ONE root are distinguishable — the defect this fixes")
    void profilesUnderOneRootDiffer(@TempDir Path dir) throws Exception {
        ProjectSession a = sessionOn(dir.resolve("maker-fxoc/.analyser/project.reciprocal.fluxtion-settings"));
        ProjectSession b = sessionOn(dir.resolve("maker-fxoc/.analyser/project.exp-2026-09-25.fluxtion-settings"));

        assertEquals(a.activeName(), b.activeName(),
                "they ARE the same project — activeName is right to say so");
        assertNotEquals(a.activeLabel(), b.activeLabel(),
                "but a person must be able to tell which one edits will auto-save into");
    }

    @Test
    @DisplayName("activeName still returns the project — callers keyed on it must not move")
    void activeNameIsUnchanged(@TempDir Path dir) throws Exception {
        ProjectSession s = sessionOn(dir.resolve("maker-fxoc/.analyser/project.reciprocal.fluxtion-settings"));

        assertEquals("maker-fxoc", s.activeName(),
                "cache keys, report headers and context.project.name all key on the PROJECT, not the "
                        + "profile; adding a label must not rename the project");
    }

    @Test
    @DisplayName("A profile name with dots survives")
    void dottedProfileName(@TempDir Path dir) throws Exception {
        ProjectSession s = sessionOn(dir.resolve("maker-fxoc/.analyser/project.exp-2026-09-25-contra.fluxtion-settings"));

        assertEquals("exp-2026-09-25-contra", s.activeProfileName(),
                "only the project. prefix and the .fluxtion-settings suffix are stripped");
    }

    @Test
    @DisplayName("A file that is not a project profile has no profile name")
    void nonProfileFile(@TempDir Path dir) throws Exception {
        ProjectSession s = sessionOn(dir.resolve("maker-fxoc/.analyser/shared.fluxtion-settings"));

        assertNull(s.activeProfileName(), "isProjectProfileFileName decides, not the presence of a dot");
        assertEquals("maker-fxoc", s.activeLabel());
    }

    @Test
    @DisplayName("No project: empty, not a crash")
    void noProject() {
        ProjectSession s = sessionWithNoProject();

        assertEquals("", s.activeName());
        assertEquals("", s.activeLabel());
        assertNull(s.activeProfileName());
    }

    @Test
    @DisplayName("The panel row shows the settings FILE, which is the only distinguishing part")
    void panelRowNamesTheFile() {
        assertEquals("project.reciprocal.fluxtion-settings",
                telamin.fluxtion.audit.analyser.analyser.ui.ProjectModel
                        .fileNameOf("/w/maker-fxoc/.analyser/project.reciprocal.fluxtion-settings"));
        assertNull(telamin.fluxtion.audit.analyser.analyser.ui.ProjectModel.fileNameOf(null));
    }
}
