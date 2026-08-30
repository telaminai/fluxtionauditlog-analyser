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
    private static final Set<String> POSIX_EXECUTABLES = Set.of(
            "mvnw", "run-server.sh", "export-audit.sh", "stop-server.sh", "check-fluxtion-key.sh");

    public record Installed(Path projectRoot, Path profile, List<String> commands) {
        public Installed {
            commands = List.copyOf(commands == null ? List.of() : commands);
        }
    }

    private final int maxEntries;
    private final long maxEntryBytes;
    private final long maxExpandedBytes;

    public TemplateArchive() {
        this(MAX_ENTRIES, MAX_ENTRY_BYTES, MAX_EXPANDED_BYTES);
    }

    TemplateArchive(int maxEntries, long maxEntryBytes, long maxExpandedBytes) {
        if (maxEntries < 1 || maxEntryBytes < 1 || maxExpandedBytes < 1) {
            throw new IllegalArgumentException("archive limits must be positive");
        }
        this.maxEntries = maxEntries;
        this.maxEntryBytes = maxEntryBytes;
        this.maxExpandedBytes = maxExpandedBytes;
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
            extract(zip, staging);
            Path root = soleProjectRoot(staging);
            Path profileInStage = root.resolve(ProjectProfile.CANONICAL_RELATIVE);
            boolean hasProfile = Files.isRegularFile(profileInStage, LinkOption.NOFOLLOW_LINKS);
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
            return new Installed(target, profile, commands);
        } finally {
            deleteTree(staging);
            // Once the atomic move succeeds the target is the requested result, not staging debris.
            // Before it succeeds, no target files have been written.
            if (!installed && restoreEmptyTarget && !Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectory(target); // restore the caller's pre-existing empty directory
            }
        }
    }

    private void extract(byte[] bytes, Path staging) throws IOException {
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
                setFixedPermissions(output, entry.getName());
                input.closeEntry();
            }
        }
        if (entries == 0) throw new IOException("starter archive has no entries");
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

    /** Commands are fixed analyser strings selected by known filenames; no archive text is executed. */
    public static List<String> commandsFor(Path root) {
        List<String> commands = new ArrayList<>();
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (Files.isRegularFile(root.resolve(windows ? "mvnw.cmd" : "mvnw"))) {
            commands.add(windows ? "mvnw.cmd package" : "./mvnw package");
        }
        addCommand(root, commands, windows, "run-server");
        addCommand(root, commands, windows, "export-audit");
        addCommand(root, commands, windows, "stop-server");
        return List.copyOf(commands);
    }

    private static void addCommand(Path root, List<String> commands, boolean windows, String stem) {
        String file = stem + (windows ? ".cmd" : ".sh");
        if (Files.isRegularFile(root.resolve(file))) commands.add(windows ? file : "./" + file);
    }

    private static void setFixedPermissions(Path file, String archiveName) throws IOException {
        try {
            Set<PosixFilePermission> permissions = new HashSet<>(Set.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ));
            String portable = archiveName.replace('\\', '/');
            String base = portable.substring(portable.lastIndexOf('/') + 1);
            // The archive has one top-level project directory. Only named root lifecycle files may
            // become executable; neither a matching nested basename nor an arbitrary .sh qualifies.
            if (portable.indexOf('/') == portable.lastIndexOf('/') && POSIX_EXECUTABLES.contains(base)) {
                permissions.add(PosixFilePermission.OWNER_EXECUTE);
                permissions.add(PosixFilePermission.GROUP_EXECUTE);
                permissions.add(PosixFilePermission.OTHERS_EXECUTE);
            }
            Files.setPosixFilePermissions(file, permissions);
        } catch (UnsupportedOperationException ignored) {
            // Windows has no POSIX mode. The archive's mode was never applied, which is the property.
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
