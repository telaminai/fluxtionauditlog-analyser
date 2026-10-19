package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.filter.RecordsViewCounts;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.TitledBorder;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;

/**
 * A filter is visible wherever it hides records — the usability review of the Event types list and records filtering
 * (2026-10-18). Every surface here renders the session's records-view counts ({@code recordsView}, rule 9), so these
 * tests drive the REAL frame and read what the person sees.
 * <ul>
 *   <li>B1: with the checklist hidden, the toggle still says a type filter is in force ("1 of 7") and the Records title
 *       gives the hidden count;</li>
 *   <li>B2: a text filter set through the action socket is shown in the Search box and named in the hidden line;</li>
 *   <li>M1: each checklist row counts its records under the OTHER filters, and a row they empty is muted;</li>
 *   <li>M2: an empty table states, as an accessible component, which filters hide the records, and each undo brings
 *       them back; Record detail says the filter hides every record.</li>
 * </ul>
 * Mutation witnesses (each fails the test named): B1 — {@code RecordsViewWords.typesBadge} returning null; B2 — the
 * Search box write removed from {@code renderRecordsView}; M1 — {@code RecordsViewWords.typeCount} printing the total
 * only; M2 — {@code RecordsFilterBar} never visible in EMPTY mode, or the TYPES undo case removed.
 */
class RecordsFilterVisibilityFrameTest {

    @Test
    @DisplayName("B1: hidden checklist, one type selected — the toggle says 1 of 7 and the Records title the hidden count")
    void aTypeFilterIsVisibleWithTheListHidden(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (var f = open(tmp)) {
            showChecklist(f, false);
            String three = typeWithTotal(f, 3);
            assertTrue(f.ex.render("filter", Map.of("dimensions", List.of(three))).ok());
            settle();
            snapshot(f, "b1-hidden-list-one-type.png");
            onEdt(() -> {
                JToggleButton t = (JToggleButton) field(f.frame, "eventTypesToggle");
                assertTrue(t.getText().startsWith("▸"), "the arrow says the list is hidden: " + t.getText());
                assertTrue(t.getIcon() instanceof RecordsFilterBar.Badge, "a badge shows the selection: " + t.getIcon());
                assertEquals("1 of 7", ((RecordsFilterBar.Badge) t.getIcon()).text());
                String name = t.getAccessibleContext().getAccessibleName();
                assertTrue(name.contains("1 of 7 selected") && name.contains("list hidden"), name);
                assertEquals("Records: 3 of 21 shown · 18 hidden", recordsTitle(f));
                RecordsFilterBar line = (RecordsFilterBar) field(f.frame, "hiddenLine");
                assertTrue(line.isShowing(), "the hidden line shows under the title");
                assertEquals("18 hidden by event types", line.sentence());
                assertEquals("showing 3 of 21", ((javax.swing.JLabel) field(f.frame, "showingLabel")).getText());
            });
            // robot walk of P3: an assistant reading context learns which filter hides what (context.hidden, additive)
            AtomicReference<Map<String, Object>> ctx = new AtomicReference<>();
            onEdt(() -> ctx.set(AsyncOpenInterleavingFrameTest.render(f.ex, "context", Map.of())));
            Map<?, ?> hidden = (Map<?, ?>) ((Map<?, ?>) ctx.get().get("context")).get("hidden");
            assertNotNull(hidden, "context.hidden: " + ctx.get());
            assertEquals(18, ((Number) hidden.get("count")).intValue());
            Map<?, ?> byTypes = (Map<?, ?>) ((List<?>) hidden.get("byFilter")).get(0);
            assertEquals("eventTypes", byTypes.get("filter"));
            assertEquals(18, ((Number) byTypes.get("hides")).intValue());
            List<?> excluded = (List<?>) byTypes.get("excludedTypes");
            assertEquals(6, excluded.size(), "every type but the selected one: " + excluded);
            assertFalse(excluded.contains(three));
            // and its undo brings them back
            onEdt(() -> button((RecordsFilterBar) field(f.frame, "hiddenLine"), "Show all").doClick());
            settle();
            onEdt(() -> {
                assertEquals("Records: all 21 shown", recordsTitle(f));
                assertNull(((JToggleButton) field(f.frame, "eventTypesToggle")).getIcon(), "no badge with every type selected");
                assertFalse(((RecordsFilterBar) field(f.frame, "hiddenLine")).isVisible());
            });
            showChecklist(f, true);
            onEdt(() -> assertTrue(((JToggleButton) field(f.frame, "eventTypesToggle")).getText().startsWith("▾")));
        }
    }

