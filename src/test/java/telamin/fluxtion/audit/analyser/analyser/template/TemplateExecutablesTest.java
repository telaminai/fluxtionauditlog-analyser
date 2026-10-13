package telamin.fluxtion.audit.analyser.analyser.template;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The installer never reads archive mode bits (D-4), so every root script a playground template ships must be
 * on {@link TemplateArchive#POSIX_EXECUTABLES} or it arrives 0644. That list drifted twice: setup.sh
 * (edit-loop §G, feedback 8) and run-and-export.sh (2026-10-13, {@code ./run-and-export.sh} exited 126 — the one
 * command the analyser bundle's CLAUDE.md names). The fixture records what the templates ship; this test fails on
 * the first name the installer would leave non-executable. The live leg ({@link TemplateLiveBench}) compares the
 * deployed archives' own marks against the installed result with {@link #archiveMarkedRootExecutables}.
 */
class TemplateExecutablesTest {

    static final String FIXTURE = "/template/playground-executables.txt";

    @TempDir Path temp;

    @Test
    void everyScriptAPlaygroundTemplateShipsIsInstalledRunnable() throws Exception {
        Fixture fixture = Fixture.load();
        assertTrue(fixture.exec.size() >= 14, "fixture lists every catalogue template: " + fixture.exec.keySet());
        assumePosix();
        List<String> failures = new ArrayList<>();
        for (var template : fixture.exec.entrySet()) {
            Path project = installUnmarked(template.getKey(), template.getValue());
            for (String script : template.getValue()) {
                if (!executable(project.resolve(script))) {
                    failures.add(template.getKey() + "/" + script + " installed " + modes(project.resolve(script)));
                    continue;
                }
                Process run = new ProcessBuilder("./" + script).directory(project.toFile())
                        .redirectErrorStream(true).start();
                String out = new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                assertTrue(run.waitFor(30, TimeUnit.SECONDS), script + " did not finish");
                if (run.exitValue() != 0 || !out.equals(script)) {
                    failures.add(template.getKey() + "/" + script + " exited " + run.exitValue() + ": " + out);
                }
            }
        }
        assertEquals(List.of(), failures, "root scripts a template ships but the installer leaves non-runnable");
    }

    /** A command the bundle's own Markdown tells the reader to run must be a file the installer made runnable. */
    @Test
    void everyCommandATemplateDocumentsIsShippedAndExecutable() throws Exception {
        Fixture fixture = Fixture.load();
        assertEquals(fixture.exec.keySet().stream().filter(id -> !id.equals("generator")).toList(),
                List.copyOf(fixture.doc.keySet()), "each catalogue template has a doc line");
        for (var template : fixture.doc.entrySet()) {
            Set<String> shipped = fixture.exec.get(template.getKey());
            assertNotNull(shipped, template.getKey());
            for (String command : template.getValue()) {
                assertTrue(shipped.contains(command), template.getKey() + " documents ./" + command
                        + " but does not ship it executable");
                assertTrue(TemplateArchive.POSIX_EXECUTABLES.contains(command), template.getKey()
                        + " documents ./" + command + ", which the installer would leave non-executable");
            }
        }
    }

    /** The fixed commands the post-install dialog shows must each name a file that was made executable. */
    @Test
    void everyInstalledCommandNamesAnExecutableFile() throws Exception {
        Fixture fixture = Fixture.load();
        assumePosix();
        for (var template : fixture.exec.entrySet()) {
            if (template.getKey().equals("generator")) continue; // not a template: no mvnw, no commands
            Path project = installUnmarked(template.getKey(), template.getValue());
            List<String> commands = TemplateArchive.commandsFor(project);
            assertFalse(commands.isEmpty(), template.getKey() + " offers no commands");
            for (String command : commands) {
                String program = command.split(" ", 2)[0];
                assertTrue(program.startsWith("./"), command);
                assertTrue(executable(project.resolve(program.substring(2))),
                        template.getKey() + ": command '" + command + "' names a non-executable file");
            }
        }
    }

    /**
     * PR #135 review: the dialog offered {@code ./mvnw package} and run-server/export-audit/stop-server to every
     * template, so the bundle never saw {@code ./run-and-export.sh} — the one command its CLAUDE.md names — and the
     * starters never saw {@code ./run.sh}. It must offer each template's documented first run, in order.
     */
    @Test
    void postInstallCommandsAreEachTemplatesDocumentedFirstRun() throws Exception {
        Fixture fixture = Fixture.load();
        assertEquals(List.copyOf(fixture.doc.keySet()), List.copyOf(fixture.first.keySet()),
                "each catalogue template has a first line");
        List<String> failures = new ArrayList<>();
        for (var template : fixture.first.entrySet()) {
            String id = template.getKey();
            for (String step : template.getValue()) {
                assertTrue(fixture.doc.get(id).contains(step), id + ": first-run step " + step
                        + " is not a command its guide names");
            }
            List<String> documented = template.getValue().stream()
                    .map(step -> step.equals("mvnw") ? "./mvnw package" : "./" + step).toList();
            List<String> offered = TemplateArchive.commandsFor(installUnmarked(id, fixture.exec.get(id)), false);
            if (!offered.equals(documented)) failures.add(id + ": offers " + offered + ", its guide says " + documented);
        }
        assertEquals(List.of(), failures, "post-install commands that are not the template's documented first run");
    }

    /**
     * PR #139 review: no catalogue template ships a .cmd run script, so on Windows the dialog offers none of them a
     * first run and shows its README pointer instead (TemplateProjectDialog.NO_WINDOWS_FIRST_RUN). When a template
     * starts shipping one, this fails: update the CHANGELOG/dialog wording that says "every catalogue template".
     */
    @Test
    void noCatalogueTemplateHasAWindowsFirstRunToday() throws Exception {
        Fixture fixture = Fixture.load();
        for (var template : fixture.first.entrySet()) {
            Set<String> shipped = new TreeSet<>(fixture.exec.get(template.getKey()));
            shipped.add("mvnw.cmd"); // every catalogue template ships the Maven wrapper's Windows form
            shipped.addAll(fixture.windows.getOrDefault(template.getKey(), Set.of()));
            assertEquals(List.of(), TemplateArchive.commandsFor(installUnmarked(template.getKey(), shipped), true),
                    template.getKey() + " now has a Windows first run");
        }
    }

    /** Windows: a sequence is offered only when every step has a .cmd form, never half of one. */
    @Test
    void windowsOffersAWholeSequenceOrNone() throws Exception {
        Path bundle = Files.createDirectories(temp.resolve("windows-bundle"));
        for (String file : List.of("mvnw", "mvnw.cmd", "run-and-export.sh", "export-audit.cmd", "stop-server.cmd")) {
            Files.writeString(bundle.resolve(file), "x");
        }
        assertEquals(List.of(), TemplateArchive.commandsFor(bundle, true),
                "the bundle ships no run-and-export.cmd, so Windows is pointed at the README");
        Path server = Files.createDirectories(temp.resolve("windows-server"));
        for (String file : List.of("mvnw", "mvnw.cmd", "run-server.sh", "run-server.cmd")) {
            Files.writeString(server.resolve(file), "x");
        }
        assertEquals(List.of("run-server.cmd"), TemplateArchive.commandsFor(server, true));
        assertEquals(List.of("./run-server.sh"), TemplateArchive.commandsFor(server, false));
    }

    @Test
    void archiveMarkReaderSeesOnlyMarkedRootEntries() throws Exception {
        Path zip = temp.resolve("marked.zip");
        try (FileSystem fs = FileSystems.newFileSystem(URI.create("jar:" + zip.toUri()),
                Map.of("create", "true", "enablePosixFileAttributes", "true"))) {
            Path root = Files.createDirectories(fs.getPath("/bundle/tools"));
            write(fs.getPath("/bundle/run.sh"), PosixFilePermission.OWNER_EXECUTE);
            write(fs.getPath("/bundle/run.cmd"), PosixFilePermission.OWNER_EXECUTE);
            write(fs.getPath("/bundle/notes.txt"));
            write(root.resolve("nested.sh"), PosixFilePermission.OWNER_EXECUTE);
        }
        assertEquals(Set.of("run.sh"), archiveMarkedRootExecutables(Files.readAllBytes(zip)));
        assertEquals(Set.of(), archiveMarkedRootExecutables(zip(Map.of("bundle/run.sh", "x".getBytes()))),
                "an entry with no unix mode is not marked executable");
    }

    // ---- shared with TemplateLiveBench ----

    /**
     * The root entries an archive claims are executable. Read only to compare with what was installed — the
     * installer itself never consults these bits (D-4). Windows wrappers ({@code .cmd}) need no bit and are skipped.
     */
    static Set<String> archiveMarkedRootExecutables(byte[] zip) throws IOException {
        Path file = Files.createTempFile("analyser-template-marks-", ".zip");
        try {
            Files.write(file, zip);
            Set<String> marked = new TreeSet<>();
            // An entry with no unix mode (a DOS-made entry) must read as unmarked, not zipfs' rwxrwxrwx default.
            try (FileSystem fs = FileSystems.newFileSystem(file, Map.of("enablePosixFileAttributes", "true",
                    "defaultPermissions", "---------"));
                 Stream<Path> roots = Files.list(fs.getPath("/"))) {
                for (Path root : roots.toList()) {
                    try (Stream<Path> children = Files.list(root)) {
                        for (Path child : children.toList()) {
                            String name = child.getFileName().toString().replace("/", "");
                            if (Files.isDirectory(child) || name.endsWith(".cmd")) continue;
                            var view = Files.getFileAttributeView(child, PosixFileAttributeView.class);
                            if (view == null) continue;
                            Set<PosixFilePermission> permissions = view.readAttributes().permissions();
                            if (permissions.contains(PosixFilePermission.OWNER_EXECUTE)) marked.add(name);
                        }
                    }
                }
            }
            return marked;
        } finally {
            Files.deleteIfExists(file);
        }
    }

    static boolean executable(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return false;
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file);
        return permissions.contains(PosixFilePermission.OWNER_EXECUTE)
                && permissions.contains(PosixFilePermission.GROUP_EXECUTE)
                && permissions.contains(PosixFilePermission.OTHERS_EXECUTE);
    }

    // ---- helpers ----

    /** Install a zip holding the template's root scripts with NO mode bits, the case D-4 makes normal. */
    private Path installUnmarked(String template, Set<String> scripts) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (String script : scripts) {
            entries.put("bundle/" + script, ("#!/bin/sh\necho " + script + "\n").getBytes(StandardCharsets.UTF_8));
        }
        Path destination = temp.resolve(template);
        return new TemplateArchive().install(zip(entries), destination).projectRoot();
    }

    private void assumePosix() throws IOException {
        Assumptions.assumeTrue(Files.getFileStore(temp).supportsFileAttributeView("posix"),
                "no POSIX execute bit on this file system");
    }

    private static String modes(Path file) throws IOException {
        return java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(file));
    }

    private static void write(Path file, PosixFilePermission... extra) throws IOException {
        Files.writeString(file, "x");
        Set<PosixFilePermission> permissions = new java.util.HashSet<>(Set.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        permissions.addAll(List.of(extra));
        Files.getFileAttributeView(file, PosixFileAttributeView.class).setPermissions(permissions);
    }

    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (var entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
            out.finish();
            return bytes.toByteArray();
        }
    }

    /**
     * The committed record of what the playground templates ship: {@code exec <id>: …} and {@code doc <id>: …}
     * (sets), {@code first <id>: …} (the documented first run, in order) and {@code cmd <id>: …} (the root
     * {@code .cmd} files it ships besides {@code mvnw.cmd}).
     */
    record Fixture(Map<String, Set<String>> exec, Map<String, Set<String>> doc, Map<String, List<String>> first,
                   Map<String, Set<String>> windows) {
        static Fixture load() throws IOException {
            Map<String, Set<String>> exec = new LinkedHashMap<>();
            Map<String, Set<String>> doc = new LinkedHashMap<>();
            Map<String, List<String>> first = new LinkedHashMap<>();
            Map<String, Set<String>> windows = new LinkedHashMap<>();
            try (InputStream in = TemplateExecutablesTest.class.getResourceAsStream(FIXTURE)) {
                assertNotNull(in, FIXTURE);
                for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                    String trimmed = line.strip();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                    String[] kindAndRest = trimmed.split("\\s+", 2);
                    int colon = kindAndRest[1].indexOf(':');
                    String id = kindAndRest[1].substring(0, colon).strip();
                    List<String> ordered = new ArrayList<>(List.of(kindAndRest[1].substring(colon + 1).strip().split("\\s+")));
                    ordered.remove("");
                    Set<String> names = new TreeSet<>(ordered);
                    switch (kindAndRest[0]) {
                        case "exec" -> exec.put(id, names);
                        case "doc" -> doc.put(id, names);
                        case "first" -> first.put(id, List.copyOf(ordered));
                        case "cmd" -> windows.put(id, names);
                        default -> throw new IllegalStateException("unknown fixture line: " + line);
                    }
                }
            }
            return new Fixture(exec, doc, first, windows);
        }
    }
}
