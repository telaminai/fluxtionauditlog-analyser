package telamin.fluxtion.audit.analyser.analyser.design;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Pattern;

/** Bounded, read-only source-root adapter. Candidates are definitions, not evidence of a relationship to a run. */
public record DesignDiscovery(String kind, List<FileRef> files, String source, List<String> unreadableFiles) {
    public record FileRef(String path, String role) { }
    public DesignDiscovery {
        files = List.copyOf(files);
        unreadableFiles = List.copyOf(unreadableFiles);
    }
    public DesignDiscovery(String kind, List<FileRef> files, String source) { this(kind, files, source, List.of()); }
    public static final int MAX_FILES = 10_000;
    private static final int MAX_ENTRIES = 20_000;
    private static final String BUILDER = "com.telamin.fluxtion.builder.compile.config.FluxtionGraphBuilder";
    private static final Pattern IMPORT = Pattern.compile("(?m)^\\s*import\\s+" + Pattern.quote(BUILDER) + "\\s*;");
    private static final Pattern CLASS = Pattern.compile("\\bclass\\s+[\\w$]+[^\\r\\n{]*?\\bimplements\\s+([^\\r\\n{]+)");
    private static final Pattern SPRING = Pattern.compile("<(?:[\\w.-]+:)?bean\\b[^>]*\\bclass\\s*=\\s*(['\"])[^'\"]*FluxtionSpringConfig\\1", Pattern.DOTALL);
    public static DesignDiscovery none() { return new DesignDiscovery("none", List.of(), "none"); }
    public static DesignDiscovery unavailable() { return unavailable("an authorised source root or directory could not be scanned"); }
    private static DesignDiscovery unavailable(String reason) { return new DesignDiscovery("none", List.of(), "unavailable: " + reason); }
    public String warning() {
        int shown = Math.min(10, unreadableFiles.size());
        return unreadableFiles.isEmpty() ? "" : unreadableFiles.size() + (unreadableFiles.size() == 1
                ? " file could not be read: " : " files could not be read: ") + String.join(", ", unreadableFiles.subList(0, shown))
                + (shown < unreadableFiles.size() ? ", and " + (unreadableFiles.size() - shown) + " more" : "");
    }
    private static final class ScanLimit extends IOException {
        ScanLimit(String reason) { super(reason); }
    }
    public static boolean isBuilder(String text) {
        boolean imported = IMPORT.matcher(text).find();
        var classes = CLASS.matcher(text);
        while (classes.find()) for (String token : classes.group(1).split(",")) {
            String type = token.trim();
            if (type.equals(BUILDER) || imported && type.equals("FluxtionGraphBuilder")) return true;
        }
        return false;
    }
    /**
     * S4 (2026-11-04): directories no walk descends into, below a root. Every build copies the design to
     * {@code target/classes/}, so a root covering {@code target/} found two designs and the zero-setup auto-open (exactly
     * one) was lost; {@code .git/} and build trees also spent the entry budget. A root that IS one of these is still
     * walked: only what lies below a root is skipped.
     */
    public static final Set<String> SKIPPED_DIRECTORIES = Set.of(".git", "node_modules", ".idea");
    /**
     * Build-output names, skipped only beside a build file ({@link #BUILD_FILES}): a Java package may be called
     * {@code build} or {@code target} ({@code src/main/java/com/acme/build/}), and a builder there is a definition.
     */
    public static final Set<String> BUILD_OUTPUT_DIRECTORIES = Set.of("target", "build");
    static final List<String> BUILD_FILES = List.of("pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle",
            "settings.gradle.kts", "build.xml");
    static boolean skipped(Path dir) {
        Path name = dir.getFileName();
        if (name == null) return false;
        if (SKIPPED_DIRECTORIES.contains(name.toString())) return true;
        if (!BUILD_OUTPUT_DIRECTORIES.contains(name.toString()) || dir.getParent() == null) return false;
        for (String file : BUILD_FILES) if (Files.isRegularFile(dir.getParent().resolve(file), LinkOption.NOFOLLOW_LINKS)) return true;
        return false;
    }
    public static DesignDiscovery scan(DesignFiles access) { return scan(access, path -> { }); }
    /** {@code beforeEntry} runs as each entry is visited: the test seam for a file vanishing mid-walk. */
    static DesignDiscovery scan(DesignFiles access, java.util.function.Consumer<Path> beforeEntry) {
        Map<String, FileRef> found = new TreeMap<>();
        Set<Path> visited = new HashSet<>();
        Set<String> unreadable = new TreeSet<>();
        int[] counts = {0, 0};
        try {
            for (String root : access.roots()) {
                Path directory = access.directory(Path.of(root));
                Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                    @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        beforeEntry.accept(dir);
                        if (!dir.equals(directory) && skipped(dir)) return FileVisitResult.SKIP_SUBTREE;
                        try {
                            if (!visited.add(dir.toRealPath())) return FileVisitResult.SKIP_SUBTREE;
                            if (++counts[0] > MAX_ENTRIES) throw new ScanLimit("20,000-entry scan limit exceeded");
                            access.directory(dir); // no directory links are followed; recheck each authorised real path
                        } catch (NoSuchFileException vanished) { return FileVisitResult.SKIP_SUBTREE; } // removed mid-walk
                        return FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        beforeEntry.accept(file);
                        if (++counts[0] > MAX_ENTRIES) throw new ScanLimit("20,000-entry scan limit exceeded");
                        if (!attrs.isRegularFile() || !visited.add(file.toAbsolutePath().normalize())) return FileVisitResult.CONTINUE;
                        if (++counts[1] > MAX_FILES) throw new ScanLimit("10,000-file scan limit exceeded");
                        String name = file.getFileName().toString();
                        if (!name.endsWith(".java") && !name.endsWith(".xml")) return FileVisitResult.CONTINUE;
                        try {
                            String text = access.read(file);
                            String role = name.endsWith(".java") ? (isBuilder(text) ? "builder" : null)
                                    : (SPRING.matcher(text.replaceAll("(?s)<!--.*?-->", "")).find() ? "spring" : null);
                            if (role != null) {
                                String path = access.resolve(file.toString()).toString();
                                found.put(path, new FileRef(path, role));
                            }
                        } catch (IOException | SecurityException error) {
                            // removed mid-walk (a concurrent build): no longer a candidate, and not unreadable either
                            if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return FileVisitResult.CONTINUE;
                            unreadable.add(file.toAbsolutePath().normalize().toString());
                        }
                        return FileVisitResult.CONTINUE;
                    }
                    /**
                     * An entry that vanished between listing and visiting is skipped; one that cannot be opened is named
                     * as unreadable. Neither fails the scan (S4: a build running beside the analyser removes files).
                     */
                    @Override public FileVisitResult visitFileFailed(Path file, IOException error) throws IOException {
                        if (file.equals(directory)) throw error; // the root itself: the scan is unavailable, as before
                        if (!(error instanceof NoSuchFileException)) unreadable.add(file.toAbsolutePath().normalize().toString());
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        } catch (ScanLimit limit) { return unavailable(limit.getMessage()); }
        catch (IOException | SecurityException | InvalidPathException error) { return unavailable(); }
        Set<String> roles = new HashSet<>(); found.values().forEach(f -> roles.add(f.role()));
        String kind = roles.size() > 1 ? "mixed" : roles.isEmpty() ? "none" : roles.iterator().next();
        return new DesignDiscovery(kind, List.copyOf(found.values()), found.isEmpty() && unreadable.isEmpty() ? "none" : "source-scan",
                List.copyOf(unreadable));
    }
}
