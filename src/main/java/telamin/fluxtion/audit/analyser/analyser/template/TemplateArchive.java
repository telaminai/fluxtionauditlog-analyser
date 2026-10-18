package telamin.fluxtion.audit.analyser.analyser.template;

import telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** D-4's fail-closed network archive boundary. It extracts bytes; it never runs their contents. */
public final class TemplateArchive {

    public static final int MAX_ENTRIES = 4096;
    public static final long MAX_ENTRY_BYTES = 64L * 1024 * 1024;
    public static final long MAX_EXPANDED_BYTES = 512L * 1024 * 1024;
    private static final Pattern WINDOWS_ABSOLUTE = Pattern.compile("^[A-Za-z]:[/\\\\].*");
    // D-4: the executable bit comes only from this fixed list of root scripts, never from the archive's own
    // mode bits. The list must name every top-level script the playground templates ship; it has drifted
    // twice — setup/validate/generate (edit-loop spec §G, feedback 8: they arrived 0644) and then
    // run-and-export.sh, run.sh and setup-java.sh (2026-10-13: ./run-and-export.sh exited 126).
    // Kept as a list rather than a "*.sh at the root" rule because D-4 is itself about WHICH names may become
    // executable and its bench attacks an archive-marked root evil.sh; the drift is caught instead by
    // TemplateExecutablesTest against src/test/resources/template/playground-executables.txt (upstream ask UP-PG-07).
    static final Set<String> POSIX_EXECUTABLES = Set.of(
            "mvnw", "run-server.sh", "export-audit.sh", "stop-server.sh", "check-fluxtion-key.sh",
            "setup.sh", "validate.sh", "generate.sh",
            "run-and-export.sh", "run.sh", "setup-java.sh", "runWasm.sh");

    /**
     * @param runnable owner follow-up (2026-10-18): the files an example's unpack gave an executable bit, relative to the
     *                 project root, in archive order — so the person can be told which will run. Empty for a starter
     *                 (its fixed list is the dialog's commands) and where there is no POSIX mode.
     */
    public record Installed(Path projectRoot, Path profile, List<String> commands, List<String> runnable) {
        public Installed {
            commands = List.copyOf(commands == null ? List.of() : commands);
            runnable = List.copyOf(runnable == null ? List.of() : runnable);
        }

        public Installed(Path projectRoot, Path profile, List<String> commands) {
            this(projectRoot, profile, commands, List.of());
        }
    }

    private final int maxEntries;
    private final long maxEntryBytes;
    private final long maxExpandedBytes;
    /**
     * False for a downloaded example (M74 P2b): the starters' fixed list does not apply. Authoring walk (2026-10-18,
     * A2): an example's {@code mvnw} and {@code *.sh} keep the executable bit the archive records for them (a GitHub
     * archive records each file's git mode), so {@code ./mvnw} and {@code ./regen.sh} run as the example's README says;
     * no other file is ever made executable, whatever the archive claims (see {@link #exampleExecutable}).
     */
    private final boolean executables;

    public TemplateArchive() {
        this(MAX_ENTRIES, MAX_ENTRY_BYTES, MAX_EXPANDED_BYTES);
    }

    /**
     * M74 P2b: the same fail-closed boundary for a prompt pack's example archive — every containment check and cap. The
     * analyser never runs an example; only its {@code mvnw} and {@code *.sh} may keep an executable bit the archive
     * records, so the person can run them as the example's README says (authoring walk, 2026-10-18).
     */
    public static TemplateArchive forExamples() {
        return new TemplateArchive(MAX_ENTRIES, MAX_ENTRY_BYTES, MAX_EXPANDED_BYTES, false);
    }

    TemplateArchive(int maxEntries, long maxEntryBytes, long maxExpandedBytes) {
        this(maxEntries, maxEntryBytes, maxExpandedBytes, true);
    }

    TemplateArchive(int maxEntries, long maxEntryBytes, long maxExpandedBytes, boolean executables) {
        if (maxEntries < 1 || maxEntryBytes < 1 || maxExpandedBytes < 1) {
            throw new IllegalArgumentException("archive limits must be positive");
        }
        this.maxEntries = maxEntries;
        this.maxEntryBytes = maxEntryBytes;
        this.maxExpandedBytes = maxExpandedBytes;
        this.executables = executables;
    }

