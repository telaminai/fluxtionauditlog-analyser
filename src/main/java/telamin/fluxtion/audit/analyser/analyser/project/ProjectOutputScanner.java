package telamin.fluxtion.audit.analyser.analyser.project;

import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture;
import telamin.fluxtion.audit.analyser.analyser.session.ProjectOutputsState;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** M81 D filesystem adapter: bounded observations only; no debounce, offers, selection or execution. Logs are
 *  metadata only; a GraphML's bytes are hashed when its metadata changes (§3.5.1), never parsed. */
public final class ProjectOutputScanner {
    public static final int MAX_DEPTH = 12;
    public static final int MAX_VISITS = 10_000;
    public static final int MAX_FILES = 100;
    /** Above this a graph keeps metadata identity only; hashing it every change would cost more than a spare offer. */
    public static final long MAX_DIGEST_BYTES = 16L << 20;
    private static final Set<String> BUILD_OUTPUT = Set.of("target", "build", "out");
    private static final Map<Path, ProjectOutputsState.Revision> DIGESTS = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Path, ProjectOutputsState.Revision> eldest) { return size() > 256; }
    });
    /** {@code logicalPath}/{@code buildCopy} are facts about a GraphML copy's place; the node decides what they mean. */
    public record Observed(Path realPath, ProjectOutputsState.Kind kind, ProjectOutputsState.Revision revision,
                           String logicalPath, boolean buildCopy) {
        public Observed(Path realPath, ProjectOutputsState.Kind kind, ProjectOutputsState.Revision revision) {
            this(realPath, kind, revision, null, false);
        }
    }
    public record Scan(List<Observed> files, boolean complete) {
        public Scan { files = List.copyOf(files); }
    }
    private ProjectOutputScanner() { }

    public static Scan scan(Path root, List<Path> declaredLogDirs, Path currentLog) {
        final Path real;
        try { real = root.toRealPath(); }
        catch (IOException | RuntimeException unavailable) { return new Scan(List.of(), false); }
        Set<Path> logDirs = new LinkedHashSet<>();
        for (Path p : declaredLogDirs) addDirectory(logDirs, p, real);
        // An explicitly open local log adds its directory, not the rest of the machine. Other projects stay outside.
        if (currentLog != null && currentLog.getParent() != null) addDirectory(logDirs, currentLog.getParent(), real);
        Map<Path, Observed> out = new LinkedHashMap<>();
        boolean[] complete = {true};
        int[] visited = {0};
        try {
            Files.walkFileTree(real, EnumSet.noneOf(FileVisitOption.class), MAX_DEPTH, new SimpleFileVisitor<>() {
                @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (++visited[0] > MAX_VISITS) { complete[0] = false; return FileVisitResult.TERMINATE; }
                    String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                    return !dir.equals(real) && (name.equals(".git") || name.equals(".claude") || name.equals("node_modules"))
                            ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                    if (++visited[0] > MAX_VISITS) {
                        complete[0] = false; return FileVisitResult.TERMINATE;
                    }
                    String name = path.getFileName().toString();
                    ProjectOutputsState.Kind kind = name.endsWith(".graphml") ? ProjectOutputsState.Kind.GRAPHML
                            : logDirs.stream().anyMatch(path::startsWith) && logName(name) ? ProjectOutputsState.Kind.LOG : null;
                    if (kind != null) {
                        var observed = observe(path, kind, real);
                        if (observed != null) {
                            if (!out.containsKey(observed.realPath()) && out.size() >= MAX_FILES) {
                                complete[0] = false; return FileVisitResult.TERMINATE;
                            }
                            out.put(observed.realPath(), observed);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFileFailed(Path file, IOException failure) {
                    complete[0] = false; return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException | RuntimeException failure) { complete[0] = false; }
        return new Scan(out.values().stream().sorted(Comparator.comparing(o -> o.realPath().toString())).toList(), complete[0]);
    }

    private static boolean logName(String name) {
        return name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".log") || name.endsWith(".jsonl");
    }
    private static void addDirectory(Set<Path> dirs, Path p, Path fence) {
        if (p == null) return;
        try {
            Path real = p.toRealPath();
            if (real.startsWith(fence) && Files.isDirectory(real)) dirs.add(real);
        } catch (IOException | RuntimeException unavailable) { /* no speculative scope */ }
    }
    /** Fresh identity for a successful-open receipt or an effect's revision precondition; a log's contents are never read. */
    public static Observed observe(Path path, ProjectOutputsState.Kind kind, Path fence) { return observe(path, kind, fence, true); }
    /** Identity without reading any bytes, for a check on the EDT; a GraphML's revision then has no content digest. */
    public static Observed observeMetadata(Path path, ProjectOutputsState.Kind kind, Path fence) { return observe(path, kind, fence, false); }
    private static Observed observe(Path path, ProjectOutputsState.Kind kind, Path fence, boolean content) {
        try {
            Path real = path.toRealPath();
            Path root = fence == null ? null : fence.toRealPath();
            if (root != null && !real.startsWith(root)) return null;
            var attrs = Files.readAttributes(real, BasicFileAttributes.class);
            if (!attrs.isRegularFile() || attrs.size() == 0) return null;
            String key = attrs.fileKey() == null ? null : LocalAgentPosture.digest(attrs.fileKey().toString());
            var metadata = new ProjectOutputsState.Revision(attrs.size(), attrs.lastModifiedTime().to(TimeUnit.NANOSECONDS), key);
            if (kind != ProjectOutputsState.Kind.GRAPHML) return new Observed(real, kind, metadata);
            Path relative = root == null ? real.getFileName() : root.relativize(real);
            return new Observed(real, kind, content ? withContent(real, metadata) : metadata, logicalPath(relative), buildCopy(relative));
        } catch (IOException | RuntimeException unavailable) { return null; }
    }
    /** The same graph wherever the build copied it, and only that: the module (the path before its first
     *  {@code src}/{@code target}/{@code build}/{@code out} segment), the source set ({@code test} when any segment
     *  names it, else {@code main}) and the path below the last {@code resources}/{@code classes} segment. A path
     *  without an output root keeps its whole relative path. */
    static String logicalPath(Path relative) {
        int start = 0, module = -1;
        boolean test = false;
        for (int i = 0; i < relative.getNameCount() - 1; i++) {
            String name = relative.getName(i).toString();
            if (name.equals("resources") || name.equals("classes") || name.equals("test-classes")) start = i + 1;
            if (module < 0 && (name.equals("src") || BUILD_OUTPUT.contains(name))) module = i;
            if (name.equals("test") || name.equals("test-classes")) test = true;
        }
        String below = relative.subpath(start, relative.getNameCount()).toString().replace('\\', '/');
        if (start == 0) return below;
        String prefix = module <= 0 ? "" : relative.subpath(0, module).toString().replace('\\', '/');
        return prefix + "|" + (test ? "test" : "main") + "|" + below;
    }
    static boolean buildCopy(Path relative) {
        for (int i = 0; i < relative.getNameCount() - 1; i++) if (BUILD_OUTPUT.contains(relative.getName(i).toString())) return true;
        return false;
    }
    /** Re-reads the bytes only when size/mtime/file key changed since the last observation of this path. */
    private static ProjectOutputsState.Revision withContent(Path real, ProjectOutputsState.Revision metadata) {
        if (metadata.size() > MAX_DIGEST_BYTES) return metadata;
        var known = DIGESTS.get(real);
        if (known != null && known.size() == metadata.size() && known.modifiedNanos() == metadata.modifiedNanos()
                && Objects.equals(known.fileKeyDigest(), metadata.fileKeyDigest())) return known;
        try {
            byte[] bytes = Files.readAllBytes(real);
            if (bytes.length != metadata.size()) return metadata;      // rewritten mid-read: the next poll sees it settle
            var digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
            var revision = new ProjectOutputsState.Revision(metadata.size(), metadata.modifiedNanos(), metadata.fileKeyDigest(), digest);
            DIGESTS.put(real, revision);
            return revision;
        } catch (IOException | java.security.NoSuchAlgorithmException unreadable) { return metadata; }
    }
}
