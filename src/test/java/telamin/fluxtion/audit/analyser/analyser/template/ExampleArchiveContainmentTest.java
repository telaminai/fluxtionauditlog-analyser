package telamin.fluxtion.audit.analyser.analyser.template;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 P2b (owner request 2026-10-17): a prompt pack's example archive is unpacked through the SAME template archive
 * boundary — the existing adversarial attacks, against {@link TemplateArchive#forExamples()} — and, unlike a starter,
 * the starters' fixed list of root scripts does not apply: only an example's {@code mvnw} and {@code *.sh} keep an
 * executable bit the archive records (authoring walk, 2026-10-18). The analyser never runs one.
 */
class ExampleArchiveContainmentTest {

    @TempDir Path temp;

    /** A GitHub /archive/&lt;commit&gt;.zip: one top-level directory named repo-commit. */
    static byte[] githubShaped(String root) throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put(root + "/README.md", "# DEMO example".getBytes());
        e.put(root + "/run.sh", "#!/bin/sh\necho ran".getBytes());
        e.put(root + "/src/main/java/com/acme/Demo.java", "package com.acme; class Demo {}".getBytes());
        return TemplateArchiveAdversarialTest.zip(e);
    }

    /**
     * ★ Witness: drop the name check in {@code TemplateArchive.exampleExecutable} — the README.md the archive marks
     * executable is made executable.
     */
    @Test
    @DisplayName("★ an example archive unpacks its one root; only its scripts keep the executable bit the archive records")
    void unpacksWithOnlyScriptsExecutable() throws Exception {
        Path dest = temp.resolve("demo-1a2b3c4");
        var installed = TemplateArchive.forExamples().install(
                TemplateArchiveAdversarialTest.claimExecutable(githubShaped("demo-1a2b3c4d")), dest);
        assertEquals(dest.toAbsolutePath().normalize(), installed.projectRoot());
        assertTrue(Files.isRegularFile(dest.resolve("src/main/java/com/acme/Demo.java")));
        assertTrue(TemplateArchiveAdversarialTest.executable(dest.resolve("run.sh")),
                "a script the archive marks executable runs as the example's README says");
        assertFalse(TemplateArchiveAdversarialTest.executable(dest.resolve("README.md")),
                "a file that is not a script is never made executable, whatever the archive claims");
        assertFalse(TemplateArchiveAdversarialTest.executable(dest.resolve("src/main/java/com/acme/Demo.java")));
        // the starter path still does what it did: the same archive's root run.sh IS executable there
        Path starter = temp.resolve("starter");
        new TemplateArchive().install(TemplateArchiveAdversarialTest.claimExecutable(githubShaped("demo-1a2b3c4d")), starter);
        assertTrue(TemplateArchiveAdversarialTest.executable(starter.resolve("run.sh")));
    }

    /**
     * ★ Authoring walk (2026-10-18, A2): the downloaded example's {@code ./mvnw} and {@code ./regen.sh} failed with
     * "permission denied" — the unpack dropped every mode. Now each entry's recorded Unix mode decides for {@code mvnw}
     * and {@code *.sh} only. Witness: make {@code exampleExecutable} return false — mvnw and regen.sh are not executable.
     */
    @Test
    @DisplayName("★ an example's mvnw and *.sh keep the archive's executable mode; nothing else, no link, no 0644 script")
    void scriptsKeepTheArchivesMode() throws Exception {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("diamond-dad9b43/mvnw", "#!/bin/sh\n".getBytes());
        e.put("diamond-dad9b43/regen.sh", "#!/bin/sh\n".getBytes());
        e.put("diamond-dad9b43/tools/nested.sh", "#!/bin/sh\n".getBytes());
        e.put("diamond-dad9b43/notes.sh", "#!/bin/sh\n".getBytes());
        e.put("diamond-dad9b43/tool", "binary".getBytes());
        e.put("diamond-dad9b43/link.sh", "/etc/passwd".getBytes());
        e.put("diamond-dad9b43/pom.xml", "<project/>".getBytes());
        byte[] zip = withModes(TemplateArchiveAdversarialTest.zip(e), Map.of(
                "diamond-dad9b43/mvnw", 0100755, "diamond-dad9b43/regen.sh", 0100755,
                "diamond-dad9b43/tools/nested.sh", 0100755, "diamond-dad9b43/notes.sh", 0100644,
                "diamond-dad9b43/tool", 0100755, "diamond-dad9b43/link.sh", 0120777,
                "diamond-dad9b43/pom.xml", 0100644));
        Path dest = temp.resolve("diamond");
        TemplateArchive.forExamples().install(zip, dest);
        assertTrue(TemplateArchiveAdversarialTest.executable(dest.resolve("mvnw")), "./mvnw runs");
        assertTrue(TemplateArchiveAdversarialTest.executable(dest.resolve("regen.sh")), "./regen.sh runs");
        assertTrue(TemplateArchiveAdversarialTest.executable(dest.resolve("tools/nested.sh")), "a nested script too");
        assertFalse(TemplateArchiveAdversarialTest.executable(dest.resolve("notes.sh")), "a 0644 script stays 0644");
        assertFalse(TemplateArchiveAdversarialTest.executable(dest.resolve("tool")), "a non-script never");
        assertFalse(TemplateArchiveAdversarialTest.executable(dest.resolve("link.sh")), "a claimed link never");
        assertFalse(TemplateArchiveAdversarialTest.executable(dest.resolve("pom.xml")));
        assertEquals(List.of(), listOutside(dest), "nothing is written outside the target");
        // an archive that records no Unix mode (as java.util.zip writes one) makes nothing runnable
        Path plain = temp.resolve("plain");
        TemplateArchive.forExamples().install(TemplateArchiveAdversarialTest.zip(e), plain);
        assertFalse(TemplateArchiveAdversarialTest.executable(plain.resolve("mvnw")), "no recorded mode: not runnable");
    }

    private List<Path> listOutside(Path dest) throws IOException {
        try (var children = Files.list(temp)) {
            return children.filter(p -> !p.equals(dest)).toList();
        }
    }

    /** Set each named entry's Unix mode in the central directory ("version made by" host 3, external attributes). */
    static byte[] withModes(byte[] zip, Map<String, Integer> modes) {
        byte[] out = zip.clone();
        for (int i = 0; i + 46 <= out.length; i++) {
            if ((out[i] & 0xff) != 0x50 || (out[i + 1] & 0xff) != 0x4b || (out[i + 2] & 0xff) != 0x01
                    || (out[i + 3] & 0xff) != 0x02) continue;
            int nameLength = (out[i + 28] & 0xff) | (out[i + 29] & 0xff) << 8;
            String name = new String(out, i + 46, nameLength, java.nio.charset.StandardCharsets.UTF_8);
            Integer mode = modes.get(name);
            if (mode == null) continue;
            out[i + 5] = 3;
            int external = mode << 16;
            out[i + 38] = (byte) external;
            out[i + 39] = (byte) (external >>> 8);
            out[i + 40] = (byte) (external >>> 16);
            out[i + 41] = (byte) (external >>> 24);
        }
        return out;
    }

    @Test
    @DisplayName("containment: traversal, absolute and backslash paths are refused; nothing escapes")
    void containment() throws Exception {
        for (String name : List.of("demo/../../escape.txt", "/absolute.txt", "C:\\escape.txt", "demo\\..\\..\\esc.txt")) {
            Path dest = temp.resolve("t" + Math.abs(name.hashCode()));
            IOException error = assertThrows(IOException.class, () -> TemplateArchive.forExamples().install(
                    TemplateArchiveAdversarialTest.zip(Map.of(name, "bad".getBytes())), dest), name);
            assertTrue(error.getMessage().contains("refusing zip entry"), error.getMessage());
            assertFalse(Files.exists(dest));
        }
        assertFalse(Files.exists(temp.getParent().resolve("escape.txt")));
    }

    @Test
    @DisplayName("an archive claiming a symbolic link installs an inert regular file")
    void noSymlinks() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("demo/pom.xml", "<project/>".getBytes());
        entries.put("demo/escape", "/etc".getBytes());
        Path dest = temp.resolve("symlink");
        TemplateArchive.forExamples().install(TemplateArchiveAdversarialTest.claimSymlink(TemplateArchiveAdversarialTest.zip(entries)), dest);
        assertFalse(Files.isSymbolicLink(dest.resolve("escape")));
        assertTrue(Files.isRegularFile(dest.resolve("escape"), LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    @DisplayName("a populated target is refused before anything is written")
    void populatedTargetRefused() throws Exception {
        Path dest = Files.createDirectories(temp.resolve("full"));
        Files.writeString(dest.resolve("mine.txt"), "the person's");
        assertThrows(IOException.class, () -> TemplateArchive.forExamples().install(githubShaped("demo"), dest));
        assertEquals("the person's", Files.readString(dest.resolve("mine.txt")));
    }
}
