package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OD-74.22 (amends OD-74.5; review c7f2 F2; §10 "no dead end"): the sample project and an evidence bundle's working copy
 * are the analyser's own folders, so a Claude Code session for them works in the private scratch folder — and the Start
 * that S1 and S3 arm is one the session ACCEPTS there. Any other refused folder is still refused.
 */
class ScratchFolderTest {

    static LocalAgentConsents.Settings on() {
        return new LocalAgentConsents.Settings(true, false, "default", List.of(), List.of(), null);
    }

    /** ★ Witness: give the session the project root (cwd = the demo root) — the folder verdict is EXPOSES_SECRETS. */
    @Test
    @DisplayName("★ S1's sample project and S3's bundle copy work in the scratch folder, whose Start is accepted")
    void analyserOwnedRootsUseTheScratchFolder(@TempDir Path tmp) throws Exception {
        Path home = Files.createDirectories(tmp.resolve("home"));
        Path demo = Files.createDirectories(home.resolve(".fluxtion-analyser/demo"));
        Path bundle = Files.createDirectories(home.resolve(".fluxtion-analyser/bundles/bundle-abc"));
        Path elsewhere = Files.createDirectories(tmp.resolve("work/my-project"));
        Path scratch = Files.createDirectories(tmp.resolve("scratch"));
        for (Path root : List.of(demo, bundle)) {
            assertTrue(LocalAgentFolders.analyserOwned(root, home, p -> false), root.toString());
            Path dir = LocalAgentFolders.workingDirectory(root, home, p -> false, () -> scratch);
            assertEquals(scratch, dir, "the scratch folder, never the analyser-owned root");
            var consent = LocalAgentConsents.of(dir, scratch, home, on(), true, p -> false);
            assertEquals(SessionEvents.FolderVerdict.OK, consent.folder());
            assertTrue(consent.confirmedForPosture(), "the scratch folder holds nothing to trust");
            assertFalse(consent.bundleWorkingCopy());
            var witness = LocalAgentConsents.of(root, scratch, home, on(), true, p -> false);
            assertEquals(SessionEvents.FolderVerdict.EXPOSES_SECRETS, witness.folder(), "the root itself is refused");
        }
        assertEquals(elsewhere, LocalAgentFolders.workingDirectory(elsewhere, home, p -> false, () -> scratch),
                "a person's own project is its own folder");
        assertFalse(LocalAgentFolders.analyserOwned(home, home, p -> false), "the home folder is not 'owned': still refused");
        assertEquals(scratch, LocalAgentFolders.workingDirectory(null, home, p -> false, () -> scratch), "no project");
        Path other = Files.createDirectories(tmp.resolve("received"));
        assertTrue(LocalAgentFolders.analyserOwned(other, home, p -> p.equals(other)), "recognised as a working copy");
    }
}
