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
        Map<Path, Optional<String>> singleModules = new HashMap<>();
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
                        var observed = observe(path, kind, real, true, singleModules);
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
        return observe(path, kind, fence, content, new HashMap<>());
    }
    private static Observed observe(Path path, ProjectOutputsState.Kind kind, Path fence, boolean content, Map<Path, Optional<String>> singleModules) {
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
            return new Observed(real, kind, content ? withContent(real, metadata) : metadata, logicalPath(relative, root, singleModules), buildCopy(relative));
        } catch (IOException | RuntimeException unavailable) { return null; }
    }
    /** The same graph wherever the build copied it, and only that (§3.5.1 rule 1). An output root is recognised by its
     *  whole shape, never by a package directory that happens to be called {@code test} or {@code resources}:
     *  {@code src/<set>/resources}, Maven {@code target/classes} / {@code target/test-classes} /
     *  {@code target/fluxtion-staging/resources}, Gradle {@code build/resources/<set>} / {@code build/classes/<lang>/<set>},
     *  IntelliJ {@code out/production|test/<module>}. The key is module | source set | path below the root; a path with
     *  no recognised root keeps its whole relative path. */
    static String logicalPath(Path relative) { return logicalPath(relative, null); }
    static String logicalPath(Path relative, Path root) {
        return logicalPath(relative, root, new HashMap<>());
    }
    private static String logicalPath(Path relative, Path root, Map<Path, Optional<String>> singleModules) {
        var r = outputRoot(relative, root, singleModules);
        return r == null ? unix(relative) : r.module() + "|" + r.set() + "|" + unix(relative.subpath(r.start(), relative.getNameCount()));
    }
    /** The first recognised source or output root along the path; {@code build} says it is build output. */
    private record Root(String module, String set, int start, boolean build) { }
    private static Root outputRoot(Path relative, Path root, Map<Path, Optional<String>> singleModules) {
        int n = relative.getNameCount();
        java.util.function.IntFunction<String> seg = i -> i < n - 1 ? relative.getName(i).toString() : "";
        for (int i = 0; i < n - 1; i++) {
            String set = null, module = i == 0 ? "" : unix(relative.subpath(0, i));
            int start = -1;
            switch (seg.apply(i)) {
                case "src" -> { if (seg.apply(i + 2).equals("resources") && !seg.apply(i + 1).isEmpty()) { set = seg.apply(i + 1); start = i + 3; } }
                case "target" -> {
                    if (seg.apply(i + 1).equals("classes")) { set = "main"; start = i + 2; }
                    else if (seg.apply(i + 1).equals("test-classes")) { set = "test"; start = i + 2; }
                    else if (seg.apply(i + 1).equals("fluxtion-staging") && seg.apply(i + 2).equals("resources")) { set = "main"; start = i + 3; }
                }
                case "build" -> {
                    if (seg.apply(i + 1).equals("resources") && !seg.apply(i + 2).isEmpty()) { set = seg.apply(i + 2); start = i + 3; }
                    else if (seg.apply(i + 1).equals("classes") && !seg.apply(i + 3).isEmpty()) { set = seg.apply(i + 3); start = i + 4; }
                }
                case "out" -> {
                    String kind = seg.apply(i + 1), name = seg.apply(i + 2);
                    if ((kind.equals("production") || kind.equals("test")) && !name.isEmpty()) {
                        set = kind.equals("test") ? "test" : "main"; start = i + 3;
                        // IntelliJ names the module. It is a directory of its own in a multi-module project; it is
                        // dropped only for a single-module one. An unknown name stays distinct: a spare offer is
                        // the safe failure, merging two modules' graphs is not (D12).
                        if (root != null && java.nio.file.Files.isDirectory(root.resolve(module).resolve(name)))
                            module = module.isEmpty() ? name : module + "/" + name;
                        else if (root != null && !name.equals(singleModules.computeIfAbsent(root.resolve(module), ProjectOutputScanner::singleModule).orElse(null)))
                            module = (module.isEmpty() ? "" : module + "/") + "out:" + name;
                    }
                }
                default -> { }
            }
            if (start > 0 && start < n) return new Root(module, set, start, !seg.apply(i).equals("src"));
        }
        return null;
    }
    /** Conservative, bounded filesystem evidence, cached only for this scan. Unknown/truncated layouts
     * keep IntelliJ names distinct; a spare offer is safer than merging different graphs. */
    private static Optional<String> singleModule(Path base) {
        if (!Files.isDirectory(base.resolve("src"), LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        Set<String> outputModules = new HashSet<>();
        try {
            for (String set : List.of("production", "test")) {
                Path outputs = base.resolve("out").resolve(set);
                if (!Files.exists(outputs, LinkOption.NOFOLLOW_LINKS)) continue;
                if (!Files.isDirectory(outputs, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
                try (var children = Files.list(outputs)) {
                    var entries = children.limit(1_001).toList();
                    if (entries.size() > 1_000) return Optional.empty();
                    for (Path entry : entries) {
                        if (Files.isSymbolicLink(entry)) return Optional.empty();
                        if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS))
                            outputModules.add(entry.getFileName().toString());
                    }
                }
            }
            if (outputModules.size() != 1) return Optional.empty();
            boolean[] certain = {true};
            int[] visited = {0};
            Files.walkFileTree(base, EnumSet.noneOf(FileVisitOption.class), MAX_DEPTH, new SimpleFileVisitor<>() {
                private FileVisitResult unknown() { certain[0] = false; return FileVisitResult.TERMINATE; }
                @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (++visited[0] > 1_000) return unknown();
                    if (dir.equals(base)) return FileVisitResult.CONTINUE;
                    String name = dir.getFileName().toString();
                    if (dir.equals(base.resolve("src"))) return FileVisitResult.SKIP_SUBTREE;
                    if (name.equals("src")) return unknown(); // includes nested modules, not just direct children
                    if (Set.of(".git", ".claude", "node_modules").contains(name))
                        return FileVisitResult.SKIP_SUBTREE;
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    return ++visited[0] > 1_000 || attrs.isSymbolicLink() || attrs.isDirectory()
                            ? unknown() : FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFileFailed(Path file, IOException failure) { return unknown(); }
                @Override public FileVisitResult postVisitDirectory(Path dir, IOException failure) {
                    return failure == null ? FileVisitResult.CONTINUE : unknown();
                }
            });
            return certain[0] ? Optional.of(outputModules.iterator().next()) : Optional.empty();
        } catch (IOException | RuntimeException unreadable) { return Optional.empty(); }
    }
    private static String unix(Path p) { return p.toString().replace('\\', '/'); }
    /** Build output by the recognised root's shape: a package directory named {@code build} below a source root is
     *  not build output. A path with no recognised root falls back to any {@code target}/{@code build}/{@code out}. */
    static boolean buildCopy(Path relative) {
        var r = outputRoot(relative, null, new HashMap<>());
        if (r != null) return r.build();
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
