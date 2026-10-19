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
    public static DesignDiscovery scan(DesignFiles access) {
        Map<String, FileRef> found = new TreeMap<>();
        Set<Path> visited = new HashSet<>();
        Set<String> unreadable = new TreeSet<>();
        int[] counts = {0, 0};
        try {
            for (String root : access.roots()) {
                Path directory = access.directory(Path.of(root));
                Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                    @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        if (!visited.add(dir.toRealPath())) return FileVisitResult.SKIP_SUBTREE;
                        if (++counts[0] > MAX_ENTRIES) throw new ScanLimit("20,000-entry scan limit exceeded");
                        access.directory(dir); // no directory links are followed; recheck each authorised real path
                        return FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
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
                            unreadable.add(file.toAbsolutePath().normalize().toString());
                        }
                        return FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult visitFileFailed(Path file, IOException error) throws IOException { throw error; }
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
