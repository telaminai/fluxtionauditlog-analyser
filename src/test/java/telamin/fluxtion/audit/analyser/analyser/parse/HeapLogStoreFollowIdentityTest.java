package telamin.fluxtion.audit.analyser.analyser.parse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.*;

/** M68.5: the heap store decides identity before it indexes anything, on real files. */
class HeapLogStoreFollowIdentityTest {

    @Test
    void unreadableSameLengthReplacementRetiresTheVerifiedIdentity(@TempDir Path dir) throws Exception {
        Path f = file(dir, "aaa");
        HeapLogStore s = HeapLogStore.fromFile(f).forFollow();
        assertEquals(0, s.appendFrom(f), "control: an idle poll adds nothing");
        assertEquals(FollowIdentity.Verdict.UNCHANGED, s.followIdentity().verdict(), "control: bytes were verified");
        assertFalse(s.readIdentities().isEmpty(), "control: opening digest exists");
        byte[] replacement = Files.readAllBytes(f);
        replacement[10] = (byte) 0xff;
        Files.write(f, replacement);
        assertThrows(java.nio.charset.CharacterCodingException.class, () -> s.appendFrom(f),
                "the replacement cannot be decoded");
        assertAll("failed reads retire current-byte claims",
                () -> assertEquals(FollowIdentity.Verdict.UNVERIFIED, s.followIdentity().verdict(),
                        "a failed decode must not retain the earlier UNCHANGED identity"),
                () -> assertTrue(s.readIdentities().isEmpty(), "a replaced file must not retain its opening digest"),
                () -> assertFalse(s.sourceDiagnostics().getFirst().contains("bytes appended"),
                        "a replacement is not established to be an append"));
        assertEquals(0, s.appendFrom(f), "a quiet failed poll indexes nothing");
        assertEquals(FollowIdentity.Verdict.UNVERIFIED, s.followIdentity().verdict(),
                "a quiet failed poll cannot resurrect verification");
        assertEquals(1, s.size(), "the retained index is unchanged");
    }

    private static String record(String id) {
        return "eventLogRecord:\n  logTime: 1\n  event: Tick\n  nodeLogs:\n    - " + id + ": { v: 1}\n---\n";
    }

    private static Path file(Path dir, String... ids) throws Exception {
        StringBuilder s = new StringBuilder("---\n");
        for (String id : ids) s.append(record(id));
        return Files.writeString(dir.resolve("follow.yaml"), s.toString());
    }

    @Test
    @DisplayName("an ordinary append is indexed")
    void append(@TempDir Path dir) throws Exception {
        Path f = file(dir, "aaa", "bbb");
        HeapLogStore s = HeapLogStore.fromFile(f);
        Files.writeString(f, record("ccc"), StandardOpenOption.APPEND);
        assertEquals(1, s.appendFrom(f));
        assertEquals(FollowIdentity.Verdict.APPEND, s.followIdentity().verdict());
    }

    @Test
    @DisplayName("the wrong-result witness: a same-length rewrite used to return 0 and announce nothing")
    void sameLengthRewrite(@TempDir Path dir) throws Exception {
        Path f = file(dir, "aaa", "bbb");
        HeapLogStore s = HeapLogStore.fromFile(f);
        String now = Files.readString(f);
        Files.writeString(f, now.replace("aaa", "zzz"));
        assertEquals(-1, s.appendFrom(f), "the caller reopens");
        assertEquals(FollowIdentity.Verdict.REPLACEMENT, s.followIdentity().verdict());
        assertEquals("aaa", s.record(0).nodeLogs().get(0).instanceId(), "and nothing was indexed over the old rows");
    }

    @Test
    @DisplayName("a middle rewrite plus an append is not indexed as an append")
    void middleRewritePlusAppend(@TempDir Path dir) throws Exception {
        Path f = file(dir, "aaa", "bbb");
        HeapLogStore s = HeapLogStore.fromFile(f);
        Files.writeString(f, Files.readString(f).replace("aaa", "zzz") + record("ccc"));
        assertEquals(-1, s.appendFrom(f));
        assertEquals(2, s.size(), "no row added over rewritten content");
    }

    @Test
    @DisplayName("a touched file with identical bytes is UNCHANGED and indexes nothing")
    void touched(@TempDir Path dir) throws Exception {
        Path f = file(dir, "aaa");
        HeapLogStore s = HeapLogStore.fromFile(f);
        Files.setLastModifiedTime(f, FileTime.fromMillis(System.currentTimeMillis() + 60_000));
        assertEquals(0, s.appendFrom(f));
        assertEquals(FollowIdentity.Verdict.UNCHANGED, s.followIdentity().verdict());
    }

    @Test
    @DisplayName("a missing file is UNVERIFIED, not an exception and not 'unchanged'")
    void missing(@TempDir Path dir) throws Exception {
        Path f = file(dir, "aaa");
        HeapLogStore s = HeapLogStore.fromFile(f);
        Files.delete(f);
        assertEquals(0, s.appendFrom(f));
        assertEquals(FollowIdentity.Verdict.UNVERIFIED, s.followIdentity().verdict());
    }

    @Test
    @DisplayName("a different file at the same path is REPLACEMENT, even when it starts with the same bytes")
    void differentFileSamePath(@TempDir Path dir) throws Exception {
        Path f = file(dir, "aaa");
        HeapLogStore s = HeapLogStore.fromFile(f);
        Object key = Files.readAttributes(f, java.nio.file.attribute.BasicFileAttributes.class).fileKey();
        org.junit.jupiter.api.Assumptions.assumeTrue(key != null, "this filesystem gives no file key");
        String content = Files.readString(f) + record("bbb");
        Path other = Files.writeString(dir.resolve("staging.yaml"), content);
        Files.move(other, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING);   // an atomic replace: a new inode
        assertEquals(-1, s.appendFrom(f));
        assertEquals(FollowIdentity.Verdict.REPLACEMENT, s.followIdentity().verdict());
        assertTrue(s.followIdentity().reason().contains("different file"), s.followIdentity().reason());
    }
}
