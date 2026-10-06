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
    /**
     * A bundle that carries a replay (spec-evidence-bundle-replay §4.2). Only then: a bundle without one stays format 1,
     * byte for byte, so a first-delivery reader still reads every bundle it could before.
     */
    public static final int FORMAT_REPLAY = 2;
    /** Where a replay member lives in a bundle. */
    public static final String REPLAY_DIR = "replay/";

    /** Fixed text, stated by every surface that shows a verified bundle (spec §4.3, EP-A10). */
    public static final List<String> LIMITS = List.of(
            "unsigned: verification detects a changed member; it does not authenticate the sender",
            "no replay: this bundle shows an investigation; it does not reproduce or fix it");

    /** The limits of a bundle that carries a replay: what a replay may claim, and no more (replay spec §1, §4.2). */
    public static final List<String> LIMITS_REPLAY = List.of(
            "unsigned: verification detects a changed member; it does not authenticate the sender",
            "replay: the recorded inputs reproduce this log only on a build whose graph matches, and only as far as the "
                    + "processor reads nothing the records do not carry");

    /** The limits a verified bundle states: its own, by whether it carries a replay. */
    public static List<String> limits(Verification v) {
        return v.replay() == null ? LIMITS : LIMITS_REPLAY;
    }

    /** One member as the manifest lists it. */
    public record Member(String path, String sha256, long bytes) { }

    /** A verification: the identity, and either every member verified or the first refusal naming the member. */
    public record Verification(String identity, List<Member> members, String refusal, Map<String, Object> excerpt,
                               Map<String, Object> replay, String processor) {
        public Verification(String identity, List<Member> members, String refusal, Map<String, Object> excerpt,
                            Map<String, Object> replay) {
            this(identity, members, refusal, excerpt, replay, null);
        }

        public Verification(String identity, List<Member> members, String refusal) {
            this(identity, members, refusal, null, null);
        }

        public Verification(String identity, List<Member> members, String refusal, Map<String, Object> excerpt) {
            this(identity, members, refusal, excerpt, null);
        }

        public boolean ok() {
            return refusal == null;
        }
    }

    // ---- pack --------------------------------------------------------------------------------------------------

    /**
     * Write {@code out} from every regular file under {@code folder}: a manifest listing each member's path, sha256 and
     * size, then the members. The manifest's bytes are deterministic for a given folder and {@code createdAt}: members
     * are sorted by path and the key order is fixed, so identical content packs to an identical identity. The manifest
     * is the FIRST entry, which is what lets a reader bound every member by its declared size (review F1). Members are
     * streamed, never held: memory does not grow with the log.
     *
     * @return the new bundle's identity
     */
    public static String pack(Path folder, Path out, Instant createdAt, String analyserVersion) throws IOException {
        return pack(folder, out, createdAt, analyserVersion, null);
    }

    /**
     * As above, stating in the manifest that the log member is an EXCERPT, and which: {@code excerpt} is the cut
     * (first and last record of the source, the source's record count, and the time window asked for). A recipient
     * must never read a slice as the whole log. Null for a whole log, which leaves the manifest's bytes as they were.
     */
    public static String pack(Path folder, Path out, Instant createdAt, String analyserVersion,
                              Map<String, Object> excerpt) throws IOException {
        return pack(folder, out, createdAt, analyserVersion, excerpt, null);
    }

    /**
     * As above, stating that the bundle carries a REPLAY (format 2): {@code replay} holds what was established about it
     * ({@code records}, {@code serviceCalls}), and the folder must hold exactly one member under {@link #REPLAY_DIR}.
     * Null for no replay, which leaves the manifest format 1, byte for byte.
     */
    public static String pack(Path folder, Path out, Instant createdAt, String analyserVersion,
                              Map<String, Object> excerpt, Map<String, Object> replay) throws IOException {
        return pack(folder, out, createdAt, analyserVersion, excerpt, replay, null);
    }

    /** As above, naming the event processor the log came from (a class name; null or blank for none). */
    public static String pack(Path folder, Path out, Instant createdAt, String analyserVersion,
                              Map<String, Object> excerpt, Map<String, Object> replay, String processor)
            throws IOException {
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
            try (InputStream in = Files.newInputStream(e.getValue())) {
                Digest d = digest(in, Long.MAX_VALUE, null);
                members.add(new Member(e.getKey(), d.sha256(), d.bytes()));
            }
        }
        long replays = members.stream().filter(x -> x.path().startsWith(REPLAY_DIR)).count();
        if (replay != null && replays != 1) throw new IOException("a replay bundle holds one " + REPLAY_DIR + " member, not " + replays);
        if (replay == null && replays != 0) throw new IOException("a " + REPLAY_DIR + " member with no replay stated");
        byte[] manifest = manifestBytes(members, createdAt, analyserVersion, excerpt, replay, processor);
        try (OutputStream os = Files.newOutputStream(out, java.nio.file.StandardOpenOption.CREATE_NEW);
             ZipOutputStream zip = new ZipOutputStream(os)) {
            put(zip, MANIFEST, manifest);
            for (Member m : members) {
                zip.putNextEntry(new ZipEntry(m.path()));
                try (InputStream in = Files.newInputStream(files.get(m.path()))) {
                    Digest d = digest(in, Long.MAX_VALUE, zip);
                    if (!d.sha256().equals(m.sha256()) || d.bytes() != m.bytes()) {
                        throw new IOException("changed while packing: " + m.path());
                    }
                }
                zip.closeEntry();
            }
        } catch (IOException ex) {
            Files.deleteIfExists(out);
            throw ex;
        }
        return identity(manifest);
    }

    static byte[] manifestBytes(List<Member> members, Instant createdAt, String analyserVersion) {
        return manifestBytes(members, createdAt, analyserVersion, null);
    }

    static byte[] manifestBytes(List<Member> members, Instant createdAt, String analyserVersion, Map<String, Object> excerpt) {
        return manifestBytes(members, createdAt, analyserVersion, excerpt, null);
    }

    static byte[] manifestBytes(List<Member> members, Instant createdAt, String analyserVersion, Map<String, Object> excerpt,
                                Map<String, Object> replay) {
        return manifestBytes(members, createdAt, analyserVersion, excerpt, replay, null);
    }

    /**
     * As above, naming the event processor the log came from. A fully-qualified CLASS NAME, not a path: it
     * identifies nothing about the sender's machine, and without it a recipient opens the bundle with no
     * processor selected and no way to guess one — the graph names the nodes but never the processor that
     * dispatches them (found in use, 2026-09-30). Absent or blank leaves the manifest's bytes as they were.
     */
    static byte[] manifestBytes(List<Member> members, Instant createdAt, String analyserVersion, Map<String, Object> excerpt,
                                Map<String, Object> replay, String processor) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("format", replay == null ? FORMAT : FORMAT_REPLAY);
        m.put("createdAt", createdAt.toString());
        m.put("analyser", analyserVersion == null ? "unknown" : analyserVersion);
        members.stream().filter(x -> x.path().startsWith("log/")).findFirst()
                .ifPresent(x -> m.put("log", Map.of("member", x.path())));
        members.stream().filter(x -> x.path().startsWith("graph/")).findFirst()
                .ifPresent(x -> m.put("graph", Map.of("member", x.path())));
        if (processor != null && !processor.isBlank()) m.put("processor", processor);
        if (excerpt != null) m.put("excerpt", excerpt);
        if (replay != null) {
            Map<String, Object> r = new LinkedHashMap<>();
            members.stream().filter(x -> x.path().startsWith(REPLAY_DIR)).findFirst().ifPresent(x -> r.put("member", x.path()));
            r.putAll(replay);
            m.put("replay", r);
        }
        List<Object> list = new ArrayList<>();
        for (Member x : members) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("path", x.path());
            one.put("sha256", x.sha256());
            one.put("bytes", x.bytes());
            list.add(one);
        }
        m.put("members", list);
        m.put("limits", replay == null ? LIMITS : LIMITS_REPLAY);
        return (Json.write(m) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static void put(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    // ---- verify ------------------------------------------------------------------------------------------------

    /**
     * The one absolute bound (review F1): the manifest is the only entry read whole. A format-1 manifest lists a few
     * members in well under a kilobyte; 4 MiB is some twenty thousand. A member has no absolute cap: it is bounded by
     * the size its manifest declares, and read as a stream, so a legitimate whole log of any size verifies in constant
     * memory.
     */
    public static final int MANIFEST_MAX_BYTES = 4 << 20;

    /**
     * Verify {@code bundle} without extracting it, in bounded memory. Refused, naming the member, for: a first entry
     * that is not the manifest, or more than one; an unreadable or oversized manifest; a member path that escapes
     * (absolute, {@code ..}, backslash, empty segments); a duplicate entry; an entry the manifest does not list (refused
     * before its bytes are read); a member larger than declared (refused the moment it exceeds); a listed member that is
     * missing; a member whose bytes or size differ.
     */
    public static Verification verify(Path bundle) throws IOException {
        return check(bundle, null).verification();
    }

    /** One streaming pass. With {@code into}, each member is also written there, re-digested as it is written. */
    private record Pass(Verification verification, Map<String, Member> listed) { }

    private static Pass check(Path bundle, Path into) throws IOException {
        String identity = null;
        Map<String, Object> excerpt = null;
        Map<String, Object> replay = null;
        String processor = null;
        Map<String, Member> listed = null;
        Set<String> seen = new LinkedHashSet<>();
        try (InputStream in = Files.newInputStream(bundle); ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                String name = e.getName();
                if (e.isDirectory()) continue;
                String problem = pathProblem(name);
                if (problem != null) return refused(identity, problem);
                if (listed == null) {
                    // the manifest comes first, so every member after it is bounded by what it declares
                    if (!name.equals(MANIFEST)) {
                        return refused(null, "no " + MANIFEST + " as the first entry: this is not an evidence bundle");
                    }
                    byte[] manifest = readBounded(zip, MANIFEST_MAX_BYTES);
                    if (manifest == null) {
                        return refused(null, MANIFEST + " is larger than " + (MANIFEST_MAX_BYTES >> 20) + " MiB");
                    }
                    identity = identity(manifest);
                    List<Member> members;
                    try {
                        members = members(manifest);
                        excerpt = excerptOf(manifest);
                        replay = replayOf(manifest, members);
                        processor = processorOf(manifest);
                    } catch (RuntimeException ex) {
                        return refused(identity, MANIFEST + " cannot be read: " + ex.getMessage());
                    }
                    listed = new LinkedHashMap<>();
                    for (Member m : members) {
                        String bad = pathProblem(m.path());
                        if (bad != null) return refused(identity, bad);
                        if (m.path().equals(MANIFEST) || listed.put(m.path(), m) != null) {
                            return refused(identity, "the manifest lists a member twice: " + m.path());
                        }
                    }
                    continue;
                }
                if (name.equals(MANIFEST)) return refused(identity, "more than one " + MANIFEST);
                if (!seen.add(name)) return refused(identity, "duplicate member: " + name);
                Member m = listed.get(name);
                if (m == null) return refused(identity, "unlisted member: " + name);          // never read
                OutputStream sink = null;
                try {
                    if (into != null) {
                        Path target = into.resolve(name).normalize();
                        if (!target.startsWith(into)) throw new IOException("path escape at extraction: " + name);   // defensive
                        Files.createDirectories(target.getParent());
                        sink = Files.newOutputStream(target, java.nio.file.StandardOpenOption.CREATE_NEW);
                    }
                    Digest d = digest(zip, m.bytes(), sink);
                    if (d.bytes() > m.bytes()) {
                        return refused(identity, "changed member: " + name + " (larger than the manifest's " + m.bytes() + " bytes)");
                    }
                    if (d.bytes() != m.bytes()) {
                        return refused(identity, "changed member: " + name + " (" + d.bytes() + " bytes, manifest says " + m.bytes() + ")");
                    }
                    if (!d.sha256().equals(m.sha256())) return refused(identity, "changed member: " + name + " (sha256 differs)");
                } finally {
                    if (sink != null) sink.close();
                }
            }
        } catch (java.util.zip.ZipException | java.io.EOFException ex) {
            return refused(identity, "not a readable bundle: " + ex.getMessage());
        }
        if (listed == null) return refused(null, "no " + MANIFEST + ": this is not an evidence bundle");
        for (String path : listed.keySet()) {
            if (!seen.contains(path)) return refused(identity, "missing member: " + path);
        }
        return new Pass(new Verification(identity, List.copyOf(listed.values()), null, excerpt, replay, processor), listed);
    }

    private static Pass refused(String identity, String why) {
        return new Pass(new Verification(identity, List.of(), why), Map.of());
    }

    @SuppressWarnings("unchecked")
    private static List<Member> members(byte[] manifest) {
        Object parsed = Json.parse(new String(manifest, StandardCharsets.UTF_8));
        if (!(parsed instanceof Map<?, ?> m)) throw new IllegalArgumentException("not a JSON object");
        Object format = m.get("format");
        if (!(format instanceof Number n) || (n.intValue() != FORMAT && n.intValue() != FORMAT_REPLAY)) {
            throw new IllegalArgumentException("unsupported format " + format + " (this reader reads formats " + FORMAT
                    + " and " + FORMAT_REPLAY + ")");
        }
        if (!(m.get("members") instanceof List<?> list)) throw new IllegalArgumentException("no members list");
        List<Member> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> x) || !(x.get("path") instanceof String path) || !(x.get("sha256") instanceof String sha)
                    || !(x.get("bytes") instanceof Number bytes) || bytes.longValue() < 0) {
                throw new IllegalArgumentException("a member needs path, sha256 and a non-negative bytes");
            }
            out.add(new Member(path, sha, bytes.longValue()));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> excerptOf(byte[] manifest) {
        Object x = ((Map<String, Object>) Json.parse(new String(manifest, StandardCharsets.UTF_8))).get("excerpt");
        if (x == null) return null;
        if (!(x instanceof Map<?, ?> m)) throw new IllegalArgumentException("excerpt is not an object");
        return Map.copyOf((Map<String, Object>) m);
    }

    /** The event processor a manifest names, or null. A class name, so nothing to validate against members. */
    @SuppressWarnings("unchecked")
    static String processorOf(byte[] manifest) {
        Map<String, Object> m = (Map<String, Object>) Json.parse(new String(manifest, StandardCharsets.UTF_8));
        return m.get("processor") instanceof String s && !s.isBlank() ? s : null;
    }

    /**
     * A format-2 manifest's replay: it must name a member the manifest lists, under {@link #REPLAY_DIR}. A format-1
     * manifest must state none. So a replay can neither be claimed without its member nor carried without its claim.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> replayOf(byte[] manifest, List<Member> members) {
        Map<String, Object> m = (Map<String, Object>) Json.parse(new String(manifest, StandardCharsets.UTF_8));
        boolean format2 = ((Number) m.get("format")).intValue() == FORMAT_REPLAY;
        Object x = m.get("replay");
        if (!format2) {
            if (x != null) throw new IllegalArgumentException("a format " + FORMAT + " manifest states a replay");
            return null;
        }
        if (!(x instanceof Map<?, ?> r) || !(r.get("member") instanceof String member)) {
            throw new IllegalArgumentException("a format " + FORMAT_REPLAY + " manifest needs a replay with its member");
        }
        if (!member.startsWith(REPLAY_DIR) || members.stream().noneMatch(mm -> mm.path().equals(member))) {
            throw new IllegalArgumentException("the replay member " + member + " is not listed");
        }
        return Map.copyOf((Map<String, Object>) r);
    }

    // ---- unpack ------------------------------------------------------------------------------------------------

    /**
     * Verify {@code bundle}, then extract it into a NEW directory under {@code parent}, named for its identity. Two
     * passes (review F1): the first verifies in bounded memory and writes NOTHING; only when it succeeds does the
     * second stream each member to disk, digesting it again as it goes. If the file changed between the passes, the
     * second refuses and the working copy is deleted. The bundle file itself is only read.
     *
     * @return the verification, and the working copy's path when it succeeded (null otherwise)
     */
    /**
     * Where opening a bundle puts its working copy. One spelling, because four of them had drifted
     * apart and nothing could ask "is this a working copy?" of a path.
     */
    public static Path workingCopiesRoot() {
        return Path.of(System.getProperty("user.home"), ".fluxtion-analyser", "bundles");
    }

    /**
     * Whether a path lies inside a bundle's working copy — a throwaway unpack, not somewhere a person
     * keeps work. A profile in here must not be resurrected as the project on the next launch: the
     * profile alone is an empty shell, with no graph, no log and no source, because those arrive only
     * through opening the bundle. Found in use, 2026-09-30, by restarting into exactly that shell.
     */
    public static boolean isWorkingCopy(Path path) {
        if (path == null) return false;
        Path root = workingCopiesRoot().toAbsolutePath().normalize();
        return path.toAbsolutePath().normalize().startsWith(root);
    }

    public static Unpacked unpack(Path bundle, Path parent) throws IOException {
        try (Unpacked unpacked = unpackOwned(bundle, parent)) {
            return new Unpacked(unpacked.verification(), unpacked.workingCopy());
        }
    }

    /** The caller holds this lease throughout preparation and every use of the extracted files. */
    public static Unpacked unpackOwned(Path bundle, Path parent) throws IOException {
        Pass first = check(bundle, null);
        if (!first.verification().ok()) return new Unpacked(first.verification(), null);
        Files.createDirectories(parent);
        String stem = first.verification().identity().substring("sha256:".length(), "sha256:".length() + 12);
        // EVERY open gets its own pristine extraction (#85, corrected).
        //
        // A reuse-by-identity scheme was tried and withdrawn. Opening a bundle applies its profile as the
        // PROJECT, so the session writes into the copy as soon as any setting changes -- which means a
        // reused copy is no longer the bundle's content. The next open then verified a modified profile,
        // its digest no longer matched the plan, and OpenBundle dropped the bundle's provenance silently:
        // a genuine bundle read as an ordinary folder somebody had opened. Checking the members on reuse
        // only moved the problem -- the copy is dirty after almost every session, so reuse rarely applied,
        // and re-extracting over it discarded the previous session's work and raced the anchor restore.
        //
        // A fresh copy per open keeps the invariant the rest of the code relies on: a working copy IS the
        // bundle's content. Accumulation -- the thirty-two copies actually reported -- is solved by reaping
        // instead (reap, workingCopies, and the Private settings control), which is what #85 asked for.
        // Making the copy read-only so it could be shared is the better long-term answer and is its own
        // issue; it changes what "a bundle is the project" means and does not belong in a bug fix.
        Path dir = Files.createTempDirectory(parent, "bundle-" + stem + "-").toAbsolutePath().normalize();
        WorkingCopyOwnership.Lease lease = null;
        Pass second;
        try {
            lease = WorkingCopyOwnership.create(dir);
            second = check(bundle, dir);
        } catch (IOException | RuntimeException ex) {
            if (lease != null) lease.close();
            deleteTree(dir);
            throw ex;
        }
        if (!second.verification().ok() || !first.verification().identity().equals(second.verification().identity())) {
            lease.close();
            deleteTree(dir);
            String why = second.verification().ok() ? "a different manifest" : second.verification().refusal();
            return new Unpacked(new Verification(first.verification().identity(), List.of(),
                    "the bundle changed while it was being unpacked (" + why + "); nothing was kept"), null);
        }
        return new Unpacked(first.verification(), dir, lease);
    }

    public record Unpacked(Verification verification, Path workingCopy,
                           WorkingCopyOwnership.Lease lease) implements AutoCloseable {
        public Unpacked(Verification verification, Path workingCopy) { this(verification, workingCopy, null); }
        @Override public void close() { if (lease != null) lease.close(); }
    }

    /** Fresh extraction, held through pending open; only provably unused managed copies are reaped. */
    public static Unpacked unpackAndReap(Path bundle, Path parent) throws IOException {
        Unpacked unpacked = unpackOwned(bundle, parent);
        if (unpacked.workingCopy() != null) {
            reap(workingCopies(), unpacked.workingCopy());
        }
        return unpacked;
    }

    /**
     * Working copies this machine holds, newest first — what {@link #reap} would consider and what a
     * person is shown before any of it is removed.
     */
    public static List<Path> workingCopies() {
        return workingCopies(workingCopiesRoot());
    }

    private static List<Path> workingCopies(Path root) {
        if (!Files.isDirectory(root)) return List.of();
        try (var list = Files.list(root)) {
            return list.filter(Files::isDirectory)
                    .filter(d -> d.getFileName().toString().startsWith("bundle-"))
                    .sorted(java.util.Comparator.comparing(EvidenceBundle::modifiedAt).reversed())
                    .toList();
        } catch (IOException | java.io.UncheckedIOException unreadable) {
            return List.of();
        }
    }

    private static java.nio.file.attribute.FileTime modifiedAt(Path dir) {
        try {
            return Files.getLastModifiedTime(dir);
        } catch (IOException unreadable) {
            return java.nio.file.attribute.FileTime.fromMillis(0);
        }
    }

    /**
     * Remove only direct managed children with a same-host marker and a free exclusive lock.
     * Live readers (including pending opens), unknown ownership and legacy unmarked copies are kept.
     * The optional keep is an additional exclusion, never evidence that the other copies are unused.
     */
    public static int reap(List<Path> copies, Path keep) {
        int removed = 0;
        Path spared = keep == null ? null : keep.toAbsolutePath().normalize();
        for (Path copy : copies) {
            Path at = copy.toAbsolutePath().normalize();
            if (at.equals(spared)) continue;
            if (WorkingCopyOwnership.reap(at)) removed++;
        }
        return removed;
    }

    /** Background cleanup uses the window's captured root, not a later change of user.home. */
    public static int reapUnused(Path root) {
        int removed = 0;
        for (Path copy : workingCopies(root)) if (WorkingCopyOwnership.reap(root, copy)) removed++;
        return removed;
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }

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

    /** The whole of {@code in} if it is at most {@code max} bytes, else null. Reads at most {@code max + 1}. */
    private static byte[] readBounded(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf, 0, (int) Math.min(buf.length, (long) max + 1 - out.size()))) > 0) {
            out.write(buf, 0, n);
            if (out.size() > max) return null;
        }
        return out.toByteArray();
    }

    /** What a streamed read established: the sha256 of what was read, and how many bytes. */
    private record Digest(String sha256, long bytes) { }

    /**
     * Digest {@code in} through a fixed buffer, copying to {@code sink} when given. Stops as soon as more than
     * {@code limit} bytes have been read, reporting {@code limit + 1}-or-more: the caller refuses at that moment, and
     * nothing beyond the declared size is ever read or written.
     */
    private static Digest digest(InputStream in, long limit, OutputStream sink) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        byte[] buf = new byte[64 * 1024];
        long total = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > limit) return new Digest("", total);
            md.update(buf, 0, n);
            if (sink != null) sink.write(buf, 0, n);
        }
        return new Digest(HexFormat.of().formatHex(md.digest()), total);
    }
}
