package telamin.fluxtion.audit.analyser.analyser.project;

import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture;
import telamin.fluxtion.audit.analyser.analyser.session.ProjectOutputsState;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** M81 D filesystem adapter: bounded metadata observations only; no debounce, offers, selection or execution. */
public final class ProjectOutputScanner {
    public static final int MAX_DEPTH = 12;
    public static final int MAX_VISITS = 10_000;
    public static final int MAX_FILES = 100;
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