    @Test
    @DisplayName("B2: filter {text} through the action socket fills the Search box and is named in the hidden line")
    void aSocketTextFilterShowsInTheSearchBox(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (var f = open(tmp)) {
            onEdt(() -> {
                ((AppConfig) field(f.frame, "config")).assistantActionsRest = true;
                ExternalRestWithHostedSessionFrameTest.invoke(f.frame, "reportRestSwitch");
            });
            Path wk = Path.of(System.getProperty("user.home"), ".fluxtion-analyser/rest-endpoint");
            assertTrue(Files.exists(wk), "REST on: the well-known endpoint file");
            var ep = new telamin.fluxtion.audit.analyser.analyser.net.RestEndpointFile(wk).read();
            var http = java.net.http.HttpClient.newHttpClient();
            var reply = ExternalRestWithHostedSessionFrameTest.post(http, ep.url(),
                    "{\"action\":\"filter\",\"params\":{\"text\":\"hedging\"}}", ep.token(), null);
            assertEquals(200, reply.statusCode(), reply.body());
            settle();
            snapshot(f, "b2-socket-search.png");
            onEdt(() -> {
                HistoryComboBox search = (HistoryComboBox) field(f.frame, "searchField");
                assertEquals("hedging", search.getText(), "the person sees the filter the assistant set");
                RecordsFilterBar line = (RecordsFilterBar) field(f.frame, "hiddenLine");
                assertEquals("9 hidden by search \"hedging\"", line.sentence());
                assertEquals("Records: 12 of 21 shown · 9 hidden", recordsTitle(f));
            });
            // the line's × clears it, and the box follows
            onEdt(() -> button((RecordsFilterBar) field(f.frame, "hiddenLine"), "×").doClick());
            settle();
            onEdt(() -> assertEquals("", ((HistoryComboBox) field(f.frame, "searchField")).getText()));
        }
    }

    @Test
    @DisplayName("M1: under a search each row counts shown / total, a row the search empties is muted, order by total")
    void checklistCountsFollowTheOtherFilters(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (var f = open(tmp)) {
            showChecklist(f, true);
            List<String> before = new ArrayList<>();
            onEdt(() -> rows(f).forEach(r -> before.add(name(r))));
            assertTrue(f.ex.render("filter", Map.of("text", "hedging")).ok());
            settle();
            snapshot(f, "m1-counts-under-search.png");
            RecordsViewCounts rv = snapshot(f).recordsView();
            onEdt(() -> {
                List<JCheckBox> rows = rows(f);
                assertEquals(before, rows.stream().map(RecordsFilterVisibilityFrameTest::name).toList(),
                        "the order is by total and does not move under a search");
                int muted = 0, partial = 0;
                for (RecordsViewCounts.TypeCount t : rv.types()) {
                    JCheckBox cb = rows.stream().filter(r -> name(r).equals(t.type())).findFirst().orElseThrow();
                    if (t.shown() == t.total()) {
                        assertTrue(cb.getText().endsWith("(" + t.total() + ")"), cb.getText());
                    } else {
                        partial++;
                        assertTrue(cb.getText().endsWith("(" + t.shown() + " / " + t.total() + ")"), cb.getText());
                    }
                    if (t.shown() == 0) {
                        muted++;
                        assertEquals(UiTheme.mutedForeground(), cb.getForeground(), t.type() + " is muted at 0");
                    } else {
                        assertNotEquals(UiTheme.mutedForeground(), cb.getForeground(), t.type() + " is not muted");
                    }
                }
                assertTrue(partial > 0 && muted > 0, "the sample has both under \"hedging\": " + rv.types());
            });
        }
    }

    @Test
    @DisplayName("M2: an empty table names each filter with its own undo, accessibly; the undos restore the records")
    void theEmptyTableNamesTheFiltersAndUndoesThem(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (var f = open(tmp)) {
            assertTrue(f.ex.render("filter", Map.of("dimensions", List.of(), "text", "hedging")).ok());
            settle();
            snapshot(f, "m2-empty-table.png");
            onEdt(() -> {
                LogTablePanel table = (LogTablePanel) field(f.frame, "tablePanel");
                assertEquals(0, table.viewRowCount());
                RecordsFilterBar empty = table.emptyState();
                assertTrue(empty.isShowing(), "the empty table's statement is a component on screen");
                assertEquals("No records: event types exclude all 21 · search \"hedging\" excludes 9", empty.sentence());
                assertEquals(empty.sentence(), empty.getAccessibleContext().getAccessibleDescription(),
                        "a screen reader hears the same sentence");
                assertEquals(UiTheme.mutedForeground(), firstLabel(empty).getForeground(), "the theme's muted ink");
                assertEquals("Select all event types", button(empty, "Select all").getAccessibleContext().getAccessibleName());
                assertFalse(((RecordsFilterBar) field(f.frame, "hiddenLine")).isVisible(), "one statement, not two");
                assertEquals(DetailPanel.FILTER_HINT, ((DetailPanel) field(f.frame, "detailPanel")).paneText(),
                        "Record detail says why there is nothing to select");
            });
            onEdt(() -> button(((LogTablePanel) field(f.frame, "tablePanel")).emptyState(), "Select all").doClick());
            settle();
            onEdt(() -> {
                LogTablePanel table = (LogTablePanel) field(f.frame, "tablePanel");
                assertEquals(12, table.viewRowCount(), "the type undo brings back the search's records");
                assertFalse(table.emptyState().isVisible());
                assertNotEquals(DetailPanel.FILTER_HINT, ((DetailPanel) field(f.frame, "detailPanel")).paneText());
                assertNull(((FilterState) field(f.frame, "filter")).dimensions());
            });
            // empty it again by search alone, and clear the search from the empty table
            assertTrue(f.ex.render("filter", Map.of("text", "no-such-text")).ok());
            settle();
            onEdt(() -> button(((LogTablePanel) field(f.frame, "tablePanel")).emptyState(), "Clear").doClick());
            settle();
            onEdt(() -> assertEquals(21, ((LogTablePanel) field(f.frame, "tablePanel")).viewRowCount()));
        }
    }

