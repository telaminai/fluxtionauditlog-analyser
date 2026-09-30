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

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("a NAMED profile of the open project is still that project — no offer")
    void aNamedProfileOfTheSameProjectIsNotOffered(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        Path root = java.nio.file.Files.createDirectories(tmp.resolve("maker"));
        Path canonical = ProjectProfile.pathFor(root);
        java.nio.file.Files.createDirectories(canonical.getParent());
        java.nio.file.Files.writeString(canonical, "sourceRoot.count=0\n");
        Path named = canonical.getParent().resolve("project.reciprocal.fluxtion-settings");
        java.nio.file.Files.writeString(named, "sourceRoot.count=0\n");
        Path log = java.nio.file.Files.writeString(
                java.nio.file.Files.createDirectories(root.resolve("logs")).resolve("run.yaml"), "x");

        // the named profile is in force; the log lives inside the very same project
        assertNull(new ProjectAutoDetect().offerFor(log, named),
                "theProjectIsAlreadyOpen — offering it switches you off your own named profile");
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("a log in a DIFFERENT project is still offered")
    void anotherProjectIsStillOffered(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        Path mine = java.nio.file.Files.createDirectories(tmp.resolve("mine"));
        Path other = java.nio.file.Files.createDirectories(tmp.resolve("other"));
        Path minesProfile = ProjectProfile.pathFor(mine);
        java.nio.file.Files.createDirectories(minesProfile.getParent());
        java.nio.file.Files.writeString(minesProfile, "sourceRoot.count=0\n");
        Path othersProfile = ProjectProfile.pathFor(other);
        java.nio.file.Files.createDirectories(othersProfile.getParent());
        java.nio.file.Files.writeString(othersProfile, "sourceRoot.count=0\n");
        Path log = java.nio.file.Files.writeString(
                java.nio.file.Files.createDirectories(other.resolve("logs")).resolve("run.yaml"), "x");

        assertEquals(othersProfile, new ProjectAutoDetect().offerFor(log, minesProfile),
                "adifferentProjectIsStillWorthOffering");
    }
}
