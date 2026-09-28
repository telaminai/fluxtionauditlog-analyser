package telamin.fluxtion.audit.analyser.bundle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Evidence bundle v1, B2 (spec-evidence-bundle-packaging.md r2 §3.3, §4; EP-A2…A5). The format is the one thing a
 * recipient must be able to rely on, so its tests use a PINNED fixture: a known DEMO folder, packed with a fixed
 * clock and version, has a known identity. Every refusal names the member. Headless: no display, no session.
 */
public class EvidenceBundleTest {

    static final Instant AT = Instant.parse("2026-09-28T12:00:00Z");
    static final String VERSION = "1.27.0-test";

    /** The pinned identity of {@link #demoFolder}, packed at {@link #AT} by {@link #VERSION}. */
    static final String PINNED = "sha256:43a34431103b814f0ba9cf138a9fdf5f5a45a12d7555e39495c55c55d952467f";

    static Path demoFolder(Path tmp) throws IOException {
        Path f = Files.createDirectories(tmp.resolve("demo-bundle"));
        write(f, "log/demo-quote-audit.yaml", "eventLogRecord:\n  eventToString: DEMO quote 1\n");
        write(f, "graph/demo-quote-processor.graphml", "<graphml><!-- DEMO --></graphml>\n");
        write(f, "profile/project.fluxtion-settings", "walk.count=1\nwalk.0.name=DEMO_tour\n");
        return f;
    }

