package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.report.*;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** Constructed regression cases for the same finding's human and assistant surfaces. */
class FindingPresentationTest {
    @Test void confirmationUsesNeutralLabelsOnTableTopologyAndReports() throws Exception {
        Finding f = new Finding(0, "at-limit accepted", "matches expected boundary", "confirmation");
        assertEquals("Observation: at-limit accepted\nAssessment: matches expected boundary", f.tableText());
        BufferedImage image = new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            String text = String.join("\n", TopologyCanvas.calloutLines(f, graphics.getFontMetrics(), 550)
                    .stream().map(TopologyCanvas.CalloutLine::text).toList());
            assertTrue(text.contains("Observation · at-limit accepted"));
            assertTrue(text.contains("Assessment · matches expected boundary"));
            assertFalse(text.contains("Fix ·"));
        } finally { graphics.dispose(); }
        var store = new HeapLogStore("---\neventLogRecord:\n  event: Tick\n  logTime: 1000\n---\n");
        var spec = new ReportSpec("confirmation", "Boundary evidence", "2026-09-21T00:00:00Z", "",
                LogFingerprint.of(store.index(), "constructed.yaml"), FilterSnapshot.all(),
                List.of(ReportSpec.SectionSpec.finding(0)));
        SwingUtilities.invokeAndWait(() -> {
            var panel = new ReportsPanel(() -> List.of(spec), s -> ReportResolver.resolve(s,
                    store.index(), Map.of(0, f), Set.of(), Set.of(), new FilterState()), s -> null,
                    r -> { }, g -> { }, focus -> { }, filter -> { }, path -> { },
                    delete -> { }, (from, to) -> null);
            panel.refresh();
            String text = componentText(panel);
            assertTrue(text.contains("Observation — record #0"), text);
            assertTrue(text.contains("Assessment"), text);
            assertFalse(text.contains("What is wrong"), text);
        });
    }

    /**
     * Flag kind default change: a flag without {@code kind} once saved a FAULT, so correct refusals an agent flagged as evidence were stored
     * as faults. A new finding without a kind is now a neutral note; explicit kinds still work; an existing finding
     * keeps its kind when a later flag omits it. Wrong-result witness: default {@link Finding#flagged} to fault again.
     */
    @Test void aFlagWithoutKindIsANeutralNoteAndExplicitKindsStillWork() {
        var store = new HeapLogStore("---\neventLogRecord:\n  event: Tick\n  logTime: 1000\n---\n");
        var filter = new FilterState();
        var tabs = new GraphTabs();
        tabs.bind(store, filter);
        java.util.Map<Integer, Finding> findings = new java.util.HashMap<>();
        var ex = new ActionExecutor(() -> store, () -> filter, tabs, new LogTablePanel(),
                (rows, note, fix, kind) -> findings.put(rows[0],
                        Finding.flagged(findings.get(rows[0]), rows[0], note, fix, kind)));
        assertTrue(ex.render("flag", Map.of("recordIndexes", List.of(0), "note", "refused, as designed")).ok());
        assertEquals("note", findings.get(0).kind());
        assertEquals("Note", findings.get(0).noteLabel());
        assertEquals("Note: refused, as designed", findings.get(0).tableText());

        findings.clear();
        assertTrue(ex.render("flag", Map.of("recordIndexes", List.of(0), "note", "broken", "kind", "fault")).ok());
        assertEquals("fault", findings.get(0).kind());
        assertEquals("What is wrong", findings.get(0).noteLabel());
        assertTrue(ex.render("flag", Map.of("recordIndexes", List.of(0), "fix", "check the limit")).ok());
        assertEquals("fault", findings.get(0).kind(), "omitting kind keeps an existing finding's category");
        assertEquals("broken", findings.get(0).note());

        assertFalse(ex.render("flag", Map.of("recordIndexes", List.of(0), "kind", "invalid")).ok());
        assertEquals("fault", findings.get(0).kind());
        assertTrue(ex.render("flag", Map.of("recordIndexes", List.of(0), "kind", "confirmation")).ok());
        assertEquals("confirmation", findings.get(0).kind());
        assertTrue(ex.render("flag", Map.of("recordIndexes", List.of(0), "kind", "note")).ok());
        assertEquals("note", findings.get(0).kind());
    }

    @Test void aNoteUsesNeutralLabelsOnTheTopologyCallout() {
        Finding f = new Finding(0, "limit reached", "as configured", "note");
        BufferedImage image = new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            String text = String.join("\n", TopologyCanvas.calloutLines(f, graphics.getFontMetrics(), 550)
                    .stream().map(TopologyCanvas.CalloutLine::text).toList());
            assertTrue(text.contains("Note · limit reached"), text);
            assertTrue(text.contains("Assessment · as configured"), text);
            assertFalse(text.contains("Fix ·"), text);
        } finally { graphics.dispose(); }
    }

    @Test void theFlagSchemaNamesTheNeutralDefaultAndEveryKind() {
        String schema = telamin.fluxtion.audit.analyser.analyser.llm.Json.write(
                telamin.fluxtion.audit.analyser.analyser.llm.VerbSchemas.hosted().get("flag"));
        for (String kind : Finding.KINDS) assertTrue(schema.contains("\"" + kind + "\""), schema);
        assertTrue(schema.contains("saved as 'note'"), schema);
        assertEquals("note", Finding.FLAG_DEFAULT_KIND);
        assertEquals("fault", Finding.fromMap(Map.of("recordIndex", 0, "note", "legacy")).kind(),
                "a finding saved before categories is still read as a fault");
    }

    /**
     * A bare bookmark (an empty finding) renders nothing — no table text and no callout — whatever its kind; a lone
     * "Note: " / "Note · " once showed after a restore. Wrong-result witness: drop the isEmpty/hasNote guards.
     */
    @Test void anEmptyFindingRendersNothingWhateverItsKind() {
        BufferedImage image = new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            for (String kind : Finding.KINDS) {
                Finding bare = new Finding(3, null, null, kind);
                assertTrue(bare.isEmpty());
                assertNull(bare.tableText(), kind);
                assertTrue(TopologyCanvas.calloutLines(bare, graphics.getFontMetrics(), 550).isEmpty(), kind);
            }
            Finding fixOnly = new Finding(3, null, "check the cap", "note");
            assertEquals("Assessment: check the cap", fixOnly.tableText());
            String text = String.join("\n", TopologyCanvas.calloutLines(fixOnly, graphics.getFontMetrics(), 550)
                    .stream().map(TopologyCanvas.CalloutLine::text).toList());
            assertFalse(text.contains("Note ·"), text);
            assertTrue(text.contains("Assessment · check the cap"), text);
        } finally { graphics.dispose(); }
    }

    /**
     * Session recovery's persisted shape. A bare bookmark is saved WITHOUT a kind — the shape a build that predates
     * the note kind restores (it rejects "note", and one rejected entry withholds every finding) — and reads back as
     * a note. A finding with text keeps its kind; one with text and no kind is a pre-category fault.
     */
    @Test void aBareBookmarkPersistsWithoutAKindAndRestoresAsANote() {
        Map<String, Object> bare = new Finding(3, null, null, Finding.FLAG_DEFAULT_KIND).toMap();
        assertFalse(bare.containsKey("kind"), "no kind an older build would reject: " + bare);
        assertEquals(Map.of("recordIndex", 3, "note", ""), Map.of("recordIndex", bare.get("recordIndex"), "note", bare.get("note")));
        assertNull(bare.get("fix"));
        assertEquals(new Finding(3, null, null, "note"), Finding.fromMap(bare));

        Map<String, Object> written = new Finding(4, "refused, as designed", null, "note").toMap();
        assertEquals("note", written.get("kind"));
        assertEquals("note", Finding.fromMap(written).kind());
        for (String kind : List.of("fault", "confirmation")) {
            Map<String, Object> m = new Finding(5, null, null, kind).toMap();
            assertEquals(kind, m.get("kind"), "an explicitly categorised empty finding keeps its kind");
            assertEquals(kind, Finding.fromMap(m).kind());
        }
        assertEquals("fault", Finding.fromMap(Map.of("recordIndex", 0, "note", "legacy")).kind());
        assertEquals("fault", Finding.fromMap(Map.of("recordIndex", 0, "note", "", "kind", "fault")).kind(),
                "a bare flag saved by 1.40.3 (kind fault) restores as it was saved");
    }

    private static String componentText(Component component) {
        StringBuilder text = new StringBuilder();
        if (component instanceof JTextComponent field) text.append(field.getText()).append('\n');
        if (component instanceof JLabel label) text.append(label.getText()).append('\n');
        if (component instanceof Container container)
            for (Component child : container.getComponents()) text.append(componentText(child));
        return text.toString();
    }
}
