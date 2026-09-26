package telamin.fluxtion.audit.analyser.analyser.template;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.SettingsShare;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Edit-loop spec §I1: a downloaded template's profile may only grant reads inside the project it installs.
 * Real installs through {@link TemplateArchive}; every refusal leaves nothing installed and no staging behind.
 */
class TemplateRootContainmentTest {

    @TempDir Path temp;

    private static byte[] template(String profile, Map<String, String> more) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("bundle/.analyser/project.fluxtion-settings", "share.version=1\n" + profile);
        entries.put("bundle/src/main/java/com/acme/Node.java", "package com.acme; class Node {}\n");
        entries.put("bundle/src/main/fluxtion/designer/application-context.xml", "<beans/>\n");
        entries.put("bundle/pom.xml", "<project/>\n");
        entries.putAll(more);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (var e : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(e.getKey()));
                out.write(e.getValue().getBytes());
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static String roots(String... roots) {
        StringBuilder sb = new StringBuilder("sourceRoot.count=" + roots.length + "\n");
        for (int i = 0; i < roots.length; i++) sb.append("sourceRoot.").append(i).append('=').append(roots[i]).append('\n');
        return sb.toString();
    }

    private IOException refused(String profile, String destinationName) throws Exception {
        Path destination = temp.resolve(destinationName);
        IOException error = assertThrows(IOException.class,
                () -> new TemplateArchive().install(template(profile, Map.of()), destination), profile);
        assertFalse(Files.exists(destination), "nothing installed for " + profile);
        try (Stream<Path> left = Files.list(temp)) {
            assertTrue(left.noneMatch(p -> p.getFileName().toString().startsWith(".analyser-template-")), "no staging left");
        }
        return error;
    }

    @Test
    void aRootThatLeavesTheProjectIsRefused() throws Exception {
        IOException error = refused(roots("src/main/java", "../outside"), "leaves");
        assertTrue(error.getMessage().contains("'../outside' leaves the project"), error.getMessage());
    }

    /** Resolves inside staging, outside the installed project once moved to a differently named destination. */
    @Test
    void aRootThatReentersThroughTheArchiveRootsOwnNameIsRefused() throws Exception {
        IOException error = refused(roots("../bundle/src/main/java"), "installed-under-another-name");
        assertTrue(error.getMessage().contains("leaves the project"), error.getMessage());
    }

    @Test
    void aRootThatIsTheWholeProjectIsRefused() throws Exception {
        for (String whole : List.of("src/..", ".", "./")) {
            IOException error = refused(roots(whole), "whole-" + Math.abs(whole.hashCode()));
            assertTrue(error.getMessage().contains("project root itself"), whole + ": " + error.getMessage());
        }
    }

    @Test
    void homeRelativeAndRootedRootsAreRefused() throws Exception {
        for (String root : List.of("~", "~/work/src", "/etc", temp.toAbsolutePath().toString())) {
            IOException error = refused(roots(root), "rooted-" + Math.abs(root.hashCode()));
            assertTrue(error.getMessage().contains("home-relative") || error.getMessage().contains("root component"),
                    root + ": " + error.getMessage());
        }
    }

    @Test
    void anyTemplateWorkspaceAnchorIsRefused() throws Exception {
        for (String anchor : List.of(".", "..")) {
            IOException error = refused(roots("src/main/java") + "workspaceRoot=" + anchor + "\n", "anchor-" + anchor.length());
            assertTrue(error.getMessage().contains("workspace anchor"), error.getMessage());
        }
    }

    @Test
    void aRootThroughARegularFileIsRefused() throws Exception {
        IOException error = refused(roots("pom.xml/sub"), "file-component");
        assertTrue(error.getMessage().contains("not a directory"), error.getMessage());
        IOException file = refused(roots("pom.xml"), "file-root");
        assertTrue(file.getMessage().contains("not a directory"), file.getMessage());
    }

