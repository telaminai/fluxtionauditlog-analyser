package telamin.fluxtion.audit.analyser.bundle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile;
import telamin.fluxtion.audit.analyser.analyser.config.SettingsShare;
import telamin.fluxtion.audit.analyser.analyser.report.Finding;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkIdentity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #178: a bundle's flags travel anchored by record digest — the walk record target's rule — and come back only on a
 * record whose text still has that digest. A mismatch is stated, record by record, never attached to the wrong record.
 */
class BundleFlagsTest {

    static final List<String> RECORDS = List.of("record zero", "record one", "record two", "record three");

    static List<BundleFlags.Flag> flagged() {
        return BundleFlags.of(Map.of(1, new Finding(1, "DEMO note one", "DEMO fix one", "fault"),
                        3, new Finding(3, "DEMO seen as designed", null, "confirmation")),
                Set.of(1, 3, 2), RECORDS::get);
    }

    @Test
    @DisplayName("each flag is anchored by its record's digest — the same digest a walk's record target is bound by")
    void anchoredByTheWalkRecordDigest() {
        var flags = flagged();
        assertEquals(List.of(1, 2, 3), flags.stream().map(BundleFlags.Flag::record).toList());
        assertEquals(WalkIdentity.recordDigest("record one"), flags.get(0).digest());
        assertEquals("", flags.get(1).note(), "a flag with no text still marks its row");
    }

    @Test
    @DisplayName("round trip: appended to the settings text, read back, rebound onto the same records — note, fix and kind")
    void roundTrip() throws Exception {
        String text = BundleFlags.append("theme=dark\n", flagged());
        var p = new java.util.Properties();
        p.load(new java.io.StringReader(text));
        assertEquals("dark", p.getProperty("theme"), "the settings are untouched");
        var back = BundleFlags.read(p);
        assertEquals(flagged(), back);
        var rebind = BundleFlags.rebind(back, RECORDS.size(), RECORDS::get);
        assertEquals(List.of(), rebind.withheld());
        assertEquals(new Finding(1, "DEMO note one", "DEMO fix one", "fault"), rebind.restored().get(0));
        assertEquals("confirmation", rebind.restored().get(2).kind());
    }

    @Test
    @DisplayName("★ #178 mismatch: a record whose text differs, or that is not there, gets NO flag — and the reason is stated")
    void aMismatchIsStatedNeverAttached() {
        List<String> changed = new ArrayList<>(RECORDS);
        changed.set(1, "record one, rewritten");
        var rebind = BundleFlags.rebind(flagged(), 3, changed::get);    // record 3 is missing too
        assertEquals(List.of(2), rebind.restored().stream().map(Finding::recordIndex).toList(),
                "only the record whose text is unchanged gets its flag back");
        assertEquals(2, rebind.withheld().size(), String.valueOf(rebind.withheld()));
        assertTrue(rebind.withheld().get(0).contains("record 1") && rebind.withheld().get(0).contains("differs"),
                rebind.withheld().get(0));
        assertTrue(rebind.withheld().get(1).contains("record 3") && rebind.withheld().get(1).contains("not in this log"),
                rebind.withheld().get(1));
    }

    @Test
    @DisplayName("an unknown digest is never equal: a flag with none is withheld, not attached")
    void unknownIsNeverEqual() {
        var rebind = BundleFlags.rebind(List.of(new BundleFlags.Flag(0, "", "fault", "x", null)), 4, RECORDS::get);
        assertTrue(rebind.restored().isEmpty());
        assertEquals(1, rebind.withheld().size());
    }

    @Test
    @DisplayName("malformed entries are skipped, never fatal (ignore, never reject)")
    void malformedEntriesAreSkipped() {
        var p = new java.util.Properties();
        p.setProperty("bundleFlag.0.record", "x");
        p.setProperty("bundleFlag.1.record", "-3");
        p.setProperty("bundleFlag.2.record", "1");
        p.setProperty("bundleFlag.2.kind", "rumour");
        p.setProperty("bundleFlag.3.record", "2");
        p.setProperty("bundleFlag.3.digest", "sha256:x");
        assertEquals(List.of(2), BundleFlags.read(p).stream().map(BundleFlags.Flag::record).toList());
    }

    @Test
    @DisplayName("the profile member carries them, with a machine path in a note REDACTED and named, like every kept value")
    void theProfileMemberCarriesThemRedacted(@TempDir Path tmp) throws Exception {
        Path settings = Files.createDirectories(tmp.resolve("p/.analyser")).resolve("project.fluxtion-settings");
        String text = ProjectProfile.write(new AppConfig(), new SettingsShare(), settings);
        var flags = List.of(new BundleFlags.Flag(1, WalkIdentity.recordDigest("record one"), "fault",
                "DEMO seen in /home/demo/logs/run.yaml", null));
        Files.writeString(settings, BundleFlags.append(text, flags));
        Path out = tmp.resolve("member.fluxtion-settings");
        var x = BundleProfile.export(settings, out);
        var carried = BundleFlags.read(out);
        assertEquals(1, carried.size());
        assertEquals("DEMO seen in " + BundleProfile.REDACTED, carried.get(0).note(), "no machine path leaves");
        assertTrue(x.redacted().stream().anyMatch(r -> r.startsWith("bundleFlag.0.note")), String.valueOf(x.redacted()));
    }

    @Test
    @DisplayName("an excerpt shifts each flag onto the excerpt's numbering, and names a flag outside it as left out")
    void anExcerptShiftsAndLeavesOut() {
        List<String> leftOut = new ArrayList<>();
        var shifted = BundleFlags.rebase(flagged(), 2, 3, leftOut);
        assertEquals(List.of(0, 1), shifted.stream().map(BundleFlags.Flag::record).toList());
        assertEquals(flagged().get(1).digest(), shifted.get(0).digest(), "the digest is the record's text, unchanged");
        assertEquals(1, leftOut.size());
        assertTrue(leftOut.get(0).contains("record 1"), leftOut.get(0));
    }
}
