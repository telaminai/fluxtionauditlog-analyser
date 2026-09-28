package telamin.fluxtion.audit.analyser.bundle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Review of the reaper: reaping must not release the lock of a capture running in THIS JVM.
 *
 * <p>On POSIX an {@code fcntl} lock belongs to the process, not to the descriptor: closing any descriptor to the
 * file releases every lock the process holds on it. {@code reapCorpses} used to open a channel to each candidate
 * marker, including one this JVM owned — it caught the {@code OverlappingFileLockException} and correctly
 * treated the capture as live, and then try-with-resources closed the channel, which disarmed the lock that had
 * just proved it.
 *
 * <p>The holder cannot see this. Its {@code FileLock.isValid()} still returns true, because the JDK keeps a
 * per-JVM lock table that the operating system knows nothing about. **Only another process can tell**, which is
 * why this test asks one. That other process is another analyser's reaper, which would then delete a capture
 * that is still running.
 */
class ReapDoesNotDisarmItsOwnLockTest {

    /** Ask a second process whether the marker's lock is free. */
    private static String asAnotherProcess(Path marker) throws Exception {
        Process p = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), CaptureLockTaker.class.getName(), marker.toString())
                .redirectErrorStream(true).start();
        String said = new String(p.getInputStream().readAllBytes()).trim();
        p.waitFor();
        return said;
    }

    @Test
    @DisplayName("A capture's own lock survives its own reap, as another process sees it")
    void reapingDoesNotReleaseThisJvmsLock(@TempDir Path exchange) throws Exception {
        Path folder = Files.createDirectory(exchange.resolve(".capture-mine"));
        try (var owner = BundleWriter.claim(folder)) {
            Path marker = folder.resolve(BundleWriter.OWNER);
            assertEquals("held", asAnotherProcess(marker), "control: a live capture's marker is locked");
            assertTrue(BundleWriter.ownedHere(marker), "control: this JVM knows it owns it");

            BundleWriter.reapCorpses(exchange);          // our own reap, over our own live marker

            assertTrue(Files.isDirectory(folder), "our own folder is not a corpse and must survive");
            assertEquals("held", asAnotherProcess(marker),
                    "reaping released this capture's OWN lock. Another analyser's reaper would now read the "
                            + "marker as free, conclude the owner is dead, and delete a capture that is still "
                            + "running. The holder cannot detect it: FileLock.isValid() still says true");
        }
    }

    @Test
    @DisplayName("A marker this JVM owns is skipped without being opened, twice over")
    void ownershipIsSettledByPath(@TempDir Path exchange) throws Exception {
        Path folder = Files.createDirectory(exchange.resolve(".capture-mine"));
        try (var owner = BundleWriter.claim(folder)) {
            BundleWriter.reapCorpses(exchange);
            BundleWriter.reapCorpses(exchange);          // a second capture starting: still must not disarm us
            assertEquals("held", asAnotherProcess(folder.resolve(BundleWriter.OWNER)),
                    "every reap is a chance to disarm ourselves, so once is not enough to test");
        }
    }

    @Test
    @DisplayName("the same folder reached another way (a symlinked exchange directory) is still recognised as ours")
    void ownershipSurvivesAnotherSpellingOfThePath(@TempDir Path tmp) throws Exception {
        Path real = Files.createDirectory(tmp.resolve("exchange"));
        Path link = Files.createSymbolicLink(tmp.resolve("exchange-link"), real);
        Path folder = Files.createDirectory(real.resolve(".capture-mine"));
        try (var owner = BundleWriter.claim(folder)) {
            BundleWriter.reapCorpses(link);              // the reaper reaches the folder through the link
            assertTrue(Files.isDirectory(folder), "our own folder survives");
            assertEquals("held", asAnotherProcess(folder.resolve(BundleWriter.OWNER)),
                    "a path spelled another way must not look unowned, or the reap opens the marker and disarms us");
        }
    }
}
