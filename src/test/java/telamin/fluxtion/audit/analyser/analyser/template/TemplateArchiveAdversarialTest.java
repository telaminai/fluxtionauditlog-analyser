package telamin.fluxtion.audit.analyser.analyser.template;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Review-added attacks on D-4's boundary. The M19.5 report asked the reviewer not to accept its own
 * security summary as evidence, so these are written against the cases its own suite leaves open —
 * a nested file whose BASENAME is allow-listed, an archive claiming a symbolic link, and the state of
 * a caller's pre-existing empty directory after a refusal.
 *
 * <p>The distinction that matters throughout: the archive arrives over the network, so every property
 * here must hold against an archive built by someone who has read this class.
 */
class TemplateArchiveAdversarialTest {

    @TempDir Path temp;

    /**
     * The allow-list is by basename, so the interesting attack is not {@code evil.sh} (which the
     * existing suite covers) but a file the list DOES name, placed where the list does not reach.
     * A nested {@code run-server.sh} is the shape a real hostile bundle would use, because it looks
     * exactly like a file the installer is supposed to make executable.
     */
    @Test
    void aNestedFileWithAnAllowlistedBasenameIsNotMadeExecutable() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("bundle/run-server.sh", "# the real one".getBytes());
        entries.put("bundle/tools/run-server.sh", "# the impostor".getBytes());
        entries.put("bundle/nested/deep/mvnw", "# also an impostor".getBytes());
        Path destination = temp.resolve("nested-allowlist");

        new TemplateArchive().install(claimExecutable(zip(entries)), destination);

        assertTrue(executable(destination.resolve("run-server.sh")),
                "the root lifecycle script is the one file the allow-list exists for");
        assertFalse(executable(destination.resolve("tools/run-server.sh")),
                "an allow-listed BASENAME below the root must not inherit the allow-list");
        assertFalse(executable(destination.resolve("nested/deep/mvnw")),
                "depth, not name, is what the allow-list is keyed on");
    }

    /**
     * A zip may declare an entry to be a symbolic link. If one were ever materialised, every other
     * guard here is bypassable: a link named {@code config} pointing at {@code /} turns a later
     * in-bounds write into an out-of-bounds one. The property is that the installed path is an inert
     * REGULAR FILE — the declared target becomes file content and points at nothing.
     */
    @Test
    void anArchiveClaimingASymbolicLinkInstallsAnInertRegularFile() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("bundle/pom.xml", "<project/>".getBytes());
        entries.put("bundle/escape", "/etc".getBytes()); // the link target, as a symlink entry stores it
        Path destination = temp.resolve("symlink");

        new TemplateArchive().install(claimSymlink(zip(entries)), destination);

        Path installed = destination.resolve("escape");
        assertFalse(Files.isSymbolicLink(installed), "an archive must not be able to create a link");
        assertTrue(Files.isRegularFile(installed, LinkOption.NOFOLLOW_LINKS));
        assertEquals("/etc", Files.readString(installed), "the target is inert content, not a link");
    }

    /**
     * Backslash separators are normalised before the traversal check, so the Windows spelling of the
     * classic attack must be refused by the same rule rather than by a second one that could drift.
     */
    @Test
    void refusesBackslashSpelledTraversal() throws Exception {
        byte[] archive = zip(Map.of("bundle\\..\\..\\escaped.txt", "x".getBytes()));
        IOException failure = assertThrows(IOException.class,
                () -> new TemplateArchive().install(archive, temp.resolve("backslash")));
        assertTrue(failure.getMessage().contains("parent traversal"), failure.getMessage());
        assertFalse(Files.exists(temp.resolve("escaped.txt")));
        assertNoStaging();
    }

    /**
     * The caller may legitimately point at an existing empty directory, and the installer deletes it
     * so the atomic move has somewhere to land. A refusal must therefore leave the caller's directory
     * as it found it: a boundary that fails closed but eats the destination has still damaged state
     * the user chose.
     */
    @Test
    void aRefusalLeavesAPreExistingEmptyDestinationIntactAndNoStagingBehind() throws Exception {
        Path destination = Files.createDirectory(temp.resolve("pre-existing"));
        byte[] twoRoots = zip(new LinkedHashMap<>(Map.of(
                "first/pom.xml", "<project/>".getBytes(),
                "second/pom.xml", "<project/>".getBytes())));

        assertThrows(IOException.class, () -> new TemplateArchive().install(twoRoots, destination));

        assertTrue(Files.isDirectory(destination, LinkOption.NOFOLLOW_LINKS),
                "the destination the user chose must survive a refusal");
        try (var children = Files.list(destination)) {
            assertTrue(children.findAny().isEmpty(), "and must still be empty");
        }
        assertNoStaging();
    }

    /** A refusal mid-extraction must not leave a partially written project anywhere the user can see. */
    @Test
    void aRefusalPartWayThroughLeavesNothingInTheDestinationParent() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("bundle/pom.xml", "<project/>".getBytes());
        entries.put("bundle/big.bin", new byte[4096]);
        entries.put("bundle/../escape.txt", "x".getBytes());
        Path destination = temp.resolve("partial");

        assertThrows(IOException.class, () -> new TemplateArchive().install(zip(entries), destination));

        assertFalse(Files.exists(destination, LinkOption.NOFOLLOW_LINKS));
        assertFalse(Files.exists(temp.resolve("escape.txt")));
        assertNoStaging();
    }

    private static boolean executable(Path path) throws IOException {
        assertTrue(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), path + " should have been installed");
        try {
            return Files.getPosixFilePermissions(path).contains(PosixFilePermission.OWNER_EXECUTE);
        } catch (UnsupportedOperationException noPosix) {
            return false;
        }
    }

    private void assertNoStaging() throws IOException {
        try (var children = Files.list(temp)) {
            assertTrue(children.noneMatch(p -> p.getFileName().toString().startsWith(".analyser-template-")),
                    "staging must not survive a refusal");
        }
    }

    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (var entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
            out.finish();
            return bytes.toByteArray();
        }
    }

    /** Mark every central-directory entry 0100755/Unix, leaving contents untouched. */
    private static byte[] claimExecutable(byte[] zip) {
        return withUnixMode(zip, 0100755);
    }

    /** Mark every central-directory entry as a Unix symbolic link (S_IFLNK | 0777). */
    private static byte[] claimSymlink(byte[] zip) {
        return withUnixMode(zip, 0120777);
    }

    private static byte[] withUnixMode(byte[] zip, int mode) {
        byte[] out = zip.clone();
        for (int i = 0; i + 46 <= out.length; i++) {
            if ((out[i] & 0xff) == 0x50 && (out[i + 1] & 0xff) == 0x4b
                    && (out[i + 2] & 0xff) == 0x01 && (out[i + 3] & 0xff) == 0x02) {
                out[i + 5] = 3; // "version made by" high byte: Unix
                int external = mode << 16;
                out[i + 38] = (byte) external;
                out[i + 39] = (byte) (external >>> 8);
                out[i + 40] = (byte) (external >>> 16);
                out[i + 41] = (byte) (external >>> 24);
            }
        }
        return out;
    }
}
