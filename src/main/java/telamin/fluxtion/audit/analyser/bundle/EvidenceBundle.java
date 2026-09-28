package telamin.fluxtion.audit.analyser.bundle;

import telamin.fluxtion.audit.analyser.analyser.llm.Json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Evidence bundle v1, the format (spec-evidence-bundle-packaging.md r2 §3.3, §4). ONE implementation of the thing a
 * recipient must be able to trust: the manifest, its identity, verification, and a safe unpack. A skill chooses what
 * goes into the folder; everything about the file itself is decided here, headless, with no UI.
 *
 * <p><b>Identity (D-2):</b> {@code sha256:} of the manifest's exact bytes, never stored inside it. <b>Unsigned (D-3):</b>
 * verification detects a changed, missing or unlisted member; it does not say who sent the bundle, and nothing here
 * ever says it does.
 */
public final class EvidenceBundle {

    private EvidenceBundle() {
    }

    public static final String MANIFEST = "manifest.json";
    public static final int FORMAT = 1;

    /** Fixed text, stated by every surface that shows a verified bundle (spec §4.3, EP-A10). */
    public static final List<String> LIMITS = List.of(
            "unsigned: verification detects a changed member; it does not authenticate the sender",
            "no replay: this bundle shows an investigation; it does not reproduce or fix it");

    /** One member as the manifest lists it. */
    public record Member(String path, String sha256, long bytes) { }

    /** A verification: the identity, and either every member verified or the first refusal naming the member. */
    public record Verification(String identity, List<Member> members, String refusal) {
        public boolean ok() {
            return refusal == null;
        }
    }

    // ---- pack --------------------------------------------------------------------------------------------------

