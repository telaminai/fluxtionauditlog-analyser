package telamin.fluxtion.audit.analyser.bundle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Convergence review, 2026-09-28: what a capture may clear from a SHARED exchange directory. The glob it replaced
 * deleted every {@code .capture-*} folder, a neighbour's capture in progress included; an age gate (proposed in review
 * {@code 097e0752}) would have guessed a bound nothing enforces and trusted clocks. A working folder is reaped only on
 * positive evidence its owner is dead: a marker from this host whose OS lock can be taken. The witness for "a live
 * owner" is a real second process, and the witness for "death releases it" is that process exiting.
 */
class BundleWriterReapTest {

    static Path folder(Path dir, String name, String host) throws Exception {
        Path f = Files.createDirectories(dir.resolve(name).resolve("bundle/log"));
        Path root = dir.resolve(name);
        if (host != null) Files.writeString(root.resolve(BundleWriter.OWNER), host, StandardCharsets.UTF_8);
        return root;
    }

    @Test
    @DisplayName("control: this host can be named, or nothing is provable and every case below would pass for free")
    void theHostIsKnown() {
        assertFalse(BundleWriter.HOST.isEmpty(), "the reaper needs this host's name");
    }

    @Test
    @DisplayName("a killed capture's folder (this host, lock free) is reaped when a refused bundle is deleted")
    void aKilledCapturesFolderIsReaped(@TempDir Path dir) throws Exception {
        Path out = Files.writeString(dir.resolve("refused.fexp"), "DEMO");
        Path corpse = folder(dir, ".capture-killed", BundleWriter.HOST);
        BundleWriter.delete(out);
        assertFalse(Files.exists(out), "the refused bundle goes");
        assertFalse(Files.exists(corpse), "and so does a folder whose owner is provably dead");
    }

    @Test
    @DisplayName("a live capture in ANOTHER process survives; once that process exits, the same folder is reaped")
    void aLiveNeighbourSurvivesAndIsReapedOnlyOnceItsOwnerDies(@TempDir Path dir) throws Exception {
        Path live = folder(dir, ".capture-neighbour", BundleWriter.HOST);
        Process holder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), CaptureLockHolder.class.getName(),
                live.resolve(BundleWriter.OWNER).toString()).redirectErrorStream(true).start();
        try {
            var in = new BufferedReader(new InputStreamReader(holder.getInputStream(), StandardCharsets.UTF_8));
            assertEquals("locked", in.readLine(), "control: the neighbour holds its lock");
            BundleWriter.delete(Files.writeString(dir.resolve("a.fexp"), "DEMO"));
            assertTrue(Files.exists(live.resolve("bundle/log")), "a neighbour's capture in progress must survive a refusal's cleanup");
        } finally {
            holder.getOutputStream().close();          // the neighbour finishes; the OS releases its lock
            assertTrue(holder.waitFor(30, TimeUnit.SECONDS));
        }
        BundleWriter.delete(Files.writeString(dir.resolve("b.fexp"), "DEMO"));
        assertFalse(Files.exists(live), "with its owner gone, the lock is free, and the same folder is a corpse");
    }

    @Test
    @DisplayName("a live capture in THIS JVM survives")
    void aLiveCaptureInThisJvmSurvives(@TempDir Path dir) throws Exception {
        Path live = folder(dir, ".capture-mine", BundleWriter.HOST);
        try (FileChannel ch = FileChannel.open(live.resolve(BundleWriter.OWNER), StandardOpenOption.WRITE)) {
            ch.lock();
            BundleWriter.delete(Files.writeString(dir.resolve("a.fexp"), "DEMO"));
            assertTrue(Files.exists(live), "a capture this JVM is running is never reaped");
        }
    }

    @Test
    @DisplayName("a folder a capture has CLAIMED survives a reap for as long as the claim is held")
    void aClaimedFolderSurvives(@TempDir Path dir) throws Exception {
        Path live = Files.createDirectories(dir.resolve(".capture-claimed"));
        try (var claim = BundleWriter.claim(live)) {
            BundleWriter.reapCorpses(dir);
            assertTrue(Files.exists(live), "the capture's own claim is what protects its folder while it runs");
        }
        BundleWriter.reapCorpses(dir);
        assertFalse(Files.exists(live), "control: released, the same folder is a corpse");
    }

    @Test
    @DisplayName("an unmarked .capture-* folder is never reaped, however old: age plays no part")
    void anUnmarkedFolderIsNeverReaped(@TempDir Path dir) throws Exception {
        Path lookalike = folder(dir, ".capture-notes-of-my-own", null);
        Files.setLastModifiedTime(lookalike, FileTime.from(Instant.now().minus(Duration.ofDays(30))));
        BundleWriter.delete(Files.writeString(dir.resolve("a.fexp"), "DEMO"));
        assertTrue(Files.exists(lookalike), "a folder nothing marked as a capture's is not provably one");
    }

    @Test
    @DisplayName("another host's folder is never reaped: on a shared mount its lock proves nothing here")
    void anotherHostsFolderIsNeverReaped(@TempDir Path dir) throws Exception {
        Path theirs = folder(dir, ".capture-elsewhere", "DEMO-other-host");
        BundleWriter.delete(Files.writeString(dir.resolve("a.fexp"), "DEMO"));
        assertTrue(Files.exists(theirs), "another machine's capture is not this one's to judge");
    }

    @Test
    @DisplayName("deleting an output that is already gone is not an error, and other bundles are left alone")
    void deletingIsIdempotentAndNarrow(@TempDir Path dir) throws Exception {
        Path mine = Files.writeString(dir.resolve("mine.fexp"), "x");
        Path other = Files.writeString(dir.resolve("someone.fexp"), "another bundle entirely");
        BundleWriter.delete(mine);
        assertDoesNotThrow(() -> BundleWriter.delete(mine), "a refusal that throws while cleaning up turns one failure into two");
        assertTrue(Files.exists(other));
    }

    @Test
    @DisplayName("a capture's own folder holds the lock while it runs, and the marker is never a bundle member")
    void aWrittenBundleCarriesNoMarker(@TempDir Path dir) throws Exception {
        Path ex = Files.createDirectories(dir.resolve("exchange"));
        folder(ex, ".capture-killed-earlier", BundleWriter.HOST);    // a corpse from an earlier, killed capture
        var job = new BundleWriter.Job(ex.resolve("w.fexp"), BundleExcerptTest.DEMO_LOG, null, "project.fluxtion-settings",
                Files.readAllBytes(BundleProfileTest.FIXTURE), "# DEMO", null, Instant.now(), "test", 256, null);
        BundleWriter.write(job);
        var v = EvidenceBundle.verify(ex.resolve("w.fexp"));
        assertTrue(v.ok(), v.refusal());
        assertEquals(List.of("log/demo-quote-audit.yaml", "notes/NOTES.md", "profile/project.fluxtion-settings"),
                v.members().stream().map(EvidenceBundle.Member::path).toList(), "exactly the members, no owner marker");
        try (var list = Files.list(ex)) {
            assertEquals(List.of(ex.resolve("w.fexp")), list.toList(),
                    "the working folder is gone, and so is the earlier corpse: a capture reaps before it starts");
        }
    }
}
