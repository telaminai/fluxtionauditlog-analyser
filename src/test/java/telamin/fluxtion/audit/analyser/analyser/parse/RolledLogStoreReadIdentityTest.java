package telamin.fluxtion.audit.analyser.analyser.parse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Independent review R3 (2026-09-26): a rolled set delegates reads to its member stores, but inherited
 * {@code LogStore}'s null {@code readThroughIdentity()} — so a mapped member rewritten in place was served through the
 * set's old merged index while the same file opened alone was suspended. The set now reports its members' verdicts,
 * naming the member, and suspends reads if any member does. And null is no longer the only answer a store without the
 * check can give: {@link LogStore#readThroughAssessed()} says whether it was asked at all.
 */
class RolledLogStoreReadIdentityTest {

    private static final String RECORD = "eventLogRecord:\n  event: Tick\n  logTime: 1\n  nodeLogs:\n    - rootNode: {v: 1}\n---\n";

    @TempDir
    Path dir;

    private Path[] members() throws Exception {
        Path a = dir.resolve("a.yaml"), b = dir.resolve("b.yaml");
        Files.writeString(a, RECORD);
        Files.writeString(b, RECORD);
        return new Path[]{a, b};
    }

    /** Same length, new bytes, a later modification time: an in-place rewrite. */
    private static void rewriteInPlace(Path p) throws Exception {
        FileTime before = Files.getLastModifiedTime(p);
        Files.writeString(p, RECORD.replace("v: 1", "v: 9"));
        Files.setLastModifiedTime(p, FileTime.fromMillis(before.toMillis() + 2000));
    }

    @Test
    @DisplayName("R3: a mapped member rewritten in place — not the first — suspends the set's reads, naming the member")
    void aChangedMappedMemberSuspendsTheSet() throws Exception {
        Path[] m = members();
        try (var rolled = RolledLogStore.open(List.of(m[0], m[1]), 0)) {           // threshold 0: every member mapped
            assertNull(rolled.readThroughIdentity(), "nothing has changed yet");
            rewriteInPlace(m[1]);
            var identity = rolled.readThroughIdentity();
            assertNotNull(identity, "R3: the set reports its member's change");
            assertTrue(identity.suspendsReads(), "R3: a mapped member changed in place suspends the SET's reads: " + identity);
            assertTrue(identity.reason().contains("b.yaml") && identity.reason().contains("2 of 2"),
                    "R3: and names which member: " + identity.reason());
        }
    }

    @Test
    @DisplayName("R3: a retained-heap member that changed is labelled, not suspended — and named")
    void aChangedHeapMemberIsLabelled() throws Exception {
        Path[] m = members();
        try (var rolled = RolledLogStore.open(List.of(m[0], m[1]), 1024)) {        // well under the threshold: heap
            rewriteInPlace(m[1]);
            var identity = rolled.readThroughIdentity();
            assertNotNull(identity, "R3: the heap member's change is reported");
            assertFalse(identity.suspendsReads(), "its bytes are retained: " + identity);
            assertTrue(identity.reason().contains("b.yaml"), identity.reason());
        }
    }

    @Test
    @DisplayName("R3: a suspended member outranks a merely superseded one")
    void theMostSevereMemberSpeaksForTheSet() throws Exception {
        Path[] m = members();
        try (var rolled = RolledLogStore.open(List.of(m[0], m[1]), 0)) {
            Files.delete(m[0]);                                  // superseded: gone, bytes retained through the channel
            rewriteInPlace(m[1]);                                // suspended
            var identity = rolled.readThroughIdentity();
            assertTrue(identity.suspendsReads(), identity.toString());
            assertTrue(identity.reason().contains("b.yaml"), identity.reason());
        }
    }

    @Test
    @DisplayName("R3: whether freshness was assessed at all is stated — null never stands in for 'checked'")
    void assessmentIsExplicit() throws Exception {
        Path[] m = members();
        try (var rolled = RolledLogStore.open(List.of(m[0], m[1]), 0);
             var mapped = new MappedLogStore(m[0])) {
            assertTrue(mapped.readThroughAssessed());
            assertTrue(HeapLogStore.fromFile(m[0]).readThroughAssessed());
            assertTrue(rolled.readThroughAssessed(), "every member assesses, so the set does");
        }
        assertFalse(new HeapLogStore(RECORD).readThroughAssessed(), "a store with no file has nothing to assess");
        LogStore plugin = new LogStore() {
            public int size() { return 0; }
            public telamin.fluxtion.audit.analyser.analyser.index.LogIndex index() { return new telamin.fluxtion.audit.analyser.analyser.index.LogIndex(); }
            public telamin.fluxtion.audit.analyser.analyser.model.LogRecord record(int row) { return null; }
            public String rawText(int row) { return null; }
            public Long minLogTime() { return null; }
            public Long maxLogTime() { return null; }
        };
        assertFalse(plugin.readThroughAssessed(), "the SPI default: not assessed, stated as such");
    }
}
