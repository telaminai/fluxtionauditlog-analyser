package telamin.fluxtion.audit.analyser.analyser.design;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Pattern;

/** Bounded, read-only source-root adapter. Candidates are definitions, not evidence of a relationship to a run. */
public record DesignDiscovery(String kind, List<FileRef> files, String source) {
    public record FileRef(String path, String role) { }
    public DesignDiscovery { files = List.copyOf(files); }
    public static final int MAX_FILES = 10_000;
    private static final int MAX_ENTRIES = 20_000;
    private static final String BUILDER = "com.telamin.fluxtion.builder.compile.config.FluxtionGraphBuilder";
    private static final Pattern IMPORT = Pattern.compile("(?m)^\\s*import\\s+" + Pattern.quote(BUILDER) + "\\s*;");
    private static final Pattern CLASS = Pattern.compile("\\bclass\\s+[\\w$]+[^\\r\\n{]*?\\bimplements\\s+([^\\r\\n{]+)");
    private static final Pattern SPRING = Pattern.compile("<(?:[\\w.-]+:)?bean\\b[^>]*\\bclass\\s*=\\s*(['\"])[^'\"]*FluxtionSpringConfig\\1", Pattern.DOTALL);
    public static DesignDiscovery none() { return new DesignDiscovery("none", List.of(), "none"); }
    public static DesignDiscovery unavailable() { return new DesignDiscovery("none", List.of(), "unavailable: source scan could not be completed within its limits or authorised roots"); }
    public static boolean isBuilder(String text) {
        boolean imported = IMPORT.matcher(text).find();
        var classes = CLASS.matcher(text);
        while (classes.find()) for (String token : classes.group(1).split(",")) {
            String type = token.trim();
            if (type.equals(BUILDER) || imported && type.equals("FluxtionGraphBuilder")) return true;
        }
        return false;
    }
    public static DesignDiscovery scan(DesignFiles access) {
        Map<String, FileRef> found = new TreeMap<>();
        Set<Path> visited = new HashSet<>();
        int[] counts = {0, 0};
        try {
            if (access.roots().size() > MAX_FILES) return unavailable();
            for (String root : access.roots()) {
                Path directory = access.directory(Path.of(root));
                Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                    @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        if (!visited.add(dir.toRealPath())) return FileVisitResult.SKIP_SUBTREE;
                        if (++counts[0] > MAX_ENTRIES) throw new IOException("source metadata limit");
                        access.directory(dir); // no directory links are followed; recheck each authorised real path
                        return FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        if (++counts[0] > MAX_ENTRIES) throw new IOException("source metadata limit");
                        if (!attrs.isRegularFile() || !visited.add(file.toRealPath())) return FileVisitResult.CONTINUE;
                        if (++counts[1] > MAX_FILES) throw new IOException("source file limit");
                        String name = file.getFileName().toString();
                        if (!name.endsWith(".java") && !name.endsWith(".xml")) return FileVisitResult.CONTINUE;
                        String text = access.read(file);
                        String role = name.endsWith(".java") ? (isBuilder(text) ? "builder" : null)
                                : (SPRING.matcher(text.replaceAll("(?s)<!--.*?-->", "")).find() ? "spring" : null);
                        if (role != null) {
                            String path = access.resolve(file.toString()).toString();
                            found.put(path, new FileRef(path, role));
                        }
                        return FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult visitFileFailed(Path file, IOException error) throws IOException { throw error; }
                });
            }
        } catch (IOException | SecurityException | InvalidPathException error) { return unavailable(); }
        Set<String> roles = new HashSet<>(); found.values().forEach(f -> roles.add(f.role()));
        String kind = roles.size() > 1 ? "mixed" : roles.isEmpty() ? "none" : roles.iterator().next();
        return new DesignDiscovery(kind, List.copyOf(found.values()), found.isEmpty() ? "none" : "source-scan");
    }
}
