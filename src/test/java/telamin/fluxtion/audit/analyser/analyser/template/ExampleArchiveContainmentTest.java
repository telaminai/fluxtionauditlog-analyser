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
 * NO file is made executable: an example is unpacked to be read and offered, never to be run.
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

    /** ★ Witness: drop the {@code executables &&} guard in setFixedPermissions — run.sh is made executable. */
    @Test
    @DisplayName("★ an example archive unpacks its one root, and no file — not even a root run.sh — is executable")
    void unpacksWithNoExecutableBits() throws Exception {
        Path dest = temp.resolve("demo-1a2b3c4");
        var installed = TemplateArchive.forExamples().install(
                TemplateArchiveAdversarialTest.claimExecutable(githubShaped("demo-1a2b3c4d")), dest);
        assertEquals(dest.toAbsolutePath().normalize(), installed.projectRoot());
        assertTrue(Files.isRegularFile(dest.resolve("src/main/java/com/acme/Demo.java")));
        assertFalse(TemplateArchiveAdversarialTest.executable(dest.resolve("run.sh")),
                "an example's scripts are not runnable until the person chooses to make them so");
        // the starter path still does what it did: the same archive's root run.sh IS executable there
        Path starter = temp.resolve("starter");
        new TemplateArchive().install(TemplateArchiveAdversarialTest.claimExecutable(githubShaped("demo-1a2b3c4d")), starter);
        assertTrue(TemplateArchiveAdversarialTest.executable(starter.resolve("run.sh")));
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
