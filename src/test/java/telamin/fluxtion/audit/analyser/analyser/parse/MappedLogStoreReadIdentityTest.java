package telamin.fluxtion.audit.analyser.analyser.parse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.*;

/** M68.5: the mapped store, on real files — the store that reads row bytes through its channel on demand. */
class MappedLogStoreReadIdentityTest {

    private static final String REC = "eventLogRecord:\n  logTime: 1\n  event: Tick\n  nodeLogs:\n    - aaa: { v: 1}\n---\n";

    private static Path file(Path dir) throws Exception {
        return Files.writeString(dir.resolve("big.yaml"), "---\n" + REC + REC.replace("aaa", "bbb"));
    }

    @Test
    @DisplayName("untouched: no change observed")
    void untouched(@TempDir Path dir) throws Exception {
        try (var s = new MappedLogStore(file(dir))) {
            assertNull(s.readThroughIdentity());
        }
    }

    @Test
    @DisplayName("the wrong-result witness: an in-place same-length rewrite changes what the rows read, so reads suspend")
    void inPlaceRewrite(@TempDir Path dir) throws Exception {
        Path f = file(dir);
        try (var s = new MappedLogStore(f)) {
            assertEquals("aaa", s.record(0).nodeLogs().get(0).instanceId());
            Files.writeString(f, Files.readString(f).replace("bbb", "zzz"));       // same inode, same length
            Files.setLastModifiedTime(f, FileTime.fromMillis(System.currentTimeMillis() + 60_000));
            assertTrue(s.rawText(1).contains("zzz"), "precondition: the channel really does read the rewritten bytes");
            var id = s.readThroughIdentity();
            assertNotNull(id);
            assertTrue(id.suspendsReads(), id.toString());
        }
    }

    @Test
    @DisplayName("an atomic replace: the open channel still reads the opened file, so it is labelled, not suspended")
    void atomicReplace(@TempDir Path dir) throws Exception {
        Path f = file(dir);
        try (var s = new MappedLogStore(f)) {
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    Files.readAttributes(f, java.nio.file.attribute.BasicFileAttributes.class).fileKey() != null);
            Path staged = Files.writeString(dir.resolve("staged.yaml"), "---\n" + REC.replace("aaa", "qqq"));
            Files.move(staged, f, StandardCopyOption.REPLACE_EXISTING);
            assertTrue(s.rawText(0).contains("aaa"), "the channel still reads the file that was opened");
            var id = s.readThroughIdentity();
            assertEquals(FollowIdentity.Verdict.REPLACEMENT, id.verdict());
            assertFalse(id.suspendsReads());
        }
    }
}
