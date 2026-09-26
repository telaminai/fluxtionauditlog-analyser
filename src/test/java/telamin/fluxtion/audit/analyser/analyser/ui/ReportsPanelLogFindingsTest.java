package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.ProducerDiagnostics;
import telamin.fluxtion.audit.analyser.analyser.report.FilterSnapshot;
import telamin.fluxtion.audit.analyser.analyser.report.LogFingerprint;
import telamin.fluxtion.audit.analyser.analyser.report.ReportRenderer;
import telamin.fluxtion.audit.analyser.analyser.report.ReportResolver;
import telamin.fluxtion.audit.analyser.analyser.report.ReportSpec;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Review item 9 — the Reports tab draws the log's findings the way the PDF does. A set of NOTES only (a rolled set's
 * completeness note) states a limit, not a fault, and the PDF draws it muted; the tab used the warning banner for both.
 *
 * <p>Asserted by colour against the tab's own muted line ("written against …"), so the test holds under either theme.
 */
class ReportsPanelLogFindingsTest {

    private static final HeapLogStore STORE =
            new HeapLogStore("---\neventLogRecord:\n  event: Tick\n  logTime: 1000\n  nodeLogs:\n    - a: { v: 1}\n---\n");

    private record Colours(Color heading, Color muted) { }

    private static Colours render(ProducerDiagnostics findings) throws Exception {
        var spec = new ReportSpec("inv", "Rolled set", "2026-09-26T00:00:00Z", "",
                LogFingerprint.of(STORE.index(), "set.yaml"), FilterSnapshot.all(),
                List.of(ReportSpec.SectionSpec.narrative("What we saw.")));
        AtomicReference<Colours> out = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            var panel = new ReportsPanel(() -> List.of(spec), s -> ReportResolver.resolve(s, STORE.index(), Map.of(),
                    Set.of(), Set.of(), new FilterState()), s -> null, r -> { }, g -> { }, f -> { }, f -> { }, p -> { });
            panel.setLogFindings(() -> findings);
            panel.refresh();
            out.set(new Colours(foreground(panel, t -> t.equals(ReportRenderer.LOG_FINDINGS_LABEL)),
                    foreground(panel, t -> t.startsWith("written against"))));
        });
        return out.get();
    }

    private static Color foreground(Component c, java.util.function.Predicate<String> text) {
        if (c instanceof JTextArea t && text.test(t.getText())) return t.getForeground();
        if (c instanceof Container k) {
            for (Component child : k.getComponents()) {
                Color hit = foreground(child, text);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    private static LogIndex oneRecord() {
        return STORE.index();
    }

    @Test
    void aNoteOnlySetIsDrawnMuted_likeThePdf() throws Exception {
        var notes = ProducerDiagnostics.of(oneRecord(), STORE::rawText, List.of(),
                List.of("each file says it is whole, and that says nothing about the set"), true);
        assertTrue(notes.firstWarning().isEmpty() && !notes.isClean(), "precondition: notes, and nothing else");
        Colours c = render(notes);
        assertNotNull(c.heading(), "the findings are on the tab");
        assertNotNull(c.muted(), "precondition: the tab has a muted line to compare with");
        assertEquals(c.muted(), c.heading(), "a statement of a limit is drawn muted, not as a warning");
    }

    @Test
    void aWarningStillWearsTheWarningBanner() throws Exception {
        var warning = ProducerDiagnostics.of(new LogIndex(), i -> null, List.of(), List.of(), false);
        assertTrue(warning.firstWarning().isPresent(), "precondition: an empty log is a warning");
        Colours c = render(warning);
        assertNotNull(c.heading(), "the findings are on the tab");
        assertNotEquals(c.muted(), c.heading(), "control: a warning is not muted, so the test above can fail");
    }
}