    private static void write(Path root, String rel, String text) throws IOException {
        Path p = root.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, text, StandardCharsets.UTF_8);
    }

    /** Every entry of a bundle, in order, as bytes — to build tampered copies of a good one. */
    public static Map<String, byte[]> entries(Path bundle) throws IOException {
        Map<String, byte[]> out = new LinkedHashMap<>();
        try (ZipInputStream z = new ZipInputStream(Files.newInputStream(bundle))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) out.put(e.getName(), z.readAllBytes());
        }
        return out;
    }

    public static Path zip(Path out, Map<String, byte[]> entries) throws IOException {
        try (OutputStream os = Files.newOutputStream(out); ZipOutputStream z = new ZipOutputStream(os)) {
            for (var e : entries.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue());
                z.closeEntry();
            }
        }
        return out;
    }

    private static Path good(Path tmp) throws IOException {
        Path out = tmp.resolve("good.fexp");
        EvidenceBundle.pack(demoFolder(tmp), out, AT, VERSION);
        return out;
    }

    private static void refused(EvidenceBundle.Verification v, String naming) {
        assertFalse(v.ok(), "expected a refusal naming '" + naming + "', but it verified");
        assertTrue(v.refusal().contains(naming), "the refusal must name '" + naming + "': " + v.refusal());
    }

    @Test
    @DisplayName("EP-A3: the pinned DEMO folder packs to its pinned identity, and packing again gives the same")
    void theIdentityIsPinnedAndDeterministic(@TempDir Path tmp) throws Exception {
        Path folder = demoFolder(tmp);
        String a = EvidenceBundle.pack(folder, tmp.resolve("a.fexp"), AT, VERSION);
        String b = EvidenceBundle.pack(folder, tmp.resolve("b.fexp"), AT, VERSION);
        assertEquals(a, b, "identical content and clock must pack to an identical identity");
        assertEquals(PINNED, a, "the format changed: a recipient's recorded identity would no longer match");
        assertEquals(a, EvidenceBundle.verify(tmp.resolve("a.fexp")).identity(), "verify states the same identity");
    }

    @Test
    @DisplayName("EP-A3: the identity is the manifest's EXACT bytes — re-serialising it changes the identity")
    void theIdentityIsTheExactManifestBytes(@TempDir Path tmp) throws Exception {
        Path bundle = good(tmp);
        var e = entries(bundle);
        String manifest = new String(e.get(EvidenceBundle.MANIFEST), StandardCharsets.UTF_8);
        e.put(EvidenceBundle.MANIFEST, (manifest.strip() + "\n\n").getBytes(StandardCharsets.UTF_8));   // same JSON, other bytes
        var v = EvidenceBundle.verify(zip(tmp.resolve("reserialised.fexp"), e));
        assertTrue(v.ok(), "the members still match: " + v.refusal());
        assertNotEquals(EvidenceBundle.verify(bundle).identity(), v.identity(),
                "a re-serialised manifest is a different bundle, even with equal JSON (D-2)");
    }

    @Test
    @DisplayName("EP-A2: a packed bundle verifies, and lists exactly the folder's files with their sha256 and size")
    void aPackedBundleVerifies(@TempDir Path tmp) throws Exception {
        var v = EvidenceBundle.verify(good(tmp));
        assertTrue(v.ok(), v.refusal());
        assertEquals(java.util.List.of("graph/demo-quote-processor.graphml", "log/demo-quote-audit.yaml",
                "profile/project.fluxtion-settings"), v.members().stream().map(EvidenceBundle.Member::path).toList());
        assertEquals(Files.size(tmp.resolve("demo-bundle/log/demo-quote-audit.yaml")), v.members().get(1).bytes());
    }

    @Test
    @DisplayName("EP-A4: a changed, missing or unlisted member is refused, naming it")
    void membersThatDoNotMatchAreRefused(@TempDir Path tmp) throws Exception {
        Path bundle = good(tmp);

        var changed = entries(bundle);
        changed.put("log/demo-quote-audit.yaml", "eventLogRecord:\n  eventToString: DEMO quote 2\n".getBytes(StandardCharsets.UTF_8));
        refused(EvidenceBundle.verify(zip(tmp.resolve("changed.fexp"), changed)), "log/demo-quote-audit.yaml");

        var sameSize = entries(bundle);                       // same length, different bytes: caught by sha256, not size
        byte[] b = sameSize.get("log/demo-quote-audit.yaml").clone();
        b[b.length - 2] = (byte) (b[b.length - 2] == '1' ? '9' : '1');
        sameSize.put("log/demo-quote-audit.yaml", b);
        refused(EvidenceBundle.verify(zip(tmp.resolve("samesize.fexp"), sameSize)), "sha256 differs");

        var missing = entries(bundle);
        missing.remove("graph/demo-quote-processor.graphml");
        refused(EvidenceBundle.verify(zip(tmp.resolve("missing.fexp"), missing)), "missing member: graph/demo-quote-processor.graphml");

        var unlisted = entries(bundle);
        unlisted.put("extra/DEMO-note.txt", "not in the manifest".getBytes(StandardCharsets.UTF_8));
        refused(EvidenceBundle.verify(zip(tmp.resolve("unlisted.fexp"), unlisted)), "unlisted member: extra/DEMO-note.txt");
    }

    @Test
    @DisplayName("EP-A4: an escaping member path is refused — .., absolute, backslash, empty segment")
    void escapingPathsAreRefused(@TempDir Path tmp) throws Exception {
        Path bundle = good(tmp);
        for (String evil : java.util.List.of("../outside.txt", "/tmp/absolute.txt", "log\\windows.txt", "log//double.txt")) {
            var e = entries(bundle);
            e.put(evil, "x".getBytes(StandardCharsets.UTF_8));
            refused(EvidenceBundle.verify(zip(tmp.resolve("escape.fexp"), e)), "path escape: " + evil);
            Files.delete(tmp.resolve("escape.fexp"));
        }
    }

    @Test
    @DisplayName("EP-A4: no manifest, an unreadable manifest, or another format is refused")
    void aBadManifestIsRefused(@TempDir Path tmp) throws Exception {
        Path bundle = good(tmp);
        var none = entries(bundle);
        none.remove(EvidenceBundle.MANIFEST);
        refused(EvidenceBundle.verify(zip(tmp.resolve("none.fexp"), none)), "no manifest.json");

        var garbled = entries(bundle);
        garbled.put(EvidenceBundle.MANIFEST, "{ not json".getBytes(StandardCharsets.UTF_8));
        refused(EvidenceBundle.verify(zip(tmp.resolve("garbled.fexp"), garbled)), "manifest.json cannot be read");

        var future = entries(bundle);
        future.put(EvidenceBundle.MANIFEST, new String(future.get(EvidenceBundle.MANIFEST), StandardCharsets.UTF_8)
                .replace("\"format\":1", "\"format\":2").getBytes(StandardCharsets.UTF_8));
        refused(EvidenceBundle.verify(zip(tmp.resolve("future.fexp"), future)), "unsupported format 2");
    }

    @Test
    @DisplayName("EP-A4/A5: unpack extracts nothing on refusal; on success a fresh copy, byte-equal; the bundle is untouched")
    void unpackIsVerifiedFreshAndLeavesTheBundleAlone(@TempDir Path tmp) throws Exception {
        Path bundle = good(tmp);
        byte[] before = Files.readAllBytes(bundle);
        Path parent = tmp.resolve("copies");

        var changed = entries(bundle);
        changed.put("log/demo-quote-audit.yaml", "tampered".getBytes(StandardCharsets.UTF_8));
        var refusedUnpack = EvidenceBundle.unpack(zip(tmp.resolve("bad.fexp"), changed), parent);
        assertFalse(refusedUnpack.verification().ok());
        assertNull(refusedUnpack.workingCopy(), "no working copy for a refused bundle");
        assertFalse(Files.exists(parent), "and nothing was written, not even the parent: verification finishes first");

        var one = EvidenceBundle.unpack(bundle, parent);
        var two = EvidenceBundle.unpack(bundle, parent);
        assertTrue(one.verification().ok(), one.verification().refusal());
        assertNotEquals(one.workingCopy(), two.workingCopy(), "each unpack is a fresh, disposable working copy");
        assertArrayEquals(Files.readAllBytes(tmp.resolve("demo-bundle/log/demo-quote-audit.yaml")),
                Files.readAllBytes(one.workingCopy().resolve("log/demo-quote-audit.yaml")), "the copy is byte-equal");
        assertArrayEquals(before, Files.readAllBytes(bundle), "EP-A5: the received bundle is only ever read");
    }

    @Test
    @DisplayName("pack refuses what it cannot represent: an existing output, a manifest.json already present, an empty folder")
    void packRefusals(@TempDir Path tmp) throws Exception {
        Path folder = demoFolder(tmp);
        Path out = tmp.resolve("x.fexp");
        EvidenceBundle.pack(folder, out, AT, VERSION);
        var overwrite = assertThrows(IOException.class, () -> EvidenceBundle.pack(folder, out, AT, VERSION));
        assertTrue(overwrite.getMessage().contains("will not overwrite"), overwrite.getMessage());
        Files.writeString(folder.resolve(EvidenceBundle.MANIFEST), "{}");
        var manifest = assertThrows(IOException.class, () -> EvidenceBundle.pack(folder, tmp.resolve("y.fexp"), AT, VERSION));
        assertTrue(manifest.getMessage().contains("already holds a manifest.json"), manifest.getMessage());
        Path empty = Files.createDirectories(tmp.resolve("empty"));
        var nothing = assertThrows(IOException.class, () -> EvidenceBundle.pack(empty, tmp.resolve("z.fexp"), AT, VERSION));
        assertTrue(nothing.getMessage().contains("nothing to pack"), nothing.getMessage());
    }

    @Test
    @DisplayName("EP-A4: a duplicate of a LISTED member is refused — built by renaming bytes, as ZipOutputStream will not write it")
    void aDuplicateEntryIsRefused(@TempDir Path tmp) throws Exception {
        // review F1 fix: an UNLISTED entry is now refused before it is read, so the duplicate that matters is a second
        // copy of a member the manifest lists, the one that could otherwise pass for it
        var e = entries(good(tmp));
        e.put("log/demo-quote-audit.yamZ", e.get("log/demo-quote-audit.yaml"));
        Path z = zip(tmp.resolve("dup.fexp"), e);
        byte[] raw = Files.readAllBytes(z);
        byte[] from = "log/demo-quote-audit.yamZ".getBytes(StandardCharsets.US_ASCII), to = "log/demo-quote-audit.yaml".getBytes(StandardCharsets.US_ASCII);
        int renamed = 0;
        for (int i = 0; i + from.length <= raw.length; i++) {
            if (java.util.Arrays.equals(raw, i, i + from.length, from, 0, from.length)) {
                System.arraycopy(to, 0, raw, i, to.length);
                renamed++;
            }
        }
        assertEquals(2, renamed, "the local header and the central directory both carry the name");
        Files.write(z, raw);
        refused(EvidenceBundle.verify(z), "duplicate member: log/demo-quote-audit.yaml");
    }

    @Test
    @DisplayName("F1: the manifest must be the FIRST entry — it is what bounds every member after it")
    void aMemberBeforeTheManifestIsRefused(@TempDir Path tmp) throws Exception {
        var e = entries(good(tmp));
        var reordered = new LinkedHashMap<String, byte[]>();
        reordered.put("log/demo-quote-audit.yaml", e.remove("log/demo-quote-audit.yaml"));
        reordered.putAll(e);
        refused(EvidenceBundle.verify(zip(tmp.resolve("late.fexp"), reordered)), "no manifest.json as the first entry");
    }

    @Test
    @DisplayName("F1: a manifest over its 4 MiB bound is refused by name, read no further than the bound")
    void anOversizedManifestIsRefused(@TempDir Path tmp) throws Exception {
        var e = entries(good(tmp));
        byte[] huge = new byte[EvidenceBundle.MANIFEST_MAX_BYTES + 1];
        java.util.Arrays.fill(huge, (byte) ' ');
        e.put(EvidenceBundle.MANIFEST, huge);
        refused(EvidenceBundle.verify(zip(tmp.resolve("huge.fexp"), e)), "manifest.json is larger than 4 MiB");
    }
}
