package telamin.fluxtion.audit.analyser.bundle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A bundle whose members differ only in case is refused by VERIFY, not left for unpack to trip over.
 *
 * <p>Found by a reviewer running the portability tests on a case-sensitive volume. Such a bundle verified, then
 * unpacked on their case-sensitive disk and was refused on a case-insensitive one — the refusal naming only a
 * path, with no reason. It failed safe, but the meaning of the artefact depended on the recipient's filesystem,
 * which is the one thing portable evidence cannot do. The app's own capture cannot produce such a bundle; it has
 * to be crafted, which is exactly why verification is the place to catch it.
 */
class BundleCaseCollisionTest {

    /** A bundle listing every given member, honestly digested, so only the case collision is wrong with it. */
    private static Path bundleListing(Path dir, String... members) throws Exception {
        StringBuilder json = new StringBuilder("{\"format\":1,\"createdAt\":\"" + Instant.EPOCH + "\",\"members\":[");
        for (int i = 0; i < members.length; i++) {
            byte[] body = ("DEMO " + members[i] + "\n").getBytes(StandardCharsets.UTF_8);
            json.append(i == 0 ? "" : ",").append("{\"path\":\"").append(members[i]).append("\",\"sha256\":\"")
                    .append(EvidenceBundle.sha256(body)).append("\",\"bytes\":").append(body.length).append("}");
        }
        json.append("],\"limits\":[]}");
        Path out = dir.resolve("crafted.fexp");
        try (var zip = new ZipOutputStream(Files.newOutputStream(out))) {
            zip.putNextEntry(new ZipEntry(EvidenceBundle.MANIFEST));
            zip.write(json.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (String m : members) {
                zip.putNextEntry(new ZipEntry(m));
                zip.write(("DEMO " + m + "\n").getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return out;
    }

    @Test
    @DisplayName("Members differing only in case are refused, naming both, whatever this disk does")
    void aCaseCollisionIsRefusedByVerify(@TempDir Path dir) throws Exception {
        Path bundle = bundleListing(dir, "notes/NOTES.md", "notes/notes.md");

        var v = EvidenceBundle.verify(bundle);

        assertFalse(v.ok(), "it verified, so unpack would decide — and the two disks decide differently");
        assertTrue(v.refusal().contains("differ only in case"), v.refusal());
        assertTrue(v.refusal().contains("notes/NOTES.md") && v.refusal().contains("notes/notes.md"),
                "a refusal that names one path leaves the reader to guess what it collided with: " + v.refusal());
    }

    @Test
    @DisplayName("Unpack refuses it too, and writes nothing — the same answer on either filesystem")
    void unpackGivesTheSameAnswer(@TempDir Path dir) throws Exception {
        Path bundle = bundleListing(dir, "notes/NOTES.md", "notes/notes.md");
        Path into = Files.createDirectory(dir.resolve("into"));

        var u = EvidenceBundle.unpack(bundle, into);

        assertFalse(u.verification().ok(), u.verification().refusal());
        assertNull(u.workingCopy());
        try (var kids = Files.list(into)) {
            assertEquals(0, kids.count(), "a refused bundle leaves nothing behind");
        }
    }

    @Test
    @DisplayName("Paths that merely SHARE a folder are fine — the rule is a collision, not a resemblance")
    void ordinaryMembersAreUnaffected(@TempDir Path dir) throws Exception {
        Path bundle = bundleListing(dir, "notes/NOTES.md", "notes/OTHER.md", "log/a.yaml");

        assertTrue(EvidenceBundle.verify(bundle).ok(),
                "over-refusing here would reject bundles the app itself produces");
    }
}
