package telamin.fluxtion.audit.analyser.analyser.net;

import telamin.fluxtion.audit.analyser.analyser.llm.ActionDispatcher;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.Samples;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The well-known REST endpoint file (spec-assistant-actions-mcp §3, M13.1): the round-trip a bridge
 * depends on, the pid liveness guard against a crash-stranded file, and publish-on-start /
 * delete-on-stop driven through a real {@link ActionServer}.
 */
class RestEndpointFileTest {

    @TempDir
    Path dir;

    private RestEndpointFile file() {
        return new RestEndpointFile(dir.resolve("rest-endpoint"));
    }

    @Test
    void writeReadRoundTrip() throws IOException {
        RestEndpointFile f = file();
        f.write("http://127.0.0.1:53411", "s3cr3t");

        RestEndpointFile.Endpoint e = f.read();
        assertNotNull(e);
        assertEquals("http://127.0.0.1:53411", e.url());
        assertEquals("s3cr3t", e.token());
        assertEquals(ProcessHandle.current().pid(), e.pid(), "pid field identifies the publishing process");
        assertNotNull(e.startedAt());
        assertTrue(e.alive(), "this JVM published it, so the liveness check must pass");
    }

    @Test
    void republishAtomicallyReplacesTheCompleteEndpoint_withoutLeavingPrivateSiblings() throws IOException {
        RestEndpointFile f = file();
        f.write("http://127.0.0.1:1", "old-token");

        f.write("http://127.0.0.1:2", "new-token");

        RestEndpointFile.Endpoint e = f.read();
        assertNotNull(e);
        assertEquals("http://127.0.0.1:2", e.url());
        assertEquals("new-token", e.token());
        try (var files = Files.list(dir)) {
            assertEquals(java.util.List.of(f.path()), files.toList(),
                    "the private write-then-move sibling is always cleaned up");
        }
    }

    @Test
    void createsMissingParentDirectories() throws IOException {
        RestEndpointFile f = new RestEndpointFile(dir.resolve("nested").resolve("deeper").resolve("rest-endpoint"));
        f.write("http://127.0.0.1:1", "t");
        assertNotNull(f.read());
    }

    @Test
    void fileIsOwnerOnlyWherePosix() throws IOException {
        RestEndpointFile f = file();
        f.write("http://127.0.0.1:53411", "s3cr3t");
        if (!Files.getFileStore(f.path()).supportsFileAttributeView("posix")) return;   // Windows: best-effort
        assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                Files.getPosixFilePermissions(f.path()), "the file carries the per-run token");
    }

    @Test
    void absentOrCorruptFileReadsAsNull() throws IOException {
        RestEndpointFile f = file();
        assertNull(f.read(), "absent file");

        Files.writeString(f.path(), "{not json", StandardCharsets.UTF_8);
        assertNull(f.read(), "a half-written file must read as absent, never throw");

        Files.writeString(f.path(), "{\"url\":\"http://127.0.0.1:1\"}", StandardCharsets.UTF_8);
        assertNull(f.read(), "no token — unusable");
    }

    @Test
    void deadPidIsNotAlive() throws IOException {
        RestEndpointFile f = file();
        // a file stranded by a crashed app: well-formed, but nothing is listening
        Files.writeString(f.path(), "{\"url\":\"http://127.0.0.1:1\",\"token\":\"t\",\"pid\":2147483646}",
                StandardCharsets.UTF_8);
        RestEndpointFile.Endpoint e = f.read();
        assertNotNull(e);
        assertFalse(e.alive(), "the bridge must see a stale endpoint as 'not running'");
    }

    @Test
    void deleteIsIdempotent() throws IOException {
        RestEndpointFile f = file();
        f.write("http://127.0.0.1:53411", "s3cr3t");
        f.delete();
        assertNull(f.read());
        f.delete();   // already gone — must not throw
    }

    @Test
    void deleteIfOwnedSparesAnotherProcessesEndpoint() throws IOException {
        RestEndpointFile f = file();
        f.write("http://127.0.0.1:53411", "s3cr3t");
        f.deleteIfOwnedByThisProcess();
        assertNull(f.read(), "our own file goes");

        Files.writeString(f.path(), "{\"url\":\"http://127.0.0.1:2\",\"token\":\"t\",\"pid\":2147483646}",
                StandardCharsets.UTF_8);
        f.deleteIfOwnedByThisProcess();
        assertNotNull(f.read(), "a second analyser's endpoint must survive our exit");
    }

    @Test
    void serverPublishesOnStartAndRemovesOnStop() throws IOException {
        HeapLogStore store = new HeapLogStore(Samples.sample());
        ActionDispatcher d = new ActionDispatcher(false, null, () -> store.index().snapshot(), store::rawText);
        RestEndpointFile f = file();
        ActionServer server = new ActionServer(d, "s3cr3t", 20, 5.0, f);

        assertNull(f.read(), "nothing published before start");
        server.start();
        try {
            RestEndpointFile.Endpoint e = f.read();
            assertNotNull(e, "start() publishes the live endpoint");
            assertEquals(server.url(), e.url());
            assertEquals("s3cr3t", e.token());
        } finally {
            server.stop();
        }
        assertNull(f.read(), "stop() removes it");
    }

    @Test
    void serverWithoutAnEndpointFilePublishesNothing() throws IOException {
        HeapLogStore store = new HeapLogStore(Samples.sample());
        ActionDispatcher d = new ActionDispatcher(false, null, () -> store.index().snapshot(), store::rawText);
        ActionServer server = new ActionServer(d, "s3cr3t", 20, 5.0);   // 4-arg: opt-in, so tests never
        server.start();                                                 // clobber a running app's endpoint
        try {
            assertNull(file().read());
        } finally {
            server.stop();
        }
    }
}
