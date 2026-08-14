package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Headless test of the source-root detection (static helpers; no GUI created). */
class ConfigPanelDetectTest {

    private static Path mkSrc(Path base, String... pkgFiles) throws IOException {
        Path smj = base.resolve("src/main/java");
        Files.createDirectories(smj.resolve("com/acme/x"));
        Files.writeString(smj.resolve("com/acme/x/A.java"), "package com.acme.x; class A {}");
        return smj;
    }

    @Test
    void expandsProjectDirToSrcMainJava(@TempDir Path root) throws IOException {
        Path proj = root.resolve("proj");
        Path smj = mkSrc(proj);
        List<Path> detected = ConfigPanel.detectSourceRoots(proj);
        assertTrue(detected.contains(smj), "project dir expands to its src/main/java");
    }

    @Test
    void findsSubModuleSourceRoots(@TempDir Path root) throws IOException {
        Path multi = root.resolve("multi");
        Path a = mkSrc(multi.resolve("modA"));
        Path b = mkSrc(multi.resolve("modB"));
        List<Path> detected = ConfigPanel.detectSourceRoots(multi);
        assertTrue(detected.contains(a) && detected.contains(b), "sub-module src/main/java dirs found");
    }

    @Test
    void acceptsAnAlreadyCorrectSourceRoot(@TempDir Path root) throws IOException {
        Path smj = mkSrc(root.resolve("proj"));
        assertTrue(ConfigPanel.looksLikeSourceRoot(smj), "a dir containing com/ is a source root");
        assertTrue(ConfigPanel.detectSourceRoots(smj).contains(smj));
    }

    @Test
    void rejectsANonSourceFolder(@TempDir Path root) throws IOException {
        Path plain = root.resolve("docs");
        Files.createDirectories(plain);
        assertFalse(ConfigPanel.looksLikeSourceRoot(plain));
        assertTrue(ConfigPanel.detectSourceRoots(plain).isEmpty(), "no source root detected under a plain folder");
    }
}
