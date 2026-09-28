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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Review F1 (evidence bundle v1): {@code --verify} read every member whole into the heap before any check, so a 2 MiB
 * bundle carrying a 2 GiB unlisted member died with an OutOfMemoryError instead of being refused, and a legitimate
 * 142 MB log needed a heap bigger than itself. The reproduction, scaled: members of 256 and 160 MiB against a child
 * analyser with a 64 MiB heap, through the REAL entry point ({@code Main.main}, {@code --verify}, {@code --unpack}),
 * so the heap bound is the one a recipient's analyser runs with, and nothing here shortcuts the read path.
 */
class EvidenceBundleBoundedMemoryTest {

    static final long MIB = 1L << 20;
    static final String HEAP = "-Xmx64m";

    record Run(int code, String out, String err) { }

    /** The analyser, in its own JVM with a small heap, as a recipient runs it. */
    static Run analyser(Path home, String... args) throws Exception {
        List<String> cmd = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                HEAP, "-Djava.awt.headless=true", "-Duser.home=" + home,
                "-cp", System.getProperty("java.class.path"), "telamin.fluxtion.audit.analyser.Main"));
        cmd.addAll(List.of(args));
        Path out = Files.createTempFile(home, "out", ".txt"), err = Files.createTempFile(home, "err", ".txt");
        Process p = new ProcessBuilder(cmd).redirectOutput(out.toFile()).redirectError(err.toFile()).start();
        assertTrue(p.waitFor(120, TimeUnit.SECONDS), "the analyser finished");
        return new Run(p.exitValue(), Files.readString(out), Files.readString(err));
    }

    /** Every entry of {@code good}, then {@code large} as {@code mib} MiB of zeros, streamed: it is never in memory here. */
    static Path withLargeEntry(Path good, Path out, String large, long mib, boolean replaceListed) throws IOException {
        Map<String, byte[]> entries = EvidenceBundleTest.entries(good);
        try (OutputStream os = Files.newOutputStream(out); ZipOutputStream z = new ZipOutputStream(os)) {
            for (var e : entries.entrySet()) {
                if (replaceListed && e.getKey().equals(large)) continue;
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue());
                z.closeEntry();
            }
            z.putNextEntry(new ZipEntry(large));
            byte[] chunk = new byte[(int) MIB];
            for (long i = 0; i < mib; i++) z.write(chunk);
            z.closeEntry();
        }
        return out;
    }

    static Path good(Path tmp) throws IOException {
        Path out = tmp.resolve("good.fexp");
        EvidenceBundle.pack(EvidenceBundleTest.demoFolder(tmp), out, EvidenceBundleTest.AT, EvidenceBundleTest.VERSION);
        return out;
    }

    @Test
    @DisplayName("F1: a small bundle carrying a 256 MiB UNLISTED member is refused by name, in a 64 MiB heap — not a crash")
    void aBombIsRefusedNotACrash(@TempDir Path tmp) throws Exception {
        Path bomb = withLargeEntry(good(tmp), tmp.resolve("bomb.fexp"), "log/bomb.bin", 256, false);
        assertTrue(Files.size(bomb) < 2 * MIB, "control: the bomb is small on disk: " + Files.size(bomb));
        Run r = analyser(tmp, "--verify", bomb.toString());
        assertFalse(r.err().contains("OutOfMemoryError"), "verification must not run out of memory:\n" + r.err());
        assertTrue(r.err().contains("REFUSED: unlisted member: log/bomb.bin"), "it is a refusal, naming the member:\n" + r.err());
        assertEquals(1, r.code(), r.out() + r.err());
    }

    @Test
    @DisplayName("F1: a listed member LARGER than its declared size is refused when it exceeds, in a 64 MiB heap")
    void anOversizedListedMemberIsRefused(@TempDir Path tmp) throws Exception {
        Path big = withLargeEntry(good(tmp), tmp.resolve("big.fexp"), "log/demo-quote-audit.yaml", 256, true);
        Run r = analyser(tmp, "--verify", big.toString());
        assertFalse(r.err().contains("OutOfMemoryError"), r.err());
        assertTrue(r.err().contains("REFUSED: changed member: log/demo-quote-audit.yaml (larger than the manifest's"),
                "refused at the declared size, naming it:\n" + r.err());

        Path copies = tmp.resolve("copies");
        Run u = analyser(tmp, "--unpack", big.toString(), "--into", copies.toString());
        assertEquals(1, u.code(), u.out() + u.err());
        assertFalse(Files.exists(copies), "and a refused unpack writes nothing, not even its parent");
    }

    @Test
    @DisplayName("F1: a LEGITIMATE 160 MiB log (larger than the 142 MB real ones) verifies and unpacks in a 64 MiB heap")
    void aLegitimateLargeLogVerifiesAndUnpacksInBoundedMemory(@TempDir Path tmp) throws Exception {
        Path folder = Files.createDirectories(tmp.resolve("large/log"));
        Path log = folder.resolve("demo-large-audit.yaml");
        byte[] line = "eventLogRecord: DEMO quote\n".getBytes(StandardCharsets.UTF_8);
        byte[] block = new byte[line.length * 40_000];
        for (int i = 0; i < 40_000; i++) System.arraycopy(line, 0, block, i * line.length, line.length);
        try (OutputStream os = Files.newOutputStream(log)) {
            for (long n = 0; n < 160 * MIB; n += block.length) os.write(block);
        }
        Path bundle = tmp.resolve("large.fexp");
        String identity = EvidenceBundle.pack(tmp.resolve("large"), bundle, Instant.parse("2026-09-28T12:00:00Z"), "test");

        Run v = analyser(tmp, "--verify", bundle.toString());
        assertFalse(v.err().contains("OutOfMemoryError"), "an honest bundle must not need a heap bigger than itself:\n" + v.err());
        assertEquals(0, v.code(), v.out() + v.err());
        assertTrue(v.out().contains("identity: " + identity) && v.out().contains("verified: 1 members"), v.out());

        Run u = analyser(tmp, "--unpack", bundle.toString(), "--into", tmp.resolve("copies").toString());
        assertEquals(0, u.code(), u.out() + u.err());
        Path copy = Path.of(u.out().lines().filter(l -> l.startsWith("working copy: ")).findFirst().orElseThrow()
                .substring("working copy: ".length()).split("  \\(")[0]);
        assertEquals(Files.size(log), Files.size(copy.resolve("log/demo-large-audit.yaml")), "the whole log, extracted");
        assertEquals(-1L, Files.mismatch(log, copy.resolve("log/demo-large-audit.yaml")), "byte for byte");
    }
}
