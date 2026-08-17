package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M20.3 — when opening a log should offer the project it sits in, and more importantly when it should
 * not. A prompt that reappears after you have declined it is what makes people turn a feature off.
 */
class ProjectAutoDetectTest {

    private final ProjectAutoDetect detect = new ProjectAutoDetect();

    private static Path repoWithProfile(Path dir, String name) throws Exception {
        Path profile = ProjectProfile.pathFor(dir.resolve(name));
        Files.createDirectories(profile.getParent());
        Files.writeString(profile, "share.version=1\n");
        return profile;
    }

    private static Path logUnder(Path dir, String repo) throws Exception {
        Path log = dir.resolve(repo).resolve("build/logs/audit.yaml");
        Files.createDirectories(log.getParent());
        Files.writeString(log, "eventLogRecord:\n");
        return log;
    }

    /** The M19 zero-setup path: open a bundle's log, be offered the bundle's project. */
    @Test
    void aLogInsideAProjectIsOffered(@TempDir Path dir) throws Exception {
        Path profile = repoWithProfile(dir, "bundle");
        Path log = logUnder(dir, "bundle");

        assertEquals(profile, detect.offerFor(log, null));
    }

    @Test
    void aLogWithNoProjectAboveItIsSilent(@TempDir Path dir) throws Exception {
        Path log = dir.resolve("loose/audit.yaml");
        Files.createDirectories(log.getParent());
        Files.writeString(log, "eventLogRecord:\n");

        assertNull(detect.offerFor(log, null));
    }

    /** Offering a project that is already loaded is noise, and noise is what gets features disabled. */
    @Test
    void theActiveProjectIsNotOfferedAgain(@TempDir Path dir) throws Exception {
        Path profile = repoWithProfile(dir, "bundle");
        Path log = logUnder(dir, "bundle");

        assertNull(detect.offerFor(log, profile));
        assertNull(detect.offerFor(log, profile.toAbsolutePath().normalize()),
                "the comparison must survive a differently-spelled but equal path");
    }

    /** The brief's rule: respect "no", and do not re-nag for the same log in the same session. */
    @Test
    void decliningStopsTheOfferForThatLogOnly(@TempDir Path dir) throws Exception {
        repoWithProfile(dir, "one");
        Path profileTwo = repoWithProfile(dir, "two");
        Path logOne = logUnder(dir, "one");
        Path logTwo = logUnder(dir, "two");

        assertNotNull(detect.offerFor(logOne, null));
        detect.decline(logOne);

        assertNull(detect.offerFor(logOne, null), "asked and answered");
        assertNull(detect.offerFor(logOne.toAbsolutePath().normalize(), null),
                "and answered however the same file is spelled");
        assertEquals(profileTwo, detect.offerFor(logTwo, null),
                "declining one log says nothing about another");
    }

    @Test
    void openingTheProjectByHandClearsTheDecline(@TempDir Path dir) throws Exception {
        Path profile = repoWithProfile(dir, "bundle");
        Path log = logUnder(dir, "bundle");

        detect.decline(log);
        assertNull(detect.offerFor(log, null));

        detect.clearDecline(log);
        assertEquals(profile, detect.offerFor(log, null));
    }

    /** An s3:// object streams to a temp file; a temp directory is not a project. */
    @Test
    void aLogWithNoLocalPathIsSilent() {
        assertNull(detect.offerFor(null, null));
    }

    /** Nested repositories: the nearest profile wins, not the outermost. */
    @Test
    void theNearestProjectWins(@TempDir Path dir) throws Exception {
        repoWithProfile(dir, "outer");
        Path inner = repoWithProfile(dir.resolve("outer"), "inner");
        Path log = dir.resolve("outer/inner/logs/audit.yaml");
        Files.createDirectories(log.getParent());
        Files.writeString(log, "eventLogRecord:\n");

        assertEquals(inner, detect.offerFor(log, null),
                "a log inside a nested project belongs to that project, not its parent");
    }
}