    /**
     * Extract into a sibling temporary directory, then atomically move the archive's sole project root
     * into {@code destination}. Existing non-empty destinations, links and non-directories are refused.
     */
    public Installed install(byte[] zip, Path destination) throws IOException {
        if (zip == null || zip.length == 0) throw new IOException("starter archive is empty");
        Path target = destination == null ? null : destination.toAbsolutePath().normalize();
        if (target == null || target.getParent() == null) throw new IOException("choose a project destination");
        Path parent = target.getParent();
        if (!Files.isDirectory(parent)) throw new IOException("destination parent does not exist: " + parent);
        refusePopulatedTarget(target);
        boolean restoreEmptyTarget = Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS);

        Path staging = Files.createTempDirectory(parent, ".analyser-template-");
        boolean installed = false;
        try {
            List<String> runnable = extract(zip, staging);
            Path root = soleProjectRoot(staging);
            Path profileInStage = root.resolve(ProjectProfile.CANONICAL_RELATIVE);
            boolean hasProfile = Files.isRegularFile(profileInStage, LinkOption.NOFOLLOW_LINKS);
            // before the move: a refused profile never becomes an installed project (edit-loop spec §I1). Every
            // settings file is a potential read grant — the root profile, a named one, a nested module's (PR #31 review).
            try (var files = Files.walk(root)) {
                for (Path settings : files.filter(p -> p.getFileName().toString().endsWith(".fluxtion-settings")
                        && Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).sorted().toList()) {
                    telamin.fluxtion.audit.analyser.analyser.config.TemplateRoots.requireContained(root, settings);
                }
            }
            List<String> commands = commandsFor(root);

            // An empty directory is allowed by D-4, but move-without-replace requires it not to exist.
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) Files.delete(target);
            try {
                Files.move(root, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                throw new IOException("atomic installation is not supported for " + parent, e);
            }
            installed = true;
            Path profile = hasProfile ? target.resolve(ProjectProfile.CANONICAL_RELATIVE) : null;
            return new Installed(target, profile, commands, runnable);
        } finally {
            deleteTree(staging);
            // Once the atomic move succeeds the target is the requested result, not staging debris.
            // Before it succeeds, no target files have been written.
            if (!installed && restoreEmptyTarget && !Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectory(target); // restore the caller's pre-existing empty directory
            }
        }
    }

    private List<String> extract(byte[] bytes, Path staging) throws IOException {
        List<String> runnable = new ArrayList<>();
        java.util.Map<String, Integer> modes = executables ? java.util.Map.of() : unixModes(bytes);
        int entries = 0;
        long total = 0;
        Set<Path> seen = new HashSet<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                entries++;
                if (entries > maxEntries) throw fail(entry, "archive has more than " + maxEntries + " entries");
                Path output = safeTarget(staging, entry);
                if (!seen.add(output)) throw fail(entry, "duplicate archive path");
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                    input.closeEntry();
                    continue;
                }
                Path parent = output.getParent();
                if (parent == null || !parent.startsWith(staging)) throw fail(entry, "entry has no safe parent");
                Files.createDirectories(parent);
                long entryBytes = 0;
                try (OutputStream out = Files.newOutputStream(output,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                    byte[] buffer = new byte[16 * 1024];
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        if (Thread.currentThread().isInterrupted()) {
                            throw fail(entry, "installation was cancelled");
                        }
                        if (read == 0) continue;
                        entryBytes += read;
                        total += read;
                        if (entryBytes > maxEntryBytes) {
                            throw fail(entry, "entry expands beyond " + maxEntryBytes + " bytes");
                        }
                        if (total > maxExpandedBytes) {
                            throw fail(entry, "archive expands beyond " + maxExpandedBytes + " bytes");
                        }
                        out.write(buffer, 0, read);
                    }
                }
                if (setFixedPermissions(output, entry.getName(), executables, modes.get(entry.getName())) && !executables) {
                    // below the archive's one top-level folder: the path the person sees in the project
                    String portable = entry.getName().replace('\\', '/');
                    runnable.add(portable.substring(portable.indexOf('/') + 1));
                }
                input.closeEntry();
            }
        }
        if (entries == 0) throw new IOException("starter archive has no entries");
        return runnable;
    }

    private static Path safeTarget(Path staging, ZipEntry entry) throws IOException {
        String raw = entry.getName();
        if (raw == null || raw.isBlank() || raw.indexOf('\0') >= 0) throw fail(entry, "blank or invalid path");
        String portable = raw.replace('\\', '/');
        if (portable.startsWith("/") || WINDOWS_ABSOLUTE.matcher(portable).matches()) {
            throw fail(entry, "absolute path");
        }
        for (String component : portable.split("/", -1)) {
            if (component.equals("..")) throw fail(entry, "parent traversal");
        }
        Path output;
        try {
            output = staging.resolve(portable).normalize();
        } catch (RuntimeException e) {
            throw fail(entry, "invalid path");
        }
        if (!output.startsWith(staging)) throw fail(entry, "path escapes the destination");
        return output;
    }

    private static Path soleProjectRoot(Path staging) throws IOException {
        List<Path> children;
        try (Stream<Path> stream = Files.list(staging)) {
            children = stream.toList();
        }
        if (children.size() != 1 || !Files.isDirectory(children.getFirst(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("starter archive must contain exactly one top-level project directory");
        }
        return children.getFirst();
    }

    private static void refusePopulatedTarget(Path target) throws IOException {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return;
        if (Files.isSymbolicLink(target)) throw new IOException("destination must not be a symbolic link: " + target);
        if (!Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("destination is not a directory: " + target);
        }
        if (!isEmpty(target)) throw new IOException("destination is not empty: " + target);
    }

    private static boolean isEmpty(Path directory) throws IOException {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.findAny().isEmpty();
        }
    }

    /**
     * The first-run commands the post-install dialog lists, in order: the sequence the template's own top-level
     * guide gives for a first run, one way to do each thing. They are fixed analyser strings selected by known root
     * filenames; no archive text is read for them or executed. Each shape is checked against what the playground
     * templates' guides say by {@code TemplateExecutablesTest} (the fixture's {@code first} lines):
     * <ul>
     *   <li>the analyser bundle ({@code run-and-export.sh}): {@code ./mvnw package} — its RUNBOOK's "First run",
     *       keyless on the shipped processor — then {@code ./run-and-export.sh}, the ONE command its CLAUDE.md
     *       names, which replaces run-server/export-audit/stop-server. {@code setup.sh} is for graph changes.</li>
     *   <li>a Spring authoring starter ({@code setup.sh}, {@code validate.sh}, {@code generate.sh}):
     *       {@code ./check-fluxtion-key.sh}, those three, then its run script. It ships only an AOT shell
     *       processor, so its default build is remote generation and needs the key its README's "Build &amp; run"
     *       checks first; the three scripts are its README's "follow RUNBOOK.md: setup.sh, validate.sh,
     *       generate.sh … Then run".</li>
     *   <li>any other starter: {@code ./check-fluxtion-key.sh} when shipped, then {@code ./run.sh} or
     *       {@code ./run-server.sh}, which build on first use — its README's "Build &amp; run".</li>
     * </ul>
     * On Windows a sequence is offered only when every step has a {@code .cmd} form; otherwise nothing is, and
     * the dialog points to the README rather than listing half a sequence. No catalogue template ships a
     * {@code .cmd} form of its run script today, so on Windows every one of them gets the README pointer.
     */
    public static List<String> commandsFor(Path root) {
        return commandsFor(root, System.getProperty("os.name", "").toLowerCase().contains("win"));
    }

    static List<String> commandsFor(Path root, boolean windows) {
        List<String> steps = new ArrayList<>(); // POSIX names: "mvnw" means "./mvnw package"
        String run = present(root, "run.sh") ? "run.sh" : present(root, "run-server.sh") ? "run-server.sh" : null;
        if (present(root, "run-and-export.sh")) {
            if (present(root, "mvnw")) steps.add("mvnw");
            steps.add("run-and-export.sh");
        } else if (run != null) {
            if (present(root, "check-fluxtion-key.sh")) steps.add("check-fluxtion-key.sh");
            if (present(root, "setup.sh") && present(root, "validate.sh") && present(root, "generate.sh")) {
                // a separate add(run): POSIX_EXECUTABLES' text for these three names is a mutation anchor
                // (installer-authoring-executables) and must occur once in this file
                steps.addAll(List.of("setup.sh", "validate.sh", "generate.sh"));
            }
            steps.add(run);
        } else if (present(root, "mvnw")) {
            steps.add("mvnw"); // a pre-M19 template with no run script: building is the one known step
        }
        List<String> commands = new ArrayList<>();
        for (String step : steps) {
            if (!windows) {
                commands.add(step.equals("mvnw") ? "./mvnw package" : "./" + step);
                continue;
            }
            String file = step.equals("mvnw") ? "mvnw.cmd" : step.substring(0, step.length() - ".sh".length()) + ".cmd";
            if (!present(root, file)) return List.of();
            commands.add(step.equals("mvnw") ? "mvnw.cmd package" : file);
        }
        return List.copyOf(commands);
    }

    private static boolean present(Path root, String file) {
        return Files.isRegularFile(root.resolve(file), LinkOption.NOFOLLOW_LINKS);
    }

    /**
     * An example's file keeps an executable bit only when its name is {@code mvnw} or ends {@code .sh} AND the archive
     * records it as an executable regular file. An archive that records no Unix mode for it (one made where there is
     * none) makes nothing runnable, and a file the archive marks as a link, or as not executable, never gets one.
     */
    static boolean exampleExecutable(String archiveName, Integer mode) {
        String portable = archiveName.replace('\\', '/');
        String base = portable.substring(portable.lastIndexOf('/') + 1);
        if (!base.equals("mvnw") && !(base.endsWith(".sh") && base.length() > ".sh".length())) return false;
        if (mode == null) return false;
        int type = mode & 0170000;
        return (type == 0100000 || type == 0) && (mode & 0111) != 0;
    }

    /**
     * The Unix mode the archive's central directory records for each entry made on Unix (host 3), by entry name. Read
     * from the bytes only; a malformed or ZIP64 directory yields what was read so far, never an error — the extraction
     * itself is the boundary that refuses a bad archive.
     */
    static java.util.Map<String, Integer> unixModes(byte[] zip) {
        java.util.Map<String, Integer> out = new java.util.HashMap<>();
        int eocd = -1;
        for (int i = zip.length - 22; i >= Math.max(0, zip.length - 22 - 0xFFFF); i--) {
            if (u32(zip, i) == 0x06054b50L) {
                eocd = i;
                break;
            }
        }
        if (eocd < 0) return out;
        int count = u16(zip, eocd + 10);
        long offset = u32(zip, eocd + 16);
        if (offset >= zip.length) return out;
        int at = (int) offset;
        for (int n = 0; n < count && at + 46 <= zip.length && u32(zip, at) == 0x02014b50L; n++) {
            int host = zip[at + 5] & 0xff;
            int nameLength = u16(zip, at + 28);
            int extraLength = u16(zip, at + 30);
            int commentLength = u16(zip, at + 32);
            long external = u32(zip, at + 38);
            if (at + 46 + nameLength > zip.length) break;
            String name = new String(zip, at + 46, nameLength, java.nio.charset.StandardCharsets.UTF_8);
            if (host == 3) out.put(name, (int) (external >>> 16));
            at += 46 + nameLength + extraLength + commentLength;
        }
        return out;
    }

    private static int u16(byte[] b, int i) {
        return (b[i] & 0xff) | (b[i + 1] & 0xff) << 8;
    }

    private static long u32(byte[] b, int i) {
        return ((long) (b[i] & 0xff)) | (long) (b[i + 1] & 0xff) << 8 | (long) (b[i + 2] & 0xff) << 16
                | (long) (b[i + 3] & 0xff) << 24;
    }

    /** @return whether the file was given an executable bit */
    private static boolean setFixedPermissions(Path file, String archiveName, boolean executables, Integer archiveMode)
            throws IOException {
        try {
            Set<PosixFilePermission> permissions = new HashSet<>(Set.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ));
            String portable = archiveName.replace('\\', '/');
            String base = portable.substring(portable.lastIndexOf('/') + 1);
            // The archive has one top-level project directory. Only named root lifecycle files may
            // become executable; neither a matching nested basename nor an arbitrary .sh qualifies.
            if (executables && portable.indexOf('/') == portable.lastIndexOf('/') && POSIX_EXECUTABLES.contains(base)) {
                permissions.add(PosixFilePermission.OWNER_EXECUTE);
                permissions.add(PosixFilePermission.GROUP_EXECUTE);
                permissions.add(PosixFilePermission.OTHERS_EXECUTE);
            }
            if (!executables && exampleExecutable(portable, archiveMode)) {
                permissions.add(PosixFilePermission.OWNER_EXECUTE);
                permissions.add(PosixFilePermission.GROUP_EXECUTE);
                permissions.add(PosixFilePermission.OTHERS_EXECUTE);
            }
            Files.setPosixFilePermissions(file, permissions);
            return permissions.contains(PosixFilePermission.OWNER_EXECUTE);
        } catch (UnsupportedOperationException ignored) {
            // Windows has no POSIX mode. The archive's mode was never applied, which is the property.
            return false;
        }
    }

    private static IOException fail(ZipEntry entry, String reason) {
        return new IOException("refusing zip entry '" + (entry == null ? "<unknown>" : entry.getName())
                + "': " + reason);
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                if (error != null) throw error;
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
