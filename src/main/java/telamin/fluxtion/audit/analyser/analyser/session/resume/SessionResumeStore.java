package telamin.fluxtion.audit.analyser.analyser.session.resume;

import telamin.fluxtion.audit.analyser.analyser.llm.Json;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;

/** User-local recovery files. All methods that touch files run outside the EDT/session dispatch. */
public final class SessionResumeStore {
    public static final String NO_PROJECT = "@no-project";
    private final Path directory;
    public SessionResumeStore(Path directory) { this.directory = directory; }

    public record Input(String role, String path) {
        public Input {
            if (!Set.of("log", "topology", "design", "diagnostics").contains(role))
                throw new IllegalArgumentException("unknown recovery input role");
            if (path == null || path.isBlank()) throw new IllegalArgumentException("empty recovery path");
        }
    }
    public record Identity(String role, String path, String sha256, String problem) { }
    /**
     * {@code profileIdentity} names the profile FILE that captured the snapshot, not just its path: the key is
     * a real path, and a project deleted and recreated at that path (a re-extracted download, say) would
     * otherwise inherit the old project's offer (edit-loop spec §E). Null for the no-project bucket and for
     * snapshots written before this field existed — neither ever counts as the same profile.
     */
    public record Snapshot(String key, String capturedAt, List<Identity> inputs, Map<String,Object> view,
                           String profileIdentity) {
        public Snapshot { inputs = List.copyOf(inputs); view = Collections.unmodifiableMap(new LinkedHashMap<>(view)); }
        public Snapshot(String key, String capturedAt, List<Identity> inputs, Map<String,Object> view) {
            this(key, capturedAt, inputs, view, null);
        }
    }
    public record Check(Identity input, String status) {
        public boolean unchanged() { return "unchanged".equals(status); }
    }

    /** Real paths coalesce symlink spellings; a missing active profile never falls into the own bucket. */
    public static String key(Path profile) throws IOException {
        return profile == null ? NO_PROJECT : profile.toRealPath().toString();
    }

    public Snapshot capture(String key, List<Input> inputs, Map<String,Object> view) {
        return capture(key, null, inputs, view);
    }

    public Snapshot capture(String key, String profileIdentity, List<Input> inputs, Map<String,Object> view) {
        List<Identity> identities = inputs.stream().map(i -> identity(i.role(), i.path())).toList();
        return new Snapshot(key, java.time.Instant.now().toString(), identities, view, profileIdentity);
    }