    /**
     * Write {@code out} from every regular file under {@code folder}: a manifest listing each member's path, sha256 and
     * size, then the members. The manifest's bytes are deterministic for a given folder and {@code createdAt}: members
     * are sorted by path and the key order is fixed, so identical content packs to an identical identity.
     *
     * @return the new bundle's identity
     */
    public static String pack(Path folder, Path out, Instant createdAt, String analyserVersion) throws IOException {
        if (!Files.isDirectory(folder)) throw new IOException("not a folder: " + folder);
        if (Files.exists(out)) throw new IOException("will not overwrite " + out);
        TreeMap<String, Path> files = new TreeMap<>();
        try (var walk = Files.walk(folder)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) continue;
                String rel = folder.relativize(p).toString().replace('\\', '/');
                if (Files.isSymbolicLink(p)) throw new IOException("a link cannot be a member: " + rel);
                if (!Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) throw new IOException("not a regular file: " + rel);
                String problem = pathProblem(rel);
                if (problem != null) throw new IOException(problem);
                if (rel.equals(MANIFEST)) throw new IOException("the folder already holds a " + MANIFEST + ": pack writes it");
                files.put(rel, p);
            }
        }
        if (files.isEmpty()) throw new IOException("nothing to pack in " + folder);
        List<Member> members = new ArrayList<>();
        for (var e : files.entrySet()) {
            byte[] bytes = Files.readAllBytes(e.getValue());
            members.add(new Member(e.getKey(), sha256(bytes), bytes.length));
        }
        byte[] manifest = manifestBytes(members, createdAt, analyserVersion);
        try (OutputStream os = Files.newOutputStream(out, java.nio.file.StandardOpenOption.CREATE_NEW);
             ZipOutputStream zip = new ZipOutputStream(os)) {
            put(zip, MANIFEST, manifest);
            for (var e : files.entrySet()) put(zip, e.getKey(), Files.readAllBytes(e.getValue()));
        }
        return identity(manifest);
    }

    static byte[] manifestBytes(List<Member> members, Instant createdAt, String analyserVersion) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("format", FORMAT);
        m.put("createdAt", createdAt.toString());
        m.put("analyser", analyserVersion == null ? "unknown" : analyserVersion);
        members.stream().filter(x -> x.path().startsWith("log/")).findFirst()
                .ifPresent(x -> m.put("log", Map.of("member", x.path())));
        members.stream().filter(x -> x.path().startsWith("graph/")).findFirst()
                .ifPresent(x -> m.put("graph", Map.of("member", x.path())));
        List<Object> list = new ArrayList<>();
        for (Member x : members) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("path", x.path());
            one.put("sha256", x.sha256());
            one.put("bytes", x.bytes());
            list.add(one);
        }
        m.put("members", list);
        m.put("limits", LIMITS);
        return (Json.write(m) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static void put(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    // ---- verify ------------------------------------------------------------------------------------------------

    /**
     * Verify {@code bundle} without extracting it. Refused, naming the member, for: no manifest or more than one; an
     * unreadable manifest; a member path that escapes (absolute, {@code ..}, backslash, empty segments); a duplicate
     * entry; an entry the manifest does not list; a listed member that is missing; a member whose bytes or size differ.
     */
    public static Verification verify(Path bundle) throws IOException {
        Read read = read(bundle);
        return read.verification();
    }

    private record Read(Verification verification, Map<String, byte[]> entries) { }

    private static Read read(Path bundle) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        byte[] manifest = null;
        try (InputStream in = Files.newInputStream(bundle); ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                String name = e.getName();
                if (e.isDirectory()) continue;
                String problem = pathProblem(name);
                if (problem != null) return refused(null, problem);
                byte[] bytes = readAll(zip);
                if (name.equals(MANIFEST)) {
                    if (manifest != null) return refused(null, "more than one " + MANIFEST);
                    manifest = bytes;
                    continue;
                }
                if (entries.put(name, bytes) != null) return refused(null, "duplicate member: " + name);
            }
        } catch (java.util.zip.ZipException ex) {
            return refused(null, "not a readable bundle: " + ex.getMessage());
        }
        if (manifest == null) return refused(null, "no " + MANIFEST + ": this is not an evidence bundle");
        String identity = identity(manifest);
        List<Member> listed;
        try {
            listed = members(manifest);
        } catch (RuntimeException ex) {
            return refused(identity, MANIFEST + " cannot be read: " + ex.getMessage());
        }
        Set<String> seen = new LinkedHashSet<>();
        for (Member m : listed) {
            if (!seen.add(m.path())) return refused(identity, "the manifest lists a member twice: " + m.path());
            String problem = pathProblem(m.path());
            if (problem != null) return refused(identity, problem);
            byte[] bytes = entries.get(m.path());
            if (bytes == null) return refused(identity, "missing member: " + m.path());
            if (bytes.length != m.bytes()) {
                return refused(identity, "changed member: " + m.path() + " (" + bytes.length + " bytes, manifest says " + m.bytes() + ")");
            }
            if (!sha256(bytes).equals(m.sha256())) return refused(identity, "changed member: " + m.path() + " (sha256 differs)");
        }
        for (String name : entries.keySet()) {
            if (!seen.contains(name)) return refused(identity, "unlisted member: " + name);
        }
        return new Read(new Verification(identity, List.copyOf(listed), null), entries);
    }

    private static Read refused(String identity, String why) {
        return new Read(new Verification(identity, List.of(), why), Map.of());
    }

    @SuppressWarnings("unchecked")
    private static List<Member> members(byte[] manifest) {
        Object parsed = Json.parse(new String(manifest, StandardCharsets.UTF_8));
        if (!(parsed instanceof Map<?, ?> m)) throw new IllegalArgumentException("not a JSON object");
        Object format = m.get("format");
        if (!(format instanceof Number n) || n.intValue() != FORMAT) {
            throw new IllegalArgumentException("unsupported format " + format + " (this reader reads format " + FORMAT + ")");
        }
        if (!(m.get("members") instanceof List<?> list)) throw new IllegalArgumentException("no members list");
        List<Member> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> x) || !(x.get("path") instanceof String path) || !(x.get("sha256") instanceof String sha)
                    || !(x.get("bytes") instanceof Number bytes)) {
                throw new IllegalArgumentException("a member needs path, sha256 and bytes");
            }
            out.add(new Member(path, sha, bytes.longValue()));
        }
        return out;
    }

    // ---- unpack ------------------------------------------------------------------------------------------------

    /**
     * Verify {@code bundle}, then extract it into a NEW directory under {@code parent}, named for its identity. Nothing
     * is written when verification refuses. The bundle file itself is only read.
     *
     * @return the verification, and the working copy's path when it succeeded (null otherwise)
     */
    public static Unpacked unpack(Path bundle, Path parent) throws IOException {
        Read read = read(bundle);
        if (!read.verification().ok()) return new Unpacked(read.verification(), null);
        Files.createDirectories(parent);
        String stem = read.verification().identity().substring("sha256:".length(), "sha256:".length() + 12);
        Path dir = Files.createTempDirectory(parent, "bundle-" + stem + "-");
        for (var e : read.entries().entrySet()) {
            Path target = dir.resolve(e.getKey()).normalize();
            if (!target.startsWith(dir)) throw new IOException("path escape at extraction: " + e.getKey());   // defensive
            Files.createDirectories(target.getParent());
            Files.write(target, e.getValue(), java.nio.file.StandardOpenOption.CREATE_NEW);
        }
        return new Unpacked(read.verification(), dir);
    }

    public record Unpacked(Verification verification, Path workingCopy) { }

    // ---- helpers -----------------------------------------------------------------------------------------------

    /** Why a member path is unsafe, or null. Relative, forward slashes only, no empty, "." or ".." segments. */
    static String pathProblem(String path) {
        if (path == null || path.isEmpty()) return "an empty member path";
        if (path.startsWith("/") || path.contains(":") || path.contains("\\")) return "path escape: " + path;
        for (String seg : path.split("/", -1)) {
            if (seg.isEmpty() || seg.equals(".") || seg.equals("..")) return "path escape: " + path;
        }
        return null;
    }

    public static String identity(byte[] manifestBytes) {
        return "sha256:" + sha256(manifestBytes);
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        in.transferTo(out);
        return out.toByteArray();
    }
}