    // ---------------------------------------------------------------------------------------------

    private static AsyncOpenInterleavingFrameTest.Frame open(Path tmp) throws Exception {
        var f = new AsyncOpenInterleavingFrameTest.Frame(tmp);
        f.dialogs.stop();
        Path log = Files.copy(Path.of("src/test/resources/sample.yml"), tmp.resolve("run.yml"));
        onEdt(() -> { f.frame.setSize(1300, 850); f.frame.setVisible(true); });
        assertTrue(f.ex.render("open", Map.of("log", log.toString())).ok());
        settle();
        assertNotNull(snapshot(f).recordsView(), "the open log's view was measured");
        return f;
    }

    private static telamin.fluxtion.audit.analyser.analyser.session.SessionSnapshot snapshot(
            AsyncOpenInterleavingFrameTest.Frame f) throws Exception {
        AtomicReference<telamin.fluxtion.audit.analyser.analyser.session.SessionSnapshot> s = new AtomicReference<>();
        onEdt(() -> s.set(((SessionDriver) field(f.frame, "session")).snapshot()));
        return s.get();
    }

    private static String typeWithTotal(AsyncOpenInterleavingFrameTest.Frame f, int total) throws Exception {
        return snapshot(f).recordsView().types().stream().filter(t -> t.total() == total).findFirst().orElseThrow().type();
    }

    private static String recordsTitle(AsyncOpenInterleavingFrameTest.Frame f) {
        var border = ((JPanel) field(f.frame, "tableArea")).getBorder();
        return ((TitledBorder) ((CompoundBorder) border).getOutsideBorder()).getTitle();
    }

    private static void showChecklist(AsyncOpenInterleavingFrameTest.Frame f, boolean show) throws Exception {
        onEdt(() -> {
            JToggleButton t = (JToggleButton) field(f.frame, "eventTypesToggle");
            if (t.isSelected() != show) t.doClick();
        });
        settle();
    }

    private static List<JCheckBox> rows(AsyncOpenInterleavingFrameTest.Frame f) {
        List<JCheckBox> out = new ArrayList<>();
        collect((Container) field(f.frame, "eventFilterPanel"), JCheckBox.class, out);
        return out;
    }

    /** A checklist row's type: its label up to the count. */
    private static String name(JCheckBox cb) {
        String t = cb.getText();
        return t.substring(0, t.lastIndexOf("  ("));
    }

    private static JButton button(Container c, String text) {
        List<JButton> out = new ArrayList<>();
        collect(c, JButton.class, out);
        return out.stream().filter(b -> text.equals(b.getText())).findFirst()
                .orElseThrow(() -> new AssertionError("no button " + text + " in " + out.stream().map(JButton::getText).toList()));
    }

    private static javax.swing.JLabel firstLabel(Container c) {
        List<javax.swing.JLabel> out = new ArrayList<>();
        collect(c, javax.swing.JLabel.class, out);
        return out.get(0);
    }

    private static <T> void collect(Container c, Class<T> type, List<T> out) {
        for (Component k : c.getComponents()) {
            if (type.isInstance(k)) out.add(type.cast(k));
            if (k instanceof Container kc) collect(kc, type, out);
        }
    }

    /** Optional before/after image: -Devtypes.shots=<dir>. */
    private static void snapshot(AsyncOpenInterleavingFrameTest.Frame f, String name) throws Exception {
        String dir = System.getProperty("evtypes.shots");
        if (dir == null) return;
        AtomicReference<BufferedImage> img = new AtomicReference<>();
        onEdt(() -> {
            var root = f.frame.getRootPane();
            BufferedImage b = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_RGB);
            var g = b.createGraphics();
            root.paint(g);
            g.dispose();
            img.set(b);
        });
        Files.createDirectories(Path.of(dir));
        ImageIO.write(img.get(), "png", Path.of(dir, name).toFile());
    }

    private static void settle() throws Exception {
        for (int i = 0; i < 5; i++) {
            Thread.sleep(150);
            SwingUtilities.invokeAndWait(() -> { });
        }
    }
}