    /**
     * Which profile FILE this is, beyond its path. The analyser rewrites a profile in place, so the file keeps
     * its identity across ordinary saves; deleting and recreating the project, or a tool that replaces the
     * file, gives it a new one. A copy is a new file too. Parts are taken only where the platform reports
     * them faithfully: the file key (device and inode) where one exists — not on Windows — and the creation
     * time on macOS and Windows, where it is a real birth time rather than a stand-in for modification time.
     * On Linux the identity is the inode alone, so a recreated file that happens to reuse the inode number
     * is not told apart; that limit is accepted and documented rather than hidden.
     */
    public static String profileIdentity(Path profile) throws IOException {
        Path real = profile.toRealPath();
        BasicFileAttributes attributes = Files.readAttributes(real, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        boolean birthTime = os.contains("mac") || os.contains("win");
        List<String> parts = new ArrayList<>();
        if (attributes.fileKey() != null) parts.add("file=" + attributes.fileKey());
        if (birthTime) parts.add("created=" + attributes.creationTime().toInstant());
        if (parts.isEmpty()) throw new IOException("profile identity is unavailable on this file system");
        return String.join(";", parts);
    }

    public List<Check> check(Snapshot snapshot) {
        return snapshot.inputs().stream().map(old -> {
            if (old.sha256() == null) return new Check(old, "unverified at capture: " + old.problem());
            Identity current = identity(old.role(), old.path());
            if (current.sha256() == null) return new Check(old, current.problem());
            return new Check(old, old.sha256().equals(current.sha256()) ? "unchanged" : "content changed");
        }).toList();
    }

    public static Identity identity(String role, String file) {
        try {
            Path path = Path.of(file).toRealPath();
            BasicFileAttributes before = Files.readAttributes(path, BasicFileAttributes.class);
            if (!before.isRegularFile()) return new Identity(role, file, null, "not a regular file");
            MessageDigest digest = digest();
            try (InputStream in = Files.newInputStream(path)) {
                byte[] buffer = new byte[64 * 1024];
                for (int n; (n = in.read(buffer)) != -1;) digest.update(buffer, 0, n);
            }
            BasicFileAttributes after = Files.readAttributes(path, BasicFileAttributes.class);
            if (before.size() != after.size() || !before.lastModifiedTime().equals(after.lastModifiedTime())
                    || !Objects.equals(before.fileKey(), after.fileKey()))
                return new Identity(role, path.toString(), null, "changed during identity capture");
            return new Identity(role, path.toString(), HexFormat.of().formatHex(digest.digest()), null);
        } catch (IOException | RuntimeException e) {
            return new Identity(role, file, null, "unavailable: " + e.getMessage());
        }
    }

    /** A before/after read agrees only when both independent content hashes agree. */
    public static List<Identity> matchingRead(List<Identity> before, List<Identity> after) {
        if (before.size() != after.size()) return List.of();
        List<Identity> out = new ArrayList<>();
        for (int i = 0; i < before.size(); i++) {
            Identity a = before.get(i), b = after.get(i);
            if (!a.role().equals(b.role()) || !a.path().equals(b.path()) || a.sha256() == null
                    || !a.sha256().equals(b.sha256())) return List.of();
            out.add(b);
        }
        return List.copyOf(out);
    }

    public void save(Snapshot snapshot) throws IOException {
        Map<String,Object> root = new LinkedHashMap<>();
        root.put("version", 1);
        root.put("key", snapshot.key());
        root.put("capturedAt", snapshot.capturedAt());
        if (snapshot.profileIdentity() != null) root.put("profileIdentity", snapshot.profileIdentity());
        root.put("inputs", snapshot.inputs().stream().map(i -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("role", i.role()); m.put("path", i.path()); m.put("sha256", i.sha256()); m.put("problem", i.problem());
            return m;
        }).toList());
        root.put("view", snapshot.view());
        Files.createDirectories(directory);
        Path temp = Files.createTempFile(directory, "resume-", ".tmp");
        try {
            Files.writeString(temp, Json.write(root));
            try { Files.move(temp, file(snapshot.key()), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, file(snapshot.key()), StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }

    public Optional<Snapshot> load(String key) throws IOException {
        Path file = file(key);
        if (!Files.exists(file)) return Optional.empty();
        if (Files.size(file) > 2_000_000) throw new IOException("recovery file exceeds size limit");
        try {
            Object parsed = Json.parse(Files.readString(file));
            if (!(parsed instanceof Map<?,?> root) || !(root.get("version") instanceof Number v) || v.doubleValue() != 1)
                throw new IllegalArgumentException("unsupported recovery version");
            if (!key.equals(root.get("key"))) throw new IllegalArgumentException("recovery belongs to another project");
            if (!(root.get("inputs") instanceof List<?> inputs) || inputs.size() > 1000)
                throw new IllegalArgumentException("invalid recovery inputs");
            List<Identity> entries = new ArrayList<>();
            for (Object o : inputs) {
                if (!(o instanceof Map<?,?> m)) throw new IllegalArgumentException("invalid recovery input");
                Input input = new Input((String)m.get("role"), (String)m.get("path"));
                String hash = (String)m.get("sha256");
                if (hash != null && !hash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("invalid recovery digest");
                entries.add(new Identity(input.role(), input.path(), hash, (String)m.get("problem")));
            }
            if (!(root.get("view") instanceof Map<?,?> rawView)) throw new IllegalArgumentException("invalid recovery view");
            Map<String,Object> view = new LinkedHashMap<>();
            rawView.forEach((k,value) -> view.put((String)k,value));
            String at = (String)root.get("capturedAt");
            java.time.Instant.parse(at);
            Object profileIdentity = root.get("profileIdentity");
            if (profileIdentity != null && !(profileIdentity instanceof String))
                throw new IllegalArgumentException("invalid recovery profile identity");
            return Optional.of(new Snapshot(key, at, entries, view, (String) profileIdentity));
        } catch (RuntimeException e) { throw new IOException("invalid recovery file: " + e.getMessage(), e); }
    }

    private Path file(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("recovery key is required");
        String id = HexFormat.of().formatHex(digest().digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return directory.resolve(id + ".json");
    }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
