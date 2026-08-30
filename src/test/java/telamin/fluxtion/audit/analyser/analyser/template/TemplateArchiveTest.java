package telamin.fluxtion.audit.analyser.analyser.template;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateArchiveTest {

    @TempDir Path temp;

    @Test
    void installsOneProjectAtomicallyAndReturnsOnlyFixedCommands() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("bundle/.analyser/project.fluxtion-settings", "share.version=1\n".getBytes());
        entries.put("bundle/mvnw", "untrusted wrapper text".getBytes());
        entries.put("bundle/run-server.sh", "untrusted run text".getBytes());
        entries.put("bundle/export-audit.sh", "untrusted export text".getBytes());
        entries.put("bundle/stop-server.sh", "untrusted stop text".getBytes());
        entries.put("bundle/notes.txt", "data".getBytes());

        Path destination = temp.resolve("chosen-project");
        TemplateArchive.Installed installed = new TemplateArchive().install(zip(entries), destination);

        assertEquals(destination, installed.projectRoot());
        assertEquals(destination.resolve(".analyser/project.fluxtion-settings"), installed.profile());
        assertEquals(List.of("./mvnw package", "./run-server.sh", "./export-audit.sh", "./stop-server.sh"),
                installed.commands());
        assertEquals("data", Files.readString(destination.resolve("notes.txt")));
        try {
            Set<PosixFilePermission> notes = Files.getPosixFilePermissions(destination.resolve("notes.txt"));
            Set<PosixFilePermission> script = Files.getPosixFilePermissions(destination.resolve("run-server.sh"));
            assertFalse(notes.contains(PosixFilePermission.OWNER_EXECUTE));
            assertTrue(script.contains(PosixFilePermission.OWNER_EXECUTE));
        } catch (UnsupportedOperationException ignored) {
            // Windows: the important property is that Java never applied the archive mode.
        }
        assertNoStagingDirectories();
    }

    @Test
    void archiveExecutableClaimIsIgnoredOutsideTheFixedAllowlist() throws Exception {
        byte[] archive = zip(Map.of("bundle/evil.sh", "not an allowed program".getBytes()));
        Path destination = temp.resolve("mode");
        new TemplateArchive().install(claimExecutable(archive), destination);
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(destination.resolve("evil.sh"));
            assertFalse(permissions.contains(PosixFilePermission.OWNER_EXECUTE));
            assertFalse(permissions.contains(PosixFilePermission.GROUP_EXECUTE));
            assertFalse(permissions.contains(PosixFilePermission.OTHERS_EXECUTE));
        } catch (UnsupportedOperationException ignored) {
            // No executable bit exists on this file system; the claim still was not applied.
        }
    }

    @Test
    void refusesParentTraversalAbsoluteAndWindowsAbsoluteEntries() throws Exception {
        for (String name : List.of("bundle/../../escape.txt", "/absolute.txt", "C:\\escape.txt")) {
            Path destination = temp.resolve("target-" + Math.abs(name.hashCode()));
            IOException error = assertThrows(IOException.class,
                    () -> new TemplateArchive().install(zip(Map.of(name, "bad".getBytes())), destination));
            assertTrue(error.getMessage().contains(name));
            assertFalse(Files.exists(destination));
            assertNoStagingDirectories();
        }
    }

    @Test
    void refusesPopulatedDestinationBeforeExtraction() throws Exception {
        Path destination = Files.createDirectory(temp.resolve("existing"));
        Files.writeString(destination.resolve("mine.txt"), "keep");
        IOException error = assertThrows(IOException.class,
                () -> new TemplateArchive().install(zip(Map.of("bundle/new.txt", "new".getBytes())), destination));
        assertTrue(error.getMessage().contains("not empty"));
        assertEquals("keep", Files.readString(destination.resolve("mine.txt")));
        assertNoStagingDirectories();
    }

    @Test
    void acceptsAnExistingEmptyDestinationWithoutMerging() throws Exception {
        Path destination = Files.createDirectory(temp.resolve("empty"));
        var installed = new TemplateArchive().install(zip(Map.of("bundle/file.txt", "ok".getBytes())), destination);
        assertEquals("ok", Files.readString(installed.projectRoot().resolve("file.txt")));
        assertNoStagingDirectories();
    }

    @Test
    void entryAndExpansionCapsFailWithoutLeavingFiles() throws Exception {
        byte[] twoEntries = zip(Map.of("bundle/a", "a".getBytes(), "bundle/b", "b".getBytes()));
        IOException count = assertThrows(IOException.class,
                () -> new TemplateArchive(1, 100, 100).install(twoEntries, temp.resolve("count")));
        assertTrue(count.getMessage().contains("more than 1"));

        IOException entry = assertThrows(IOException.class,
                () -> new TemplateArchive(10, 3, 100).install(
                        zip(Map.of("bundle/large", "1234".getBytes())), temp.resolve("entry")));
        assertTrue(entry.getMessage().contains("entry expands"));

        IOException total = assertThrows(IOException.class,
                () -> new TemplateArchive(10, 10, 3).install(
                        zip(Map.of("bundle/a", "12".getBytes(), "bundle/b", "34".getBytes())),
                        temp.resolve("total")));
        assertTrue(total.getMessage().contains("archive expands"));

        assertFalse(Files.exists(temp.resolve("count")));
        assertFalse(Files.exists(temp.resolve("entry")));
        assertFalse(Files.exists(temp.resolve("total")));
        assertNoStagingDirectories();
    }

    @Test
    void refusesMultipleRootsAndDuplicatePaths() throws Exception {
        IOException roots = assertThrows(IOException.class,
                () -> new TemplateArchive().install(zip(Map.of(
                        "one/a", "a".getBytes(), "two/b", "b".getBytes())), temp.resolve("roots")));
        assertTrue(roots.getMessage().contains("one top-level"));

        byte[] duplicate;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ZipOutputStream out = new ZipOutputStream(bytes)) {
            put(out, "bundle/a", "one".getBytes());
            // java.util.zip refuses exact duplicate names itself, so use slash normalization to reach the same path.
            put(out, "bundle/x/../a", "two".getBytes());
            duplicate = bytes.toByteArray();
        }
        IOException dup = assertThrows(IOException.class,
                () -> new TemplateArchive().install(duplicate, temp.resolve("duplicate")));
        assertTrue(dup.getMessage().contains("parent traversal") || dup.getMessage().contains("duplicate"));
        assertNoStagingDirectories();
    }

    private void assertNoStagingDirectories() throws IOException {
        try (var children = Files.list(temp)) {
            assertTrue(children.noneMatch(path -> path.getFileName().toString().startsWith(".analyser-template-")));
        }
    }

    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (var entry : entries.entrySet()) put(out, entry.getKey(), entry.getValue());
            out.finish();
            return bytes.toByteArray();
        }
    }

    private static void put(ZipOutputStream out, String name, byte[] bytes) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(bytes);
        out.closeEntry();
    }

    /** Mark the central-directory entry 0755/Unix without changing its contents. */
    private static byte[] claimExecutable(byte[] zip) {
        byte[] out = zip.clone();
        for (int i = 0; i + 46 <= out.length; i++) {
            if ((out[i] & 0xff) == 0x50 && (out[i + 1] & 0xff) == 0x4b
                    && (out[i + 2] & 0xff) == 0x01 && (out[i + 3] & 0xff) == 0x02) {
                out[i + 5] = 3; // "version made by" high byte: Unix
                int external = 0100755 << 16;
                out[i + 38] = (byte) external;
                out[i + 39] = (byte) (external >>> 8);
                out[i + 40] = (byte) (external >>> 16);
                out[i + 41] = (byte) (external >>> 24);
                return out;
            }
        }
        throw new AssertionError("zip has no central-directory entry");
    }
}
