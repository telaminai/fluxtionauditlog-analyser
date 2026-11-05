package telamin.fluxtion.audit.analyser.analyser.project;

import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture;
import telamin.fluxtion.audit.analyser.analyser.session.ProjectOutputsState;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** M81 D filesystem adapter: bounded observations; identical Maven GraphML copies are omitted. No offers or opening. */
public final class ProjectOutputScanner {
    public static final int MAX_DEPTH = 12;
    public static final int MAX_VISITS = 10_000;
    public static final int MAX_FILES = 100;
    private static final long MAX_COPY_COMPARE_BYTES = 8 * 1024 * 1024;
    public record Observed(Path realPath, ProjectOutputsState.Kind kind, ProjectOutputsState.Revision revision) { }
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
        long[] comparisonBudget = {32 * 1024 * 1024};
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
        return new Scan(out.values().stream()
                .filter(o -> o.kind() != ProjectOutputsState.Kind.GRAPHML
                        || !identicalBuildCopy(real, o.realPath(), out.keySet(), comparisonBudget))
                .sorted(Comparator.comparing(o -> o.realPath().toString())).toList(), complete[0]);
    }

    /** Only known Maven resource copies with an identical source retained in this scan are redundant.
     * Different contents, standalone build outputs, links outside the project and oversized files stay distinct. */
    private static boolean identicalBuildCopy(Path root, Path path, Set<Path> observed, long[] comparisonBudget) {
        Path relative = root.relativize(path);
        Path source = null;
        for (String prefix : List.of("target/classes", "target/fluxtion-staging/resources")) {
            Path buildRoot = Path.of(prefix);
            if (relative.startsWith(buildRoot) && relative.getNameCount() > buildRoot.getNameCount()) {
                source = root.resolve("src/main/resources").resolve(buildRoot.relativize(relative));
                break;
            }
        }
        if (source == null) return false;
        var copyBefore = observe(path, ProjectOutputsState.Kind.GRAPHML, root);
        var sourceBefore = observe(source, ProjectOutputsState.Kind.GRAPHML, root);
        if (copyBefore == null || sourceBefore == null
                || !sourceBefore.realPath().startsWith(root.resolve("src/main/resources"))
                || !observed.contains(sourceBefore.realPath())
                || copyBefore.revision().size() > MAX_COPY_COMPARE_BYTES
                || copyBefore.revision().size() != sourceBefore.revision().size()
                || copyBefore.revision().size() * 2 > comparisonBudget[0]) return false;
        comparisonBudget[0] -= copyBefore.revision().size() * 2;
        try {
            return Files.mismatch(copyBefore.realPath(), sourceBefore.realPath()) == -1
                    && copyBefore.equals(observe(path, ProjectOutputsState.Kind.GRAPHML, root))
                    && sourceBefore.equals(observe(source, ProjectOutputsState.Kind.GRAPHML, root));
        } catch (IOException | RuntimeException changed) { return false; }
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
    /** Fresh identity for a successful-open receipt or an effect's revision precondition; no file contents read. */
    public static Observed observe(Path path, ProjectOutputsState.Kind kind, Path fence) {
        try {
            Path real = path.toRealPath();
            if (fence != null && !real.startsWith(fence.toRealPath())) return null;
            var attrs = Files.readAttributes(real, BasicFileAttributes.class);
            if (!attrs.isRegularFile() || attrs.size() == 0) return null;
            String key = attrs.fileKey() == null ? null : LocalAgentPosture.digest(attrs.fileKey().toString());
            return new Observed(real, kind, new ProjectOutputsState.Revision(attrs.size(),
                    attrs.lastModifiedTime().to(TimeUnit.NANOSECONDS), key));
        } catch (IOException | RuntimeException unavailable) { return null; }
    }
}
