package telamin.fluxtion.audit.analyser.analyser.design;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** The read adapter for M66. A project supplies a relative-path base, never an implicit read grant. */
public final class DesignFiles {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    private final List<Path> roots;
    private final Path project;

    public DesignFiles(List<String> roots, Path project) {
        this.roots = roots.stream().map(Path::of).map(p -> p.toAbsolutePath().normalize()).toList();
        this.project = project == null ? null : project.toAbsolutePath().normalize();
    }
    public List<String> roots() { return roots.stream().map(Path::toString).toList(); }
    public Path project() { return project; }
    public Path directory(Path directory) throws IOException {
        Path real = directory.toRealPath();
        if (Files.isDirectory(real)) for (Path root : roots) {
            if (Files.isDirectory(root) && real.startsWith(root.toRealPath())) return real;
        }
        throw new IOException("directory outside authorised roots");
    }

    public Path resolve(String requested) throws IOException {
        if (requested == null || requested.isBlank()) throw new IOException("a file path is required");
        Path path;
        try { path = Path.of(requested); }
        catch (InvalidPathException e) { throw new IOException("invalid file path", e); }
        List<Path> candidates = new ArrayList<>();
        if (path.isAbsolute()) candidates.add(path);
        else {
            if (project != null) candidates.add(project.resolve(path));
            for (Path root : roots) candidates.add(root.resolve(path));
        }
        Set<Path> matches = new LinkedHashSet<>();
        Set<Path> readableCandidates = new LinkedHashSet<>();
        for (Path candidate : candidates) {
            if (!Files.isRegularFile(candidate)) continue;
            Path real = candidate.toRealPath();
            readableCandidates.add(real);
            for (Path root : roots) {
                if (Files.isDirectory(root) && real.startsWith(root.toRealPath())) matches.add(real);
            }
        }
        if (matches.size() > 1) throw new IOException("ambiguous file under authorised roots: " + matches);
        if (matches.isEmpty()) {
            if (readableCandidates.size() == 1) {
                Path found = readableCandidates.iterator().next();
                Path parent = found.getParent();
                String call = telamin.fluxtion.audit.analyser.analyser.llm.Json.write(Map.of("add", List.of(parent.toString())));
                throw new IOException("file outside authorised roots: " + requested + projectHint(found)
                        + "; to authorise its parent only, call source_root " + call + ", then retry open {design}. No root was added.");
            }
            throw new IOException("file unavailable or outside authorised roots: " + requested
                    + (path.isAbsolute() ? projectHint(path) : relativeHint()));
        }
        return matches.iterator().next();
    }

    /** "; it is inside the project D — open {project: D} …", when the file belongs to a project that is not open. */
    private String projectHint(Path file) {
        return telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.enclosingProject(file)
                .filter(dir -> project == null || !sameDirectory(dir, project))
                .map(dir -> "; it is inside the project " + dir + " — open {project: "
                        + telamin.fluxtion.audit.analyser.analyser.llm.Json.write(dir.toString())
                        + "} applies that project's own source roots, then retry")
                .orElse("");
    }

    /**
     * The same directory by filesystem identity: a project opened through an alias (a symlink) is lexically unlike the
     * canonical directory found above the file (PR #35 review). Metadata only; it grants nothing. When identity cannot
     * be read, the lexical comparison decides, which at worst suggests opening the open project again.
     */
    private static boolean sameDirectory(Path a, Path b) {
        try { return Files.isSameFile(a, b); }
        catch (IOException | SecurityException e) { return a.equals(b); }
    }

    /** A relative path resolves against the open project and the roots; say which, since none may be set. */
    private String relativeHint() {
        String against = project == null && roots.isEmpty() ? "no project is open and no source roots are set"
                : "it was looked up under " + (project == null ? "" : "the project " + project + (roots.isEmpty() ? "" : " and "))
                  + (roots.isEmpty() ? "" : "the source roots " + roots);
        return "; a relative path resolves against the open project and the source roots, and " + against
                + ". Pass an absolute path, or open the project first with open {project: <dir>}";
    }

    public String read(Path path) throws IOException {
        // Recheck on EVERY read, including Follow, so a replaced symlink cannot preserve an old grant.
        Path permitted = resolve(path.toString());
        try (var input = Files.newInputStream(permitted, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new IOException("file exceeds the 2 MiB source-view limit");
            return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        }
    }

    public Path fqn(String name) throws IOException {
        if (name == null || !name.matches("[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*(\\.[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*)*"))
            throw new IOException("a Java class name is required");
        String candidate = name.replace('$', '.');
        while (true) {
            try { return resolve(candidate.replace('.', '/') + ".java"); }
            catch (IOException e) {
                if (e.getMessage().startsWith("ambiguous")) throw e;
                int dot = candidate.lastIndexOf('.');
                if (dot < 0) throw new IOException("class not under an authorised root: " + name);
                candidate = candidate.substring(0, dot);
            }
        }
    }

    public Path reportLocation(String root, String file) throws IOException {
        if (file == null || Path.of(file).isAbsolute()) throw new IOException("report location must be relative to sourceRoot");
        Path relative = Path.of(file).normalize();
        if (relative.startsWith("..")) throw new IOException("report location escapes sourceRoot");
        // Do not guess a foreign build-machine prefix. An absolute sourceRoot must itself be authorised.
        return resolve(root == null || root.isBlank() ? relative.toString() : Path.of(root).resolve(relative).toString());
    }

    public List<String> discoverDiagnostics() {
        if (project == null) return List.of();
        List<String> found = new ArrayList<>();
        for (String name : List.of("target/fluxtion-validation.json", "target/fluxtion-reconciliation.json", "target/classes/fluxtion-diagnostics.json")) {
            try { found.add(resolve(project.resolve(name).toString()).toString()); }
            catch (IOException ignored) { }
        }
        return List.copyOf(found);
    }

    public static String sha256(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
