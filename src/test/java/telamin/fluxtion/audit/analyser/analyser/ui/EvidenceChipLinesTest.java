package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.core.FileObservation;
import telamin.fluxtion.audit.analyser.analyser.session.BundleProvenance;
import telamin.fluxtion.audit.analyser.analyser.session.LogObservationState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionSnapshot;

import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ★ M79-3 A3.1 and A3.2 (spec-first-minute §8.1): the evidence chip's three states, for every row of §8.1's table, from
 * the snapshot alone; no row without a byte or digest check shows ✓; {@code unknown} is ⚠; and the details hold each
 * former row's prose verbatim. Witnesses (rule 8, in the PR): map {@code unchanged-metadata} to ✓; drop the basis
 * sentence from the details.
 */
class EvidenceChipLinesTest {

    static final String BASIS = "size, modification time and file identity; unchanged metadata does not prove identical bytes";
    static final long G = 4;

    /** A snapshot with a log of generation {@link #G} open and the named components replaced. */
    static SessionSnapshot snapshot(Map<String, Object> with) {
        try {
            RecordComponent[] cs = SessionSnapshot.class.getRecordComponents();
            Object[] values = new Object[cs.length];
            Map<String, Object> set = new LinkedHashMap<>(Map.of("logOpen", true, "logGeneration", G,
                    "logPath", "/demo/audit.yaml"));
            set.putAll(with);
            for (int i = 0; i < cs.length; i++) {
                values[i] = set.containsKey(cs[i].getName()) ? set.get(cs[i].getName())
                        : cs[i].getAccessor().invoke(SessionSnapshot.EMPTY);
            }
            Class<?>[] types = Arrays.stream(cs).map(RecordComponent::getType).toArray(Class<?>[]::new);
            return SessionSnapshot.class.getDeclaredConstructor(types).newInstance(values);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    static LogObservationState observed(String state) {
        return new LogObservationState(G, state, "unknown".equals(state) ? "no read-time metadata observation" : BASIS,
                1, true, true);
    }

    static final BundleProvenance BUNDLE = new BundleProvenance("sha256:0123456789abcdef0123", "/in/case.fexp",
            "/tmp/wc", "unsigned: not authenticated to a sender\nno replay", "", null);

    record Row(String what, Map<String, Object> with, EvidenceChipLines.State want) {
    }

    /** §8.1's table, row by row, plus the cases that must NOT earn ✓. */
    static List<Row> rows() {
        List<Row> out = new ArrayList<>();
        out.add(new Row("Follow found the file changed", Map.of("logIdentity", "UNVERIFIED",
                "logIdentityReason", "the file changed", "logObservation", observed("unchanged-metadata")),
                EvidenceChipLines.State.CHANGED));
        out.add(new Row("Follow found the file replaced", Map.of("logIdentity", "REPLACEMENT",
                "logObservation", observed("unchanged-metadata")), EvidenceChipLines.State.CHANGED));
        out.add(new Row("metadata changed on disk", Map.of("logObservation", observed("changed-on-disk")),
                EvidenceChipLines.State.CHANGED));
        out.add(new Row("a bundle's log whose metadata changed", Map.of("logObservation", observed("changed-on-disk"),
                "bundle", BUNDLE, "logPath", "/tmp/wc/log/audit.yaml"), EvidenceChipLines.State.CHANGED));
        out.add(new Row("metadata unknown", Map.of("logObservation", observed("unknown")), EvidenceChipLines.State.UNKNOWN));
        out.add(new Row("nothing observed yet", Map.of(), EvidenceChipLines.State.UNKNOWN));
        out.add(new Row("an observation of another log", Map.of("logObservation",
                new LogObservationState(G + 1, "unchanged-metadata", BASIS, 1, true, true)), EvidenceChipLines.State.UNKNOWN));
        out.add(new Row("the read could not be identified", Map.of("logObservation",
                new LogObservationState(G, "unchanged-metadata", BASIS, 1, true, false)), EvidenceChipLines.State.UNKNOWN));
        out.add(new Row("Follow verified every byte", Map.of("logIdentity", "VERIFIED", "logIdentityReason",
                "the same file; every byte already read is verified unchanged", "logObservation",
                observed("unchanged-metadata")), EvidenceChipLines.State.CHECKED));
        out.add(new Row("a verified bundle's own log", Map.of("bundle", BUNDLE, "logPath", "/tmp/wc/log/audit.yaml",
                "logObservation", observed("unchanged-metadata")), EvidenceChipLines.State.CHECKED));
        out.add(new Row("an ordinary log, metadata unchanged", Map.of("logObservation", observed("unchanged-metadata")),
                EvidenceChipLines.State.AS_READ));
        out.add(new Row("a reader that reports no read identity", Map.of("logObservation",
                new LogObservationState(G, "unchanged-metadata", BASIS, 1, false, false)), EvidenceChipLines.State.AS_READ));
        out.add(new Row("another log opened under a bundle's project", Map.of("bundle", BUNDLE,
                "logPath", "/elsewhere/audit.yaml", "logObservation", observed("unchanged-metadata")),
                EvidenceChipLines.State.AS_READ));
        out.add(new Row("REOPENED after a replacement, metadata unchanged", Map.of("logIdentity", "REOPENED",
                "logObservation", observed("unchanged-metadata")), EvidenceChipLines.State.AS_READ));
        return out;
    }

    @Test
    @DisplayName("★ A3.1: every row of §8.1 maps to its state; unknown is ⚠; only a byte or digest check is ✓")
    void everyRowHasItsState() {
        for (Row r : rows()) {
            var lines = EvidenceChipLines.of(snapshot(r.with()));
            assertNotNull(lines, r.what());
            assertEquals(r.want(), lines.state(), r.what());
            String chip = lines.chip();
            switch (r.want()) {
                case CHANGED -> assertEquals("⚠ changed", chip, r.what());
                case UNKNOWN -> assertEquals("⚠ unknown", chip, r.what());
                case CHECKED -> assertEquals("Evidence ✓ checked", chip, r.what());
                case AS_READ -> assertEquals("Evidence · as read", chip, r.what());
            }
            // the glyph is never the only signal: every state has words and a summary
            assertTrue(chip.chars().filter(Character::isLetter).count() >= 5, r.what());
            assertFalse(lines.summary().isBlank(), r.what());
            // ✓ only on a byte or digest check: Follow's VERIFIED, or a verified bundle's own log
            boolean byteCheck = "VERIFIED".equals(r.with().get("logIdentity"))
                    || (r.with().get("bundle") != null && String.valueOf(r.with().get("logPath")).startsWith("/tmp/wc/"));
            if (chip.contains("✓")) assertTrue(byteCheck, "✓ without a byte or digest check: " + r.what());
        }
        assertNull(EvidenceChipLines.of(SessionSnapshot.EMPTY), "no log, no chip");
    }

    @Test
    @DisplayName("★ A3.2: the details hold every former row's prose verbatim, and the ⚠ words are the records table's")
    void theDetailsKeepTheWords() throws Exception {
        String model = Files.readString(Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/core/FileObservation.java"));
        assertTrue(model.contains("\"" + BASIS + "\""), "the basis this test holds is FileObservation's own");
        // an ordinary log: the file observation's state and its basis
        var asRead = EvidenceChipLines.of(snapshot(Map.of("logObservation", observed("unchanged-metadata"))));
        assertTrue(text(asRead).contains("unchanged-metadata — " + BASIS), text(asRead));
        // nothing observed: FileObservation's own words for it
        var none = EvidenceChipLines.of(snapshot(Map.of()));
        assertTrue(text(none).contains("unknown — " + FileObservation.UNOBSERVED.get("basis")), text(none));
        // a bundle: the former bundle row's words, and the bundle's own limits
        var bundle = EvidenceChipLines.of(snapshot(Map.of("bundle", BUNDLE, "logPath", "/tmp/wc/log/audit.yaml",
                "logObservation", observed("unchanged-metadata"))));
        assertTrue(text(bundle).contains("verified on open; unsigned, so it is not authenticated to a sender"), text(bundle));
        assertTrue(text(bundle).contains(BUNDLE.limits()), text(bundle));
        assertTrue(text(bundle).contains(BASIS), "the caveat is kept even when ✓: " + text(bundle));
        // Follow's ⚠: the records table's identity banner, from the same source
        var changed = EvidenceChipLines.of(snapshot(Map.of("logIdentity", "UNVERIFIED", "logIdentityReason",
                "the file changed after it was read", "logObservation", observed("unchanged-metadata"))));
        assertTrue(text(changed).contains(LogTablePanel.identityBannerText("UNVERIFIED", "the file changed after it was read")),
                text(changed));
    }

    static String text(EvidenceChipLines l) {
        StringBuilder b = new StringBuilder(l.summary());
        for (var d : l.details()) b.append('\n').append(d.label()).append(": ").append(d.words());
        return b.toString();
    }
}
