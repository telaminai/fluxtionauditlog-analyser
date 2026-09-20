package telamin.fluxtion.audit.analyser.analyser.parse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.security.MessageDigest;
import static org.junit.jupiter.api.Assertions.*;

class FileReadIdentityTest {
    @TempDir Path tmp;
    @Test void rawByteDigestMatchesForHeapMappedAndEveryRolledMember() throws Exception {
        byte[] bytes = ("# café before records\r\n---\r\neventLogRecord:\r\n  logTime: 1000\r\n  event: Tick\r\n"
                + "  nodeLogs:\r\n    - child: {value: 1}\r\n---\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path a = Files.write(tmp.resolve("a.yml"),bytes);
        Path b = Files.writeString(tmp.resolve("b.yml"),new String(bytes,java.nio.charset.StandardCharsets.UTF_8).replace("1000","2000"));
        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        for (int threshold : List.of(0, 100)) {
            try (var store = LogStores.open(a,threshold)) {
                assertEquals(expected,store.readIdentities().getFirst().sha256());
                assertEquals(a.toRealPath().toString(),store.readIdentities().getFirst().path());
            }
            try (var store = RolledLogStore.open(List.of(a,b),threshold)) {
                assertEquals(2,store.readIdentities().size());
                assertEquals(expected,store.readIdentities().getFirst().sha256());
                assertNotEquals(expected,store.readIdentities().getLast().sha256());
            }
        }
        var heap = HeapLogStore.fromFile(a);
        Files.writeString(a,"\n# grow\n",StandardOpenOption.APPEND);
        heap.appendFrom(a);
        assertTrue(heap.readIdentities().isEmpty(),"follow must invalidate the opening identity");
    }
    @Test void incompleteAndConcurrentReadsNeverClaimAFullIdentity() throws Exception {
        Path p = Files.writeString(tmp.resolve("changing"),"0123456789");
        var partial = FileReadIdentity.begin(p);
        try (var in = partial.open()) { in.read(); }
        assertNull(partial.finish().sha256());
        var changed = FileReadIdentity.begin(p);
        try (var in = changed.open()) {
            in.readAllBytes();
            Files.writeString(p,"extra",StandardOpenOption.APPEND);
        }
        assertNull(changed.finish().sha256());
    }
    @Test void sameSizeAndTimeReplacementGetsDifferentIdentity() throws Exception {
        Path p = Files.writeString(tmp.resolve("same"),"first");
        var time = Files.getLastModifiedTime(p);
        String first;
        var capture = FileReadIdentity.begin(p);
        try(var in = capture.open()) { in.readAllBytes(); }
        first = capture.finish().sha256();
        Files.writeString(p,"other"); Files.setLastModifiedTime(p,time);
        capture = FileReadIdentity.begin(p);
        try(var in = capture.open()) { in.readAllBytes(); }
        assertNotEquals(first,capture.finish().sha256());
    }
}