    @Test
    void descendantRootsIncludingOneNotYetCreatedInstallAndMavenReposAreNotSubjectToThisRule() throws Exception {
        String profile = roots("src/main/java", "./src/main/fluxtion/designer", "target/generated-sources")
                + "mavenRepo.count=1\nmavenRepo.0=~/.m2/repository\n";
        Path destination = temp.resolve("ok");
        var installed = new TemplateArchive().install(template(profile, Map.of()), destination);
        assertEquals(destination, installed.projectRoot());
        assertFalse(Files.exists(destination.resolve("target")), "a future root is judged, not created");
    }

    /** Canonicalising both sides: an installation parent reached through an alias is not a false refusal. */
    @Test
    void anInstallationParentReachedThroughASymlinkIsNotRefused() throws Exception {
        Path real = Files.createDirectories(temp.resolve("real-parent"));
        Path alias;
        try { alias = Files.createSymbolicLink(temp.resolve("alias-parent"), real); }
        catch (UnsupportedOperationException | IOException noLinks) {
            org.junit.jupiter.api.Assumptions.abort("symbolic links are unavailable here");
            return;
        }
        var installed = new TemplateArchive().install(template(roots("src/main/java"), Map.of()), alias.resolve("proj"));
        assertTrue(Files.isDirectory(installed.projectRoot().resolve("src/main/java")));
    }

    /** The archive cannot plant a link: a ZIP entry that claims to be one arrives as an ordinary file. */
    @Test
    void anEntryClaimingToBeASymlinkIsInstalledAsAnOrdinaryFile() throws Exception {
        byte[] zip = claimSymlink(template(roots("src/main/java"), Map.of("bundle/link-to-etc", "../../../etc")),
                "bundle/link-to-etc");
        Path destination = temp.resolve("links");
        new TemplateArchive().install(zip, destination);
        try (Stream<Path> tree = Files.walk(destination)) {
            assertTrue(tree.noneMatch(Files::isSymbolicLink), "no symbolic link exists in the installed tree");
        }
        assertTrue(Files.isRegularFile(destination.resolve("link-to-etc"), java.nio.file.LinkOption.NOFOLLOW_LINKS));
    }

    /** Ordinary profiles keep their external roots: this rule is for installed templates only. */
    @Test
    void aUserAuthoredProfileStillLoadsAnExternalMonorepoRoot() throws Exception {
        Path project = Files.createDirectories(temp.resolve("mono/app"));
        var plan = new SettingsShare().preview("share.version=1\n" + roots("../shared-lib/src/main/java"),
                new AppConfig(), project);
        assertEquals(List.of(project.resolve("../shared-lib/src/main/java").normalize().toString()), plan.sourceRoots());
    }

    /** Mark the named central-directory entry as a Unix symbolic link (S_IFLNK) without changing its bytes. */
    private static byte[] claimSymlink(byte[] zip, String name) {
        byte[] out = zip.clone();
        byte[] wanted = name.getBytes();
        for (int i = 0; i + 46 <= out.length; i++) {
            if ((out[i] & 0xff) == 0x50 && (out[i + 1] & 0xff) == 0x4b && (out[i + 2] & 0xff) == 0x01 && (out[i + 3] & 0xff) == 0x02) {
                int nameLength = (out[i + 28] & 0xff) | (out[i + 29] & 0xff) << 8;
                if (nameLength == wanted.length && java.util.Arrays.equals(out, i + 46, i + 46 + nameLength, wanted, 0, wanted.length)) {
                    out[i + 5] = 3;                                   // made by Unix
                    int external = 0120777 << 16;                     // S_IFLNK | 0777
                    out[i + 38] = (byte) external; out[i + 39] = (byte) (external >>> 8);
                    out[i + 40] = (byte) (external >>> 16); out[i + 41] = (byte) (external >>> 24);
                    return out;
                }
            }
        }
        throw new AssertionError("no central-directory entry named " + name);
    }
}
