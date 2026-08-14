package telamin.fluxtion.audit.analyser.analyser.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class MavenSourceResolverTest {

    private static final String FQN = "com.acme.calc.HedgeNode";
    private static final String SOURCE = "package com.acme.calc;\npublic class HedgeNode { }\n";

    /** Builds {@code <repo>/com/acme/calc-lib/1.0/calc-lib-1.0-sources.jar} containing the FQN's .java. */
    private static Path fakeRepo(Path dir) throws IOException {
        Path jarDir = dir.resolve("repo/com/acme/calc-lib/1.0");
        Files.createDirectories(jarDir);
        Path jar = jarDir.resolve("calc-lib-1.0-sources.jar");
        try (OutputStream os = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(os)) {
            zip.putNextEntry(new ZipEntry("com/acme/calc/HedgeNode.java"));
            zip.write(SOURCE.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        // a plain (non-sources) jar that must never be searched
        Path plain = jarDir.resolve("calc-lib-1.0.jar");
        try (OutputStream os = Files.newOutputStream(plain); ZipOutputStream zip = new ZipOutputStream(os)) {
            zip.putNextEntry(new ZipEntry("com/acme/calc/HedgeNode.class"));
            zip.write(new byte[]{1, 2, 3});
            zip.closeEntry();
        }
        return dir.resolve("repo");
    }

    @Test
    void findsSourceInSourcesJar(@TempDir Path dir) throws IOException {
        Path repo = fakeRepo(dir);
        MavenSourceResolver r = new MavenSourceResolver(List.of(repo.toString()), true);
        Optional<String> src = r.read(FQN);
        assertTrue(src.isPresent());
        assertEquals(SOURCE, src.get());
    }

    @Test
    void missIsEmptyAndDisabledSearchesNothing(@TempDir Path dir) throws IOException {
        Path repo = fakeRepo(dir);
        MavenSourceResolver enabled = new MavenSourceResolver(List.of(repo.toString()), true);
        assertTrue(enabled.read("com.acme.calc.NoSuchClass").isEmpty());
        assertTrue(enabled.read(null).isEmpty());

        MavenSourceResolver disabled = new MavenSourceResolver(List.of(repo.toString()), false);
        assertTrue(disabled.read(FQN).isEmpty(), "\"don't search local repos\" bypasses the lookup");
    }

    @Test
    void missingRepoDirectoryIsHarmless() {
        MavenSourceResolver r = new MavenSourceResolver(List.of("/no/such/repo"), true);
        assertTrue(r.read(FQN).isEmpty());
    }

    @Test
    void serviceFallsBackToMavenWhenRootsMiss(@TempDir Path dir) throws IOException {
        Path repo = fakeRepo(dir);
        SourceService service = new SourceService();
        service.configure(List.of(), null, List.of(repo.toString()), true);
        assertEquals(SOURCE, service.sourceForFqn(FQN).orElseThrow());
    }
}
